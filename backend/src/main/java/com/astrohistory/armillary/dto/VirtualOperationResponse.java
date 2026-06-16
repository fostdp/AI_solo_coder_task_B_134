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

    private double cumulativeWearMm;
    private double estimatedTimeToFailureHours;
    private int stressLevel;

    private String targetStarName;
    private Map<String, Double> axisPositions;
    private Map<String, String> statusMessages;
}
