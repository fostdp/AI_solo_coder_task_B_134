package com.astrohistory.armillary.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VirtualOperationResponse {

    private double currentAzimuthDeg;
    private double currentAltitudeDeg;
    private double currentRightAscensionDeg;
    private double currentDeclinationDeg;

    private double azimuthErrorArcmin;
    private double altitudeErrorArcmin;
    private double totalPointingErrorArcmin;
    private double errorUncertaintyArcmin;

    private double currentFrictionTorqueNm;
    private double currentWearRateMPerSec;
    private double currentLambdaRatio;
    private double currentFilmThicknessUm;
    private double frictionCoefficient;

    private Double cumulativeWearMm;
    private Double estimatedTimeToFailureHours;
    private Integer stressLevel;
    private String targetStarName;

    private Double operationTorqueRequiredNm;
    private Double inertiaResistanceNm;
    private Double dampingCoefficient;
    private Double currentAngularAccelerationRadS2;
    private Double hapticFeedbackIntensity;
    private String forceFeedbackStatus;
    private Double estimatedManualForceN;

    private Map<String, Double> axisPositions;
    private Map<String, String> statusMessages;
}
