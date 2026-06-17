package com.astrohistory.armillary.comparator;

import com.astrohistory.armillary.dto.CrossEraComparisonDTO;
import com.astrohistory.armillary.dto.SensorDataDTO;
import com.astrohistory.armillary.entity.BearingConfig;
import com.astrohistory.armillary.entity.SensorData;
import com.astrohistory.armillary.enums.BearingTechnologyLevel;
import com.astrohistory.armillary.enums.LubricantType;
import com.astrohistory.armillary.repository.BearingConfigRepository;
import com.astrohistory.armillary.repository.SensorDataRepository;
import com.astrohistory.armillary.simulation.BearingFrictionModel;
import com.astrohistory.armillary.simulation.SimulationExecutor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.Callable;

@Service
@RequiredArgsConstructor
public class EraComparator {

    private final BearingFrictionModel frictionModel;
    private final BearingConfigRepository bearingConfigRepository;
    private final SensorDataRepository sensorDataRepository;
    private final SimulationExecutor simulationExecutor;

    public CrossEraComparisonDTO compareAcrossEras(UUID instrumentId, String axisName) {
        BearingConfig config = bearingConfigRepository
                .findByInstrumentIdAndAxisNameOrderByIdDesc(instrumentId, axisName).stream()
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("轴承配置未找到"));

        SensorData latestSensor = sensorDataRepository
                .findTopByInstrumentIdAndAxisNameOrderByTimestampDesc(instrumentId, axisName)
                .orElse(null);

        SensorDataDTO sensorDTO = convertToSensorDTO(latestSensor, instrumentId, axisName);

        BearingTechnologyLevel[] levels = BearingTechnologyLevel.values();
        List<Callable<CrossEraComparisonDTO.EraBearingData>> tasks = new ArrayList<>();

        for (BearingTechnologyLevel techLevel : levels) {
            tasks.add(() -> {
                BearingFrictionModel.FrictionSimulationResult simResult =
                        frictionModel.simulateWithTechnologyLevel(
                                config, sensorDTO, 0.0, LocalDateTime.now(), techLevel);

                int[] yearRange = getEraYearRange(techLevel);

                return CrossEraComparisonDTO.EraBearingData.builder()
                        .technologyLevel(techLevel)
                        .eraName(techLevel.getDisplayName())
                        .eraYearStart(yearRange[0])
                        .eraYearEnd(yearRange[1])
                        .typicalFrictionCoefficient(simResult.getFrictionCoefficient())
                        .typicalWearRate(simResult.getWearRate())
                        .typicalRunoutMicrometers(techLevel.getTypicalRunoutMicrometers())
                        .estimatedPrecisionArcsec(techLevel.getTypicalRunoutMicrometers() * 10.0)
                        .materialProperties(buildMaterialProperties(techLevel, config))
                        .commonLubricants(getEraCommonLubricants(techLevel))
                        .typicalApplications(getEraApplications(techLevel))
                        .build();
            });
        }

        List<CrossEraComparisonDTO.EraBearingData> eraData;
        try {
            eraData = simulationExecutor.submitAllCombined(tasks).join();
        } catch (Exception e) {
            eraData = new ArrayList<>();
            for (Callable<CrossEraComparisonDTO.EraBearingData> task : tasks) {
                try {
                    eraData.add(task.call());
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
            }
        }

        CrossEraComparisonDTO.EraBearingData oldest = eraData.get(0);
        CrossEraComparisonDTO.EraBearingData newest = eraData.get(eraData.size() - 1);

        Map<String, Double> ranking = new LinkedHashMap<>();
        for (CrossEraComparisonDTO.EraBearingData d : eraData) {
            double score = 1.0 / (d.getTypicalFrictionCoefficient() * 0.3 +
                    d.getTypicalWearRate() * 1e6 * 0.3 +
                    d.getTypicalRunoutMicrometers() * 0.4);
            ranking.put(d.getEraName(), score);
        }

        CrossEraComparisonDTO.SummaryComparison summary =
                CrossEraComparisonDTO.SummaryComparison.builder()
                        .frictionCoefficientImprovementRatio(
                                oldest.getTypicalFrictionCoefficient() /
                                        Math.max(newest.getTypicalFrictionCoefficient(), 1e-6))
                        .wearRateImprovementRatio(
                                oldest.getTypicalWearRate() /
                                        Math.max(newest.getTypicalWearRate(), 1e-15))
                        .precisionImprovementRatio(
                                oldest.getTypicalRunoutMicrometers() /
                                        Math.max(newest.getTypicalRunoutMicrometers(), 1e-3))
                        .lifetimeImprovementRatio(
                                newest.getTypicalWearRate() > 0 ?
                                        oldest.getTypicalWearRate() / newest.getTypicalWearRate() : 0)
                        .eraRanking(ranking)
                        .build();

        List<String> insights = Arrays.asList(
                String.format("从古代青铜轴承到现代气浮轴承，摩擦系数降低了约 %.1f 倍",
                        summary.getFrictionCoefficientImprovementRatio()),
                String.format("磨损率降低了约 %.0f 个数量级",
                        Math.log10(summary.getWearRateImprovementRatio())),
                String.format("轴系精度提升了约 %.0f 倍",
                        summary.getPrecisionImprovementRatio()),
                "润滑剂技术的进步是轴承性能提升的关键因素之一",
                "材料科学和加工精度的提升共同推动了轴承革命"
        );

        return CrossEraComparisonDTO.builder()
                .eraData(eraData)
                .summary(summary)
                .insights(insights)
                .build();
    }

    private SensorDataDTO convertToSensorDTO(SensorData sensor, UUID instrumentId, String axisName) {
        if (sensor == null) {
            return SensorDataDTO.builder()
                    .instrumentId(instrumentId)
                    .axisName(axisName)
                    .timestamp(LocalDateTime.now())
                    .rotationalSpeed(BigDecimal.valueOf(1.0))
                    .frictionTorque(BigDecimal.valueOf(100.0))
                    .wearDepth(BigDecimal.valueOf(0.001))
                    .temperature(BigDecimal.valueOf(25.0))
                    .loadRadial(BigDecimal.valueOf(500.0))
                    .loadAxial(BigDecimal.valueOf(100.0))
                    .build();
        }
        return SensorDataDTO.builder()
                .instrumentId(instrumentId)
                .axisName(axisName)
                .timestamp(sensor.getTimestamp())
                .rotationalSpeed(sensor.getRotationalSpeed())
                .frictionTorque(sensor.getFrictionTorque())
                .wearDepth(sensor.getWearDepth())
                .temperature(sensor.getTemperature())
                .loadRadial(sensor.getLoadRadial())
                .loadAxial(sensor.getLoadAxial())
                .pointingErrorAz(sensor.getPointingErrorAz())
                .pointingErrorAlt(sensor.getPointingErrorAlt())
                .build();
    }

    private int[] getEraYearRange(BearingTechnologyLevel level) {
        return switch (level) {
            case ANCIENT_BRONZE_WOOD -> new int[]{-200, 1000};
            case ANCIENT_BRONZE_IRON -> new int[]{1000, 1800};
            case EARLY_MODERN -> new int[]{1800, 1900};
            case MODERN_PLAIN -> new int[]{1900, 1950};
            case MODERN_ROLLING -> new int[]{1950, 2000};
            case MODERN_PRECISE -> new int[]{2000, 2100};
        };
    }

    private List<LubricantType> getEraCommonLubricants(BearingTechnologyLevel level) {
        return switch (level) {
            case ANCIENT_BRONZE_WOOD -> Arrays.asList(LubricantType.DRY, LubricantType.ANIMAL_FAT);
            case ANCIENT_BRONZE_IRON -> Arrays.asList(LubricantType.VEGETABLE_OIL, LubricantType.MERCURY);
            case EARLY_MODERN -> Arrays.asList(LubricantType.VEGETABLE_OIL, LubricantType.MINERAL_OIL);
            case MODERN_PLAIN, MODERN_ROLLING -> Arrays.asList(LubricantType.MINERAL_OIL);
            case MODERN_PRECISE -> Arrays.asList(LubricantType.MODERN_SYNTHETIC);
        };
    }

    private String getEraApplications(BearingTechnologyLevel level) {
        return switch (level) {
            case ANCIENT_BRONZE_WOOD -> "汉代浑仪、唐代水运浑仪、早期天文仪器";
            case ANCIENT_BRONZE_IRON -> "元代简仪、明代浑仪、清代象限仪";
            case EARLY_MODERN -> "工业革命时期机床、蒸汽机车";
            case MODERN_PLAIN -> "内燃机、发电机、一般机床";
            case MODERN_ROLLING -> "汽车、电机、通用机械";
            case MODERN_PRECISE -> "卫星、望远镜、光刻机、数控机床";
        };
    }

    private Map<String, Double> buildMaterialProperties(BearingTechnologyLevel level, BearingConfig config) {
        Map<String, Double> props = new LinkedHashMap<>();
        double factor = switch (level) {
            case ANCIENT_BRONZE_WOOD -> 0.5;
            case ANCIENT_BRONZE_IRON -> 1.0;
            case EARLY_MODERN -> 1.5;
            case MODERN_PLAIN -> 2.0;
            case MODERN_ROLLING -> 3.0;
            case MODERN_PRECISE -> 10.0;
        };
        props.put("hardnessGPa", config.getHardness() != null ?
                config.getHardness().doubleValue() * factor / 1000.0 : 0.18);
        props.put("elasticModulusGPa", config.getElasticModulus() != null ?
                config.getElasticModulus().doubleValue() * Math.min(factor, 1.2) / 1000.0 : 100.0);
        props.put("surfaceRoughnessRaUm", config.getSurfaceRoughnessRa() != null ?
                config.getSurfaceRoughnessRa().doubleValue() / factor : 0.8 / factor);
        return props;
    }
}
