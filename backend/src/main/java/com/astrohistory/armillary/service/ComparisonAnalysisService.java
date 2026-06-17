package com.astrohistory.armillary.service;

import com.astrohistory.armillary.dto.*;
import com.astrohistory.armillary.entity.BearingConfig;
import com.astrohistory.armillary.entity.FrictionSimulation;
import com.astrohistory.armillary.entity.PointingAnalysis;
import com.astrohistory.armillary.entity.SensorData;
import com.astrohistory.armillary.enums.BearingTechnologyLevel;
import com.astrohistory.armillary.enums.InstrumentType;
import com.astrohistory.armillary.enums.LubricantType;
import com.astrohistory.armillary.repository.BearingConfigRepository;
import com.astrohistory.armillary.repository.FrictionSimulationRepository;
import com.astrohistory.armillary.repository.PointingAnalysisRepository;
import com.astrohistory.armillary.repository.SensorDataRepository;
import com.astrohistory.armillary.simulation.BearingFrictionModel;
import com.astrohistory.armillary.simulation.PointingAccuracyModel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ComparisonAnalysisService {

    private final BearingFrictionModel frictionModel;
    private final PointingAccuracyModel pointingModel;
    private final BearingConfigRepository bearingConfigRepository;
    private final FrictionSimulationRepository frictionRepository;
    private final SensorDataRepository sensorDataRepository;
    private final PointingAnalysisRepository pointingRepository;

    public List<InstrumentComparisonDTO> compareInstrumentBearings(
            UUID instrumentId, String axisName) {

        List<InstrumentComparisonDTO> results = new ArrayList<>();

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

        for (InstrumentType type : InstrumentType.values()) {
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

            results.add(buildComparisonDTO(
                    instrumentId, type, axisName, characteristics,
                    simResult, pointingResult, referenceConfig));
        }

        return results;
    }

    public CrossEraComparisonDTO compareAcrossEras(
            UUID instrumentId, String axisName) {

        BearingConfig config = bearingConfigRepository
                .findByInstrumentIdAndAxisNameOrderByIdDesc(instrumentId, axisName).stream()
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("轴承配置未找到"));

        SensorData latestSensor = sensorDataRepository
                .findTopByInstrumentIdAndAxisNameOrderByTimestampDesc(instrumentId, axisName)
                .orElse(null);

        SensorDataDTO sensorDTO = convertToSensorDTO(latestSensor, instrumentId, axisName);

        List<CrossEraComparisonDTO.EraBearingData> eraData = new ArrayList<>();
        double baselineFriction = 0.0;
        double baselineWear = 0.0;
        double baselinePrecision = 0.0;

        BearingTechnologyLevel[] levels = BearingTechnologyLevel.values();
        for (int i = 0; i < levels.length; i++) {
            BearingTechnologyLevel techLevel = levels[i];
            BearingFrictionModel.FrictionSimulationResult simResult =
                    frictionModel.simulateWithTechnologyLevel(
                            config, sensorDTO, 0.0, LocalDateTime.now(), techLevel);

            if (i == 0) {
                baselineFriction = simResult.getFrictionCoefficient();
                baselineWear = simResult.getWearRate();
                baselinePrecision = techLevel.getTypicalRunoutMicrometers() * 10.0;
            }

            int[] yearRange = getEraYearRange(techLevel);

            CrossEraComparisonDTO.EraBearingData data = CrossEraComparisonDTO.EraBearingData.builder()
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

            eraData.add(data);
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

    public LubricantComparisonDTO compareLubricants(
            UUID instrumentId, String axisName) {

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

    public VirtualOperationResponse simulateVirtualOperation(VirtualOperationRequest request) {

        List<BearingConfig> configs = bearingConfigRepository
                .findByInstrumentId(request.getInstrumentId());

        if (configs.isEmpty()) {
            throw new IllegalArgumentException("未找到仪器配置: " + request.getInstrumentId());
        }

        LubricantType lubricantType = LubricantType.fromString(request.getLubricantType());
        double totalWearMm = 0.0;
        double totalFrictionTorque = 0.0;
        double maxWearRate = 0.0;
        double minLambda = Double.MAX_VALUE;
        double avgLambda = 0.0;
        double avgFrictionCoeff = 0.0;
        double avgFilmThickness = 0.0;
        int count = 0;

        for (BearingConfig config : configs) {
            SensorDataDTO sensorDTO = buildSensorDTOFromVirtualRequest(request, config.getAxisName());

            double accumulatedWear = request.isSimulateWear() ?
                    request.getSimulateHours() * 3600.0 * 1e-7 : 0.0;

            BearingFrictionModel.FrictionSimulationResult simResult =
                    frictionModel.simulateWithLubricantOverride(
                            config, sensorDTO, accumulatedWear,
                            LocalDateTime.now(), lubricantType);

            totalWearMm += simResult.getTotalWearDepth();
            totalFrictionTorque += simResult.getFrictionTorque();
            maxWearRate = Math.max(maxWearRate, simResult.getWearRate());
            minLambda = Math.min(minLambda, simResult.getLambdaRatio());
            avgLambda += simResult.getLambdaRatio();
            avgFrictionCoeff += simResult.getFrictionCoefficient();
            avgFilmThickness += simResult.getFilmThickness();
            count++;
        }

        avgLambda = count > 0 ? avgLambda / count : 0.0;
        avgFrictionCoeff = count > 0 ? avgFrictionCoeff / count : 0.0;
        avgFilmThickness = count > 0 ? avgFilmThickness / count : 0.0;

        double latitudeDeg = 39.9042;
        double az = request.getAzimuthAxisAngleDeg();
        double alt = request.getAltitudeAxisAngleDeg();
        double eq = request.getEquatorialAxisAngleDeg();
        double dec = request.getDeclinationAxisAngleDeg();

        double ra = eq;
        double decRad = Math.toRadians(dec);
        double latRad = Math.toRadians(latitudeDeg);

        double hourAngle = Math.toRadians(az - 180.0);
        double raFromAltAz = Math.toRadians(LocalDateTime.now().getHour() * 15.0) - hourAngle;
        double decFromAltAz = Math.asin(Math.sin(latRad) * Math.sin(Math.toRadians(alt)) +
                Math.cos(latRad) * Math.cos(Math.toRadians(alt)) * Math.cos(hourAngle));

        double wearError = totalWearMm * 3600.0;
        double frictionError = totalFrictionTorque * 0.001;
        double lambdaError = minLambda < 1.0 ? (1.0 - minLambda) * 60.0 : 0.0;

        double azError = wearError * 0.5 + frictionError * 0.3 + lambdaError * 0.2;
        double altError = wearError * 0.6 + frictionError * 0.2 + lambdaError * 0.2;
        double totalError = Math.sqrt(azError * azError + altError * altError);
        double uncertainty = totalError * 0.15;

        azError = azError / 60.0;
        altError = altError / 60.0;
        totalError = totalError / 60.0;
        uncertainty = uncertainty / 60.0;

        int stressLevel = 1;
        if (minLambda < 0.5) stressLevel = 5;
        else if (minLambda < 1.0) stressLevel = 4;
        else if (minLambda < 1.5) stressLevel = 3;
        else if (minLambda < 3.0) stressLevel = 2;

        double lifetime = maxWearRate > 0 ? (0.0001) / (maxWearRate * 3600.0) : 99999.0;

        Map<String, Double> axisPositions = new LinkedHashMap<>();
        axisPositions.put("equatorial", eq);
        axisPositions.put("declination", dec);
        axisPositions.put("azimuth", az);
        axisPositions.put("altitude", alt);

        Map<String, String> statusMessages = new LinkedHashMap<>();
        statusMessages.put("lubrication", frictionModel.getLubricationRegimeName(minLambda));
        statusMessages.put("stress", getStressDescription(stressLevel));
        statusMessages.put("wear", getWearStatusDescription(totalWearMm));

        String starName = getNearestStarName(Math.toDegrees(raFromAltAz), Math.toDegrees(decFromAltAz));

        double rpm = request.getRotationalSpeedRpm();
        double omegaRadS = rpm * 2.0 * Math.PI / 60.0;
        double eqAngleRad = Math.toRadians(request.getEquatorialAxisAngleDeg());
        double decAngleRad = Math.toRadians(request.getDeclinationAxisAngleDeg());
        double angAccel = omegaRadS * (rpm > 1.0 ? 0.1 : 1.0);

        double loadKg = request.getLoadKg();
        double totalMassKg = 150.0 + loadKg * 0.8;
        double eqRingRadius = 1.2;
        double decRingRadius = 0.8;
        double baseRadius = 1.5;

        double inertiaEq = 0.5 * totalMassKg * eqRingRadius * eqRingRadius;
        double inertiaDec = 0.5 * totalMassKg * 0.6 * decRingRadius * decRingRadius;
        double totalInertia = inertiaEq + inertiaDec;

        double inertiaTorque = totalInertia * angAccel;

        double dampingCoeff = 0.05 * totalFrictionTorque * Math.max(omegaRadS, 0.01);
        double dampingTorque = dampingCoeff * omegaRadS;

        double gravityTorque = totalMassKg * 9.81 * baseRadius * 0.5 *
                Math.abs(Math.cos(eqAngleRad) * Math.sin(decAngleRad));

        double totalTorque = totalFrictionTorque + inertiaTorque + dampingTorque + gravityTorque;

        double leverArm = 0.4;
        double manualForce = totalTorque / Math.max(leverArm, 0.01);

        double hapticIntensity = 0.0;
        String feedbackStatus = "正常";
        if (minLambda < 0.5) {
            hapticIntensity = Math.min(1.0, totalTorque / 500.0);
            feedbackStatus = "强振动 - 边界润滑";
        } else if (minLambda < 1.0) {
            hapticIntensity = Math.min(0.6, totalTorque / 200.0);
            feedbackStatus = "轻微振动 - 混合润滑";
        } else if (minLambda < 3.0) {
            hapticIntensity = Math.min(0.3, totalTorque / 100.0);
            feedbackStatus = "平滑 - 弹流润滑";
        } else {
            hapticIntensity = Math.min(0.1, totalTorque / 50.0);
            feedbackStatus = "极平滑 - 全膜流体润滑";
        }

        return VirtualOperationResponse.builder()
                .currentAzimuthDeg(az)
                .currentAltitudeDeg(alt)
                .currentRightAscensionDeg(Math.toDegrees(raFromAltAz))
                .currentDeclinationDeg(Math.toDegrees(decFromAltAz))
                .azimuthErrorArcmin(azError * 60.0)
                .altitudeErrorArcmin(altError * 60.0)
                .totalPointingErrorArcmin(totalError * 60.0)
                .errorUncertaintyArcmin(uncertainty * 60.0)
                .currentFrictionTorqueNm(totalFrictionTorque)
                .currentWearRateMPerSec(maxWearRate)
                .currentLambdaRatio(minLambda)
                .currentFilmThicknessUm(avgFilmThickness)
                .frictionCoefficient(avgFrictionCoeff)
                .cumulativeWearMm(totalWearMm)
                .estimatedTimeToFailureHours(lifetime)
                .stressLevel(stressLevel)
                .targetStarName(starName)
                .operationTorqueRequiredNm(totalTorque)
                .inertiaResistanceNm(inertiaTorque)
                .dampingCoefficient(dampingCoeff)
                .currentAngularAccelerationRadS2(angAccel)
                .hapticFeedbackIntensity(hapticIntensity)
                .forceFeedbackStatus(feedbackStatus)
                .estimatedManualForceN(manualForce)
                .axisPositions(axisPositions)
                .statusMessages(statusMessages)
                .build();
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

    private SensorDataDTO buildSensorDTOFromVirtualRequest(VirtualOperationRequest req, String axisName) {
        double speed = switch (axisName) {
            case "赤道轴" -> req.getEquatorialAxisAngleDeg() / 360.0 * 60.0;
            case "赤纬轴" -> req.getDeclinationAxisAngleDeg() / 360.0 * 60.0;
            case "地平经轴" -> req.getAzimuthAxisAngleDeg() / 360.0 * 60.0;
            case "地平纬轴" -> req.getAltitudeAxisAngleDeg() / 360.0 * 60.0;
            default -> 1.0;
        };
        speed = Math.max(Math.abs(speed), 0.1) * req.getRotationalSpeedRpm();

        return SensorDataDTO.builder()
                .instrumentId(req.getInstrumentId())
                .axisName(axisName)
                .timestamp(LocalDateTime.now())
                .rotationalSpeed(BigDecimal.valueOf(speed))
                .frictionTorque(BigDecimal.valueOf(req.getLoadKg() * 0.2))
                .wearDepth(BigDecimal.valueOf(0.0))
                .temperature(BigDecimal.valueOf(req.getTemperatureC()))
                .loadRadial(BigDecimal.valueOf(req.getLoadKg()))
                .loadAxial(BigDecimal.valueOf(req.getLoadKg() * 0.2))
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

    private String getStressDescription(int level) {
        return switch (level) {
            case 1 -> "极低应力 - 全膜流体润滑，运行理想";
            case 2 -> "低应力 - 弹流润滑，正常运行状态";
            case 3 -> "中等应力 - 混合润滑，需关注维护";
            case 4 -> "高应力 - 边界润滑为主，磨损加速";
            case 5 -> "极高应力 - 接近干摩擦，需立即停机检查";
            default -> "未知";
        };
    }

    private String getWearStatusDescription(double wearMm) {
        if (wearMm < 0.001) return "几乎无磨损";
        if (wearMm < 0.01) return "轻微磨损";
        if (wearMm < 0.05) return "中度磨损，建议定期检查";
        if (wearMm < 0.1) return "严重磨损，建议尽快检修";
        return "超限磨损，必须更换";
    }

    private String getNearestStarName(double raDeg, double decDeg) {
        double ra = ((raDeg % 360) + 360) % 360;
        double dec = Math.max(-90, Math.min(90, decDeg));

        double[][] stars = {
                {0.0, 89.26, "北极星"},
                {5.28, 45.95, "五车二"},
                {42.13, -16.45, "参宿四"},
                {83.0, -5.7, "天狼星"},
                {101.28, 11.97, "南河三"},
                {178.5, 12.4, "狮子座"},
                {187.0, 38.78, "大角星"},
                {279.3, 38.3, "织女星"},
                {283.8, -9.5, "牛郎星"},
                {347.7, -30.0, "北落师门"}
        };

        double minDist = Double.MAX_VALUE;
        String nearest = "未知天体";
        for (double[] star : stars) {
            double deltaRa = Math.toRadians(star[0] - ra);
            double dec1 = Math.toRadians(star[1]);
            double dec2 = Math.toRadians(dec);
            double dist = Math.acos(Math.sin(dec1) * Math.sin(dec2) +
                    Math.cos(dec1) * Math.cos(dec2) * Math.cos(deltaRa));
            if (dist < minDist) {
                minDist = dist;
                nearest = star[2];
            }
        }
        return nearest + " (附近)";
    }
}
