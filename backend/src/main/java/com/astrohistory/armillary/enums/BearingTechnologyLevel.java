package com.astrohistory.armillary.enums;

import lombok.Getter;

@Getter
public enum BearingTechnologyLevel {

    ANCIENT_BRONZE_WOOD("古代青铜-木质轴承", "Ancient Bronze-Wood Bearing",
            6.3, 3.2, 0.8, 1.5,
            "滑动摩擦为主，手工加工精度低"),
    ANCIENT_BRONZE_IRON("古代青铜-铸铁轴承", "Ancient Bronze-Cast Iron Bearing",
            3.2, 1.6, 0.4, 0.8,
            "郭守敬简仪典型轴承，精度显著提升"),
    EARLY_MODERN("近代机械轴承", "Early Modern Mechanical Bearing",
            1.0, 0.5, 0.1, 0.2,
            "19世纪工业革命时期，初步标准化"),
    MODERN_PLAIN("现代滑动轴承", "Modern Plain Bearing",
            0.5, 0.2, 0.05, 0.1,
            "精密加工+流体润滑"),
    MODERN_ROLLING("现代滚动轴承", "Modern Rolling Element Bearing",
            0.1, 0.05, 0.01, 0.02,
            "滚珠/滚柱轴承，标准工业级"),
    MODERN_PRECISE("现代精密气浮轴承", "Modern Precision Air Bearing",
            0.005, 0.002, 0.001, 0.0005,
            "航天级精度，摩擦接近零");

    private final String displayName;
    private final String englishName;
    private final double typicalRunoutMicrometers;
    private final double typicalWearRate;
    private final double frictionCoefficientMin;
    private final double frictionCoefficientTypical;
    private final String description;

    BearingTechnologyLevel(String displayName, String englishName,
                           double typicalRunoutMicrometers,
                           double typicalWearRate,
                           double frictionCoefficientMin,
                           double frictionCoefficientTypical,
                           String description) {
        this.displayName = displayName;
        this.englishName = englishName;
        this.typicalRunoutMicrometers = typicalRunoutMicrometers;
        this.typicalWearRate = typicalWearRate;
        this.frictionCoefficientMin = frictionCoefficientMin;
        this.frictionCoefficientTypical = frictionCoefficientTypical;
        this.description = description;
    }

    public static BearingTechnologyLevel fromInstrumentType(InstrumentType type) {
        return switch (type) {
            case ARMILLARY_SPHERE -> ANCIENT_BRONZE_WOOD;
            case ARMILLARY_TRADITIONAL -> ANCIENT_BRONZE_IRON;
            case ARMILLARY_SIMPLIFIED, QUADRANT -> ANCIENT_BRONZE_IRON;
            case MODERN_PRECISE -> MODERN_PRECISE;
        };
    }
}
