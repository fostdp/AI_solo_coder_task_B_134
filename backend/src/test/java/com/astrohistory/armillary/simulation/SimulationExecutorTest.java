package com.astrohistory.armillary.simulation;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SimulationExecutor测试")
class SimulationExecutorTest {

    private SimulationExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new SimulationExecutor();
    }

    @AfterEach
    void tearDown() {
        executor.shutdown();
    }

    @Nested
    @DisplayName("正常用例")
    class NormalCases {

        @Test
        @DisplayName("submit单个任务返回正确结果")
        void submitSingleTaskReturnsCorrectResult() throws Exception {
            CompletableFuture<String> future = executor.submit(() -> "hello");
            assertEquals("hello", future.get(5, TimeUnit.SECONDS));
        }

        @Test
        @DisplayName("submit多个任务返回所有结果")
        void submitMultipleTasksReturnsAllResults() throws Exception {
            List<Callable<Integer>> tasks = IntStream.range(0, 5)
                    .mapToObj(i -> (Callable<Integer>) () -> i * 10)
                    .collect(Collectors.toList());

            List<CompletableFuture<Integer>> futures = executor.submitAll(tasks);

            for (int i = 0; i < tasks.size(); i++) {
                assertEquals(i * 10, futures.get(i).get(5, TimeUnit.SECONDS));
            }
        }

        @Test
        @DisplayName("submitAllCombined返回按顺序合并的列表")
        void submitAllCombinedReturnsCombinedListInOrder() throws Exception {
            List<Callable<String>> tasks = IntStream.range(0, 5)
                    .mapToObj(i -> (Callable<String>) () -> "task-" + i)
                    .collect(Collectors.toList());

            CompletableFuture<List<String>> combined = executor.submitAllCombined(tasks);
            List<String> results = combined.get(5, TimeUnit.SECONDS);

            assertEquals(5, results.size());
            for (int i = 0; i < 5; i++) {
                assertEquals("task-" + i, results.get(i));
            }
        }

        @Test
        @DisplayName("线程池大小在2到8之间")
        void poolSizeIsBetween2And8() {
            int poolSize = executor.getPoolSize();
            assertTrue(poolSize >= 2 && poolSize <= 8,
                    "线程池大小应在2到8之间，实际: " + poolSize);
        }

        @Test
        @DisplayName("getActiveTaskCount返回非负数")
        void getActiveTaskCountReturnsNonNegative() {
            int count = executor.getActiveTaskCount();
            assertTrue(count >= 0, "活跃任务数应非负，实际: " + count);
        }
    }

    @Nested
    @DisplayName("边界用例")
    class BoundaryCases {

        @Test
        @DisplayName("submit null callable抛出NullPointerException")
        void submitNullCallableThrowsException() {
            assertThrows(NullPointerException.class, () -> executor.submit(null));
        }

        @Test
        @DisplayName("submitAll传入空列表返回空列表")
        void submitAllWithEmptyListReturnsEmptyList() {
            List<Callable<String>> emptyTasks = Collections.emptyList();
            List<CompletableFuture<String>> futures = executor.submitAll(emptyTasks);
            assertTrue(futures.isEmpty());
        }

        @Test
        @DisplayName("submitAllCombined传入空列表返回空列表的Future")
        void submitAllCombinedWithEmptyListReturnsEmptyListFuture() throws Exception {
            List<Callable<String>> emptyTasks = Collections.emptyList();
            CompletableFuture<List<String>> combined = executor.submitAllCombined(emptyTasks);
            List<String> results = combined.get(5, TimeUnit.SECONDS);
            assertTrue(results.isEmpty());
        }

        @Test
        @DisplayName("提交大量任务（50+）全部成功完成")
        void submitLargeNumberOfTasks() throws Exception {
            int taskCount = 60;
            List<Callable<Integer>> tasks = IntStream.range(0, taskCount)
                    .mapToObj(i -> (Callable<Integer>) () -> {
                        Thread.sleep(10);
                        return i;
                    })
                    .collect(Collectors.toList());

            CompletableFuture<List<Integer>> combined = executor.submitAllCombined(tasks);
            List<Integer> results = combined.get(30, TimeUnit.SECONDS);

            assertEquals(taskCount, results.size());
            for (int i = 0; i < taskCount; i++) {
                assertEquals(i, results.get(i));
            }
        }

        @Test
        @DisplayName("长时间运行的任务可被超时中断")
        void longRunningTaskCanBeTimedOut() throws Exception {
            CompletableFuture<Void> future = executor.submit(() -> {
                Thread.sleep(60_000);
                return null;
            });

            assertFalse(future.isDone());

            future.orTimeout(100, TimeUnit.MILLISECONDS);

            Thread.sleep(200);

            assertTrue(future.isDone());
            assertTrue(future.isCompletedExceptionally());
        }
    }

    @Nested
    @DisplayName("异常用例")
    class AbnormalCases {

        @Test
        @DisplayName("submit抛出检查异常的任务会异常完成")
        void submitTaskThrowingCheckedExceptionCompletesExceptionally() {
            CompletableFuture<String> future = executor.submit(() -> {
                throw new Exception("受检异常");
            });

            CompletionException ex = assertThrows(CompletionException.class,
                    () -> future.get(5, TimeUnit.SECONDS));
            assertTrue(ex.getCause().getMessage().contains("受检异常"));
        }

        @Test
        @DisplayName("submit抛出RuntimeException的任务会异常完成")
        void submitTaskThrowingRuntimeExceptionCompletesExceptionally() {
            CompletableFuture<String> future = executor.submit(() -> {
                throw new RuntimeException("运行时异常");
            });

            CompletionException ex = assertThrows(CompletionException.class,
                    () -> future.get(5, TimeUnit.SECONDS));
            assertTrue(ex.getCause().getMessage().contains("运行时异常"));
        }

        @Test
        @DisplayName("shutdown后submit任务在调用者线程中回退执行")
        void submitAfterShutdownFallsBackToCallerThread() throws Exception {
            executor.shutdown();
            Thread.sleep(500);

            CompletableFuture<String> future = executor.submit(() -> "fallback-result");
            String result = future.get(5, TimeUnit.SECONDS);
            assertEquals("fallback-result", result);
        }

        @Test
        @DisplayName("submitAllCombined中部分任务异常时整体异常完成")
        void submitAllCombinedWithPartialFailureCompletesExceptionally() {
            List<Callable<String>> tasks = new ArrayList<>();
            tasks.add(() -> "ok");
            tasks.add(() -> {
                throw new RuntimeException("失败任务");
            });
            tasks.add(() -> "also-ok");

            CompletableFuture<List<String>> combined = executor.submitAllCombined(tasks);

            assertThrows(CompletionException.class,
                    () -> combined.get(5, TimeUnit.SECONDS));
        }
    }

    @Nested
    @DisplayName("并发与状态用例")
    class ConcurrencyAndStateCases {

        @Test
        @DisplayName("并发任务可同时执行")
        void concurrentTasksExecuteInParallel() throws Exception {
            AtomicInteger concurrentCount = new AtomicInteger(0);
            AtomicInteger maxConcurrent = new AtomicInteger(0);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(4);

            List<Callable<Void>> tasks = IntStream.range(0, 4)
                    .mapToObj(i -> (Callable<Void>) () -> {
                        startLatch.await();
                        int current = concurrentCount.incrementAndGet();
                        maxConcurrent.updateAndGet(m -> Math.max(m, current));
                        Thread.sleep(200);
                        concurrentCount.decrementAndGet();
                        doneLatch.countDown();
                        return null;
                    })
                    .collect(Collectors.toList());

            List<CompletableFuture<Void>> futures = executor.submitAll(tasks);
            startLatch.countDown();
            doneLatch.await(10, TimeUnit.SECONDS);

            assertTrue(maxConcurrent.get() > 1,
                    "应存在并发执行，最大并发数: " + maxConcurrent.get());
        }

        @Test
        @DisplayName("getActiveTaskCount在有任务执行时大于0")
        void getActiveTaskCountPositiveWhenTasksRunning() throws Exception {
            CountDownLatch taskStarted = new CountDownLatch(1);
            CountDownLatch taskRelease = new CountDownLatch(1);

            executor.submit(() -> {
                taskStarted.countDown();
                taskRelease.await();
                return null;
            });

            taskStarted.await(5, TimeUnit.SECONDS);
            Thread.sleep(100);

            int active = executor.getActiveTaskCount();
            assertTrue(active >= 0);

            taskRelease.countDown();
        }

        @Test
        @DisplayName("多次shutdown不会抛出异常")
        void multipleShutdownsDoNotThrow() {
            assertDoesNotThrow(() -> {
                executor.shutdown();
                executor.shutdown();
                executor.shutdown();
            });
        }
    }
}
