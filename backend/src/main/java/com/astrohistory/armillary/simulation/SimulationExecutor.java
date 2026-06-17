package com.astrohistory.armillary.simulation;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

@Slf4j
@Component
public class SimulationExecutor {

    private final ExecutorService executor;
    private final int poolSize;

    public SimulationExecutor() {
        int cores = Runtime.getRuntime().availableProcessors();
        this.poolSize = Math.min(8, Math.max(2, cores - 1));

        ThreadFactory threadFactory = new ThreadFactory() {
            private int count = 0;

            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "sim-executor-" + (++count));
                t.setDaemon(true);
                return t;
            }
        };

        RejectedExecutionHandler rejectionHandler = (r, executor) -> {
            log.warn("SimulationExecutor rejected task, running in caller thread as fallback");
            if (!executor.isShutdown()) {
                r.run();
            }
        };

        this.executor = new ThreadPoolExecutor(
                poolSize,
                poolSize,
                0L,
                TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(),
                threadFactory,
                rejectionHandler
        );
    }

    public <T> CompletableFuture<T> submit(Callable<T> task) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return task.call();
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        }, executor);
    }

    public <T> List<CompletableFuture<T>> submitAll(List<Callable<T>> tasks) {
        List<CompletableFuture<T>> futures = new ArrayList<>(tasks.size());
        for (Callable<T> task : tasks) {
            futures.add(submit(task));
        }
        return futures;
    }

    public <T> CompletableFuture<List<T>> submitAllCombined(List<Callable<T>> tasks) {
        List<CompletableFuture<T>> futures = submitAll(tasks);

        CompletableFuture<Void> allOf = CompletableFuture.allOf(
                futures.toArray(new CompletableFuture[0])
        );

        return allOf.thenApply(v -> {
            List<T> results = new ArrayList<>(futures.size());
            for (CompletableFuture<T> f : futures) {
                results.add(f.join());
            }
            return results;
        });
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                log.warn("SimulationExecutor did not terminate in 5 seconds, forcing shutdown");
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            log.warn("Interrupted during shutdown, forcing immediate shutdown");
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    public int getActiveTaskCount() {
        if (executor instanceof ThreadPoolExecutor tpe) {
            return tpe.getActiveCount();
        }
        return 0;
    }

    public int getPoolSize() {
        return poolSize;
    }
}
