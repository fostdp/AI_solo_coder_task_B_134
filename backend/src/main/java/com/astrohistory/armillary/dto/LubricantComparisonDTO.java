package com.astrohistory.armillary.dto;

import com.astrohistory.armillary.enums.LubricantType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LubricantComparisonDTO {

    private List<LubricantDataPoint> lubricantData;
    private SimulationConditions conditions;
    private RankingSummary ranking;
    private List<String> historicalNotes;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LubricantDataPoint {
        private LubricantType lubricantType;
        private String displayName;

        private double frictionCoefficient;
        private double lambdaRatio;
        private double filmThicknessUm;
        private double wearRateMPerSec;
        private double wearAfter1000HoursMm;
        private double estimatedLifetimeHours;

        private double viscosityAtOperatingTemp;
        private double temperatureStabilityIndex;
        private double oxidationResistanceScore;

        private boolean historicallyAvailable;
        private int historicalCentury;
        private List<String> historicalApplications;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SimulationConditions {
        private double loadKg;
        private double rotationalSpeedRpm;
        private double temperatureC;
        private String materialPair;
        private double surfaceRoughnessRa;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RankingSummary {
        private Map<String, Double> byWearResistance;
        private Map<String, Double> byFrictionReduction;
        private Map<String, Double> byHistoricalPracticality;
        private Map<String, Double> byOverallScore;
    }
}
