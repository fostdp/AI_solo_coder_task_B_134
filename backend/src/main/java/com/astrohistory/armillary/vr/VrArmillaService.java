package com.astrohistory.armillary.vr;

import com.astrohistory.armillary.dto.SensorDataDTO;
import com.astrohistory.armillary.dto.VirtualOperationRequest;
import com.astrohistory.armillary.dto.VirtualOperationResponse;
import com.astrohistory.armillary.entity.BearingConfig;
import com.astrohistory.armillary.enums.LubricantType;
import com.astrohistory.armillary.repository.BearingConfigRepository;
import com.astrohistory.armillary.simulation.BearingFrictionModel;
import com.astrohistory.armillary.simulation.SimulationExecutor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
public class VrArmillaService {

    private final BearingFrictionModel frictionModel;
    private final BearingConfigRepository bearingConfigRepository;
    private final SimulationExecutor simulationExecutor;

    public VirtualOperationResponse simulateVirtualOperation(VirtualOperationRequest request) {

        List<BearingConfig> configs = bearingConfigRepository
                .findByInstrumentId(request.getInstrumentId());

        if (configs.isEmpty()) {
            throw new IllegalArgumentException("未找到仪器配置: " + request.getInstrumentId());
        }

        LubricantType lubricantType = LubricantType.fromString(request.getLubricantType());

        List<Callable<BearingFrictionModel.FrictionSimulationResult>> tasks = new ArrayList<>();
        for (BearingConfig config : configs) {
            SensorDataDTO sensorDTO = buildSensorDTOFromVirtualRequest(request, config.getAxisName());
            double accumulatedWear = request.isSimulateWear() ?
                    request.getSimulateHours() * 3600.0 * 1e-7 : 0.0;

            tasks.add(() -> frictionModel.simulateWithLubricantOverride(
                    config, sensorDTO, accumulatedWear,
                    LocalDateTime.now(), lubricantType));
        }

        List<BearingFrictionModel.FrictionSimulationResult> simResults =
                simulationExecutor.submitAllCombined(tasks).join();

        double totalWearMm = 0.0;
        double totalFrictionTorque = 0.0;
        double maxWearRate = 0.0;
        double minLambda = Double.MAX_VALUE;
        double avgLambda = 0.0;
        double avgFrictionCoeff = 0.0;
        double avgFilmThickness = 0.0;
        int count = simResults.size();

        for (BearingFrictionModel.FrictionSimulationResult simResult : simResults) {
            totalWearMm += simResult.getTotalWearDepth();
            totalFrictionTorque += simResult.getFrictionTorque();
            maxWearRate = Math.max(maxWearRate, simResult.getWearRate());
            minLambda = Math.min(minLambda, simResult.getLambdaRatio());
            avgLambda += simResult.getLambdaRatio();
            avgFrictionCoeff += simResult.getFrictionCoefficient();
            avgFilmThickness += simResult.getFilmThickness();
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
