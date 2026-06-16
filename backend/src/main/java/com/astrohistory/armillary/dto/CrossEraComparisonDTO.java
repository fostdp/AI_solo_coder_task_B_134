package com.astrohistory.armillary.dto;

import com.astrohistory.armillary.enums.BearingTechnologyLevel;
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
public class CrossEraComparisonDTO {

    private List<EraBearingData> eraData;
    private SummaryComparison summary;
    private List<String> insights;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EraBearingData {
        private BearingTechnologyLevel technologyLevel;
        private String eraName;
        private int eraYearStart;
        private int eraYearEnd;

        private double typicalFrictionCoefficient;
        private double typicalWearRate;
        private double typicalRunoutMicrometers;
        private double estimatedPrecisionArcsec;

        private Map<String, Double> materialProperties;
        private List<LubricantType> commonLubricants;
        private String typicalApplications;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SummaryComparison {
        private double frictionCoefficientImprovementRatio;
        private double wearRateImprovementRatio;
        private double precisionImprovementRatio;
        private double lifetimeImprovementRatio;
        private Map<String, Double> eraRanking;
    }
}
