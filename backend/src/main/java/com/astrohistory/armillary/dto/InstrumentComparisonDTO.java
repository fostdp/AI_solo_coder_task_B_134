package com.astrohistory.armillary.dto;

import com.astrohistory.armillary.enums.InstrumentType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InstrumentComparisonDTO {

    private UUID instrumentId;
    private String instrumentName;
    private InstrumentType instrumentType;
    private String axisName;

    private FrictionMetricsDTO frictionMetrics;
    private WearMetricsDTO wearMetrics;
    private GeometryMetricsDTO geometryMetrics;
    private PointingMetricsDTO pointingMetrics;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FrictionMetricsDTO {
        private double lambdaRatio;
        private double filmThicknessUm;
        private double frictionCoefficient;
        private double asperityContactRatio;
        private double contactPressureMPa;
        private String lubricationRegime;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WearMetricsDTO {
        private double wearRateMPerSec;
        private double wearDepthMm;
        private double maxAllowableWearMm;
        private double wearPercentage;
        private double estimatedLifetimeHours;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GeometryMetricsDTO {
        private double perpendicularityErrorArcsec;
        private double axialRunoutMicrometers;
        private double radialRunoutMicrometers;
        private double totalGeometricErrorArcsec;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PointingMetricsDTO {
        private double azimuthErrorArcmin;
        private double altitudeErrorArcmin;
        private double totalPointingErrorArcmin;
        private double geometricContributionArcmin;
        private double errorUncertaintyArcmin;
    }

    private Map<String, Object> metadata;
    private List<String> keyFeatures;
}
