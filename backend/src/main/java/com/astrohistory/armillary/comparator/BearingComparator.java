package com.astrohistory.armillary.comparator;

import com.astrohistory.armillary.dto.InstrumentComparisonDTO;
import com.astrohistory.armillary.dto.SensorDataDTO;
import com.astrohistory.armillary.entity.BearingConfig;
import com.astrohistory.armillary.entity.FrictionSimulation;
import com.astrohistory.armillary.entity.SensorData;
import com.astrohistory.armillary.enums.BearingTechnologyLevel;
import com.astrohistory.armillary.enums.InstrumentType;
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
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
public class BearingComparator {

    private final BearingFrictionModel frictionModel;
    private final BearingConfigRepository bearingConfigRepository;
    private final SensorDataRepository sensorDataRepository;
    private final FrictionSimulationRepository frictionRepository;
    private final SimulationExecutor simulationExecutor;

    public CompletableFuture<List<InstrumentComparisonDTO>> compareInstrumentBearings(UUID instrumentId, String axisName) {
        BearingConfig referenceConfig = bearingConfigRepository
                .findByInstrumentIdAndAxisName(instrumentId, axisName)
                .orElseThrow(() -> new IllegalArgumentException(
                        "未找到仪器 " + instrumentId + " 的轴 " + axisName + " 配置"));

        SensorData latestSensor = sensorDataRepository
                .findTopByInstrumentIdAndAxisNameOrderByTimestampDesc(instrumentId, axisName)
                .orElse(null);

        SensorDataDTO sensorDTO = convertToSensorDTO(latestSensor, instrumentId, axisName);

        FrictionSimulation latestFriction = frictionRepository
                .findTopByInstrumentIdAndAxisNameOrderBySimulationTimeDesc(instrumentId, axisName)
                .orElse(null);

        double accumulatedWear = latestFriction != null && latestFriction.getTotalWearDepth() != null
                ? latestFriction.getTotalWearDepth().doubleValue() : 0.0;

        List<CompletableFuture<InstrumentComparisonDTO>> futures = new ArrayList<>();

        for (InstrumentType type : InstrumentType.values()) {
            futures.add(simulationExecutor.submit(() -> {
                Map<String, Object> characteristics =
                        frictionModel.getInstrumentTypeBearingCharacteristics(type);

                BearingTechnologyLevel techLevel = (BearingTechnologyLevel)
                        characteristics.get("technologyLevel");

                BearingFrictionModel.FrictionSimulationResult simResult =
                        frictionModel.simulateWithTechnologyLevel(
                                referenceConfig, sensorDTO, accumulatedWear,
                                LocalDateTime.now(), techLevel);

                SimplePointingResult pointingResult =
                        buildPointingResultForInstrument(type, latestSensor);

                return buildComparisonDTO(
                        instrumentId, type, axisName, characteristics,
                        simResult, pointingResult, referenceConfig);
            }));
        }

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> {
                    List<InstrumentComparisonDTO> results = new ArrayList<>(futures.size());
                    for (CompletableFuture<InstrumentComparisonDTO> f : futures) {
                        results.add(f.join());
                    }
                    return results;
                });
    }

    private InstrumentComparisonDTO buildComparisonDTO(
            UUID instrumentId, InstrumentType type, String axisName,
            Map<String, Object> characteristics,
            BearingFrictionModel.FrictionSimulationResult simResult,
            SimplePointingResult pointingResult,
            BearingConfig config) {

        double maxWear = config.getMaxAllowableWear() != null ?
                config.getMaxAllowableWear().doubleValue() : 0.1;
        double wearPct = (simResult.getTotalWearDepth() / maxWear) * 100.0;
        double lifetime = simResult.getWearRate() > 0 ?
                (maxWear * 0.001) / (simResult.getWearRate() * 3600.0) : 99999.0;

        InstrumentComparisonDTO.FrictionMetricsDTO frictionMetrics =
                InstrumentComparisonDTO.FrictionMetricsDTO.builder()
                        .lambdaRatio(simResult.getLambdaRatio())
                        .filmThicknessUm(simResult.getFilmThickness())
                        .frictionCoefficient(simResult.getFrictionCoefficient())
                        .asperityContactRatio(simResult.getAsperityContactRatio())
                        .contactPressureMPa(simResult.getContactPressure())
                        .lubricationRegime(frictionModel.getLubricationRegimeName(simResult.getLambdaRatio()))
                        .build();

        InstrumentComparisonDTO.WearMetricsDTO wearMetrics =
                InstrumentComparisonDTO.WearMetricsDTO.builder()
                        .wearRateMPerSec(simResult.getWearRate())
                        .wearDepthMm(simResult.getTotalWearDepth())
                        .maxAllowableWearMm(maxWear)
                        .wearPercentage(wearPct)
                        .estimatedLifetimeHours(lifetime)
                        .build();

        double perpErr = config.getPerpendicularityError() != null ?
                config.getPerpendicularityError().doubleValue() : 0.0;
        double axialRun = config.getAxialRunout() != null ?
                config.getAxialRunout().doubleValue() : 0.0;
        double radialRun = config.getRadialRunout() != null ?
                config.getRadialRunout().doubleValue() : 0.0;

        BearingTechnologyLevel techLevel = (BearingTechnologyLevel) characteristics.get("technologyLevel");
        perpErr *= techLevel.getTypicalRunoutMicrometers() / 3.2;
        axialRun *= techLevel.getTypicalRunoutMicrometers() / 3.2;
        radialRun *= techLevel.getTypicalRunoutMicrometers() / 3.2;

        InstrumentComparisonDTO.GeometryMetricsDTO geometryMetrics =
                InstrumentComparisonDTO.GeometryMetricsDTO.builder()
                        .perpendicularityErrorArcsec(perpErr)
                        .axialRunoutMicrometers(axialRun * 1000.0)
                        .radialRunoutMicrometers(radialRun * 1000.0)
                        .totalGeometricErrorArcsec(Math.sqrt(perpErr * perpErr +
                                axialRun * 1000.0 * 0.001 + radialRun * 1000.0 * 0.001))
                        .build();

        InstrumentComparisonDTO.PointingMetricsDTO pointingMetrics =
                InstrumentComparisonDTO.PointingMetricsDTO.builder()
                        .azimuthErrorArcmin(pointingResult.getAzimuthErrorDeg() * 60.0)
                        .altitudeErrorArcmin(pointingResult.getAltitudeErrorDeg() * 60.0)
                        .totalPointingErrorArcmin(pointingResult.getTotalPointingErrorDeg() * 60.0)
                        .geometricContributionArcmin(pointingResult.getGeometricErrorContributionDeg() * 60.0)
                        .errorUncertaintyArcmin(pointingResult.getErrorUncertaintyDeg() * 60.0)
                        .build();

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("originYear", characteristics.get("originYear"));
        metadata.put("axisCount", characteristics.get("axisCount"));
        metadata.put("technologyLevel", techLevel.getDisplayName());
        metadata.put("typicalLubricant", characteristics.get("typicalLubricant"));
        metadata.put("estimatedPrecisionArcsec", characteristics.get("estimatedPrecisionArcsec"));

        @SuppressWarnings("unchecked")
        List<String> keyFeatures = (List<String>) characteristics.get("keyFeatures");

        return InstrumentComparisonDTO.builder()
                .instrumentId(instrumentId)
                .instrumentName(type.getDisplayName())
                .instrumentType(type)
                .axisName(axisName)
                .frictionMetrics(frictionMetrics)
                .wearMetrics(wearMetrics)
                .geometryMetrics(geometryMetrics)
                .pointingMetrics(pointingMetrics)
                .metadata(metadata)
                .keyFeatures(keyFeatures)
                .build();
    }

    private SimplePointingResult buildPointingResultForInstrument(
            InstrumentType type, SensorData sensor) {

        double dec = sensor != null && sensor.getTemperature() != null ?
                Math.toRadians(sensor.getTemperature().doubleValue() * 0.9) : Math.toRadians(30.0);
        double wearError = 0.002 / type.getAxisCount();
        double geometricError = ((BearingTechnologyLevel)
                frictionModel.getInstrumentTypeBearingCharacteristics(type)
                        .get("technologyLevel")).getTypicalRunoutMicrometers() * 0.00001;

        double azErr = wearError + geometricError * 0.5;
        double altErr = wearError * 1.2 + geometricError * 0.5;
        double total = Math.sqrt(azErr * azErr + altErr * altErr);
        double uncertainty = total * 0.15;

        return new SimplePointingResult(
                0.0, Math.toDegrees(dec), azErr, altErr, total,
                geometricError, uncertainty);
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

    public static class SimplePointingResult {
        private final double targetRa;
        private final double targetDec;
        private final double azimuthErrorDeg;
        private final double altitudeErrorDeg;
        private final double totalPointingErrorDeg;
        private final double geometricErrorContributionDeg;
        private final double errorUncertaintyDeg;

        public SimplePointingResult(double targetRa, double targetDec,
                                    double azimuthErrorDeg, double altitudeErrorDeg,
                                    double totalPointingErrorDeg,
                                    double geometricErrorContributionDeg,
                                    double errorUncertaintyDeg) {
            this.targetRa = targetRa;
            this.targetDec = targetDec;
            this.azimuthErrorDeg = azimuthErrorDeg;
            this.altitudeErrorDeg = altitudeErrorDeg;
            this.totalPointingErrorDeg = totalPointingErrorDeg;
            this.geometricErrorContributionDeg = geometricErrorContributionDeg;
            this.errorUncertaintyDeg = errorUncertaintyDeg;
        }

        public double getAzimuthErrorDeg() { return azimuthErrorDeg; }
        public double getAltitudeErrorDeg() { return altitudeErrorDeg; }
        public double getTotalPointingErrorDeg() { return totalPointingErrorDeg; }
        public double getGeometricErrorContributionDeg() { return geometricErrorContributionDeg; }
        public double getErrorUncertaintyDeg() { return errorUncertaintyDeg; }
    }
}
