package com.astrohistory.armillary.analyzer;

import com.astrohistory.armillary.dto.LubricantComparisonDTO;
import com.astrohistory.armillary.dto.SensorDataDTO;
import com.astrohistory.armillary.entity.BearingConfig;
import com.astrohistory.armillary.entity.FrictionSimulation;
import com.astrohistory.armillary.entity.SensorData;
import com.astrohistory.armillary.repository.BearingConfigRepository;
import com.astrohistory.armillary.repository.FrictionSimulationRepository;
import com.astrohistory.armillary.repository.SensorDataRepository;
import com.astrohistory.armillary.simulation.BearingFrictionModel;
import com.astrohistory.armillary.simulation.SimulationExecutor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LubricantAnalyzer {

    private final BearingFrictionModel frictionModel;
    private final BearingConfigRepository bearingConfigRepository;
    private final SensorDataRepository sensorDataRepository;
    private final FrictionSimulationRepository frictionRepository;
    private final SimulationExecutor simulationExecutor;

    public LubricantComparisonDTO compareLubricants(UUID instrumentId, String axisName) {
        BearingConfig config = bearingConfigRepository
                .findByInstrumentIdAndAxisNameOrderByIdDesc(instrumentId, axisName).stream()
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("轴承配置未找到"));

        SensorData latestSensor = sensorDataRepository
                .findTopByInstrumentIdAndAxisNameOrderByTimestampDesc(instrumentId, axisName)
                .orElse(null);

        SensorDataDTO sensorDTO = convertToSensorDTO(latestSensor, instrumentId, axisName);

        FrictionSimulation latestFriction = frictionRepository
                .findTopByInstrumentIdAndAxisNameOrderBySimulationTimeDesc(instrumentId, axisName)
                .orElse(null);

        double accumulatedWear = latestFriction != null && latestFriction.getTotalWearDepth() != null
                ? latestFriction.getTotalWearDepth().doubleValue() : 0.0;

        List<LubricantComparisonDTO.LubricantDataPoint> lubricantData =
                frictionModel.compareAllLubricants(config, sensorDTO, accumulatedWear, LocalDateTime.now());

        LubricantComparisonDTO.SimulationConditions conditions =
                LubricantComparisonDTO.SimulationConditions.builder()
                        .loadKg(sensorDTO.getLoadRadial() != null ?
                                sensorDTO.getLoadRadial().doubleValue() : 500.0)
                        .rotationalSpeedRpm(sensorDTO.getRotationalSpeed() != null ?
                                sensorDTO.getRotationalSpeed().doubleValue() : 1.0)
                        .temperatureC(sensorDTO.getTemperature() != null ?
                                sensorDTO.getTemperature().doubleValue() : 25.0)
                        .materialPair(config.getInnerRingMaterial() + "-" + config.getOuterRingMaterial())
                        .surfaceRoughnessRa(config.getSurfaceRoughnessRa() != null ?
                                config.getSurfaceRoughnessRa().doubleValue() : 0.2)
                        .build();

        LubricantComparisonDTO.RankingSummary ranking = buildLubricantRanking(lubricantData);

        List<String> historicalNotes = Arrays.asList(
                "元代郭守敬简仪主要使用芝麻油作为润滑剂",
                "中国古代天文仪器使用的植物油需定期更换，约每3-6个月一次",
                "北宋沈括《梦溪笔谈》中记载了石油，但当时并未用于机械润滑",
                "水银悬浮轴承仅在少数高精度仪器中使用，因毒性未能普及",
                "19世纪石油工业兴起后，矿物油逐渐取代动植物油成为主流润滑剂"
        );

        return LubricantComparisonDTO.builder()
                .lubricantData(lubricantData)
                .conditions(conditions)
                .ranking(ranking)
                .historicalNotes(historicalNotes)
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

    private LubricantComparisonDTO.RankingSummary buildLubricantRanking(
            List<LubricantComparisonDTO.LubricantDataPoint> data) {

        Map<String, Double> byWear = data.stream()
                .sorted(Comparator.comparingDouble(LubricantComparisonDTO.LubricantDataPoint::getWearRateMPerSec))
                .collect(Collectors.toMap(
                        LubricantComparisonDTO.LubricantDataPoint::getDisplayName,
                        d -> 1.0 / (d.getWearRateMPerSec() * 1e8 + 1e-6),
                        (a, b) -> a, LinkedHashMap::new));

        Map<String, Double> byFriction = data.stream()
                .sorted(Comparator.comparingDouble(LubricantComparisonDTO.LubricantDataPoint::getFrictionCoefficient))
                .collect(Collectors.toMap(
                        LubricantComparisonDTO.LubricantDataPoint::getDisplayName,
                        d -> 1.0 / (d.getFrictionCoefficient() + 0.001),
                        (a, b) -> a, LinkedHashMap::new));

        Map<String, Double> byHistorical = data.stream()
                .filter(LubricantComparisonDTO.LubricantDataPoint::isHistoricallyAvailable)
                .sorted((a, b) -> Integer.compare(a.getHistoricalCentury(), b.getHistoricalCentury()))
                .collect(Collectors.toMap(
                        LubricantComparisonDTO.LubricantDataPoint::getDisplayName,
                        d -> d.getTemperatureStabilityIndex() * 0.4 +
                                d.getOxidationResistanceScore() * 0.3 +
                                (1.0 / (d.getFrictionCoefficient() + 0.1)) * 0.3,
                        (a, b) -> a, LinkedHashMap::new));

        Map<String, Double> overall = data.stream()
                .collect(Collectors.toMap(
                        LubricantComparisonDTO.LubricantDataPoint::getDisplayName,
                        d -> byWear.getOrDefault(d.getDisplayName(), 0.0) * 0.35 +
                                byFriction.getOrDefault(d.getDisplayName(), 0.0) * 0.35 +
                                d.getTemperatureStabilityIndex() * 0.15 +
                                d.getOxidationResistanceScore() * 0.15,
                        (a, b) -> a, LinkedHashMap::new));

        return LubricantComparisonDTO.RankingSummary.builder()
                .byWearResistance(sortByValueDesc(byWear))
                .byFrictionReduction(sortByValueDesc(byFriction))
                .byHistoricalPracticality(sortByValueDesc(byHistorical))
                .byOverallScore(sortByValueDesc(overall))
                .build();
    }

    private Map<String, Double> sortByValueDesc(Map<String, Double> map) {
        return map.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (a, b) -> a, LinkedHashMap::new));
    }
}
