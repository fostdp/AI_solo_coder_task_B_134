package com.astrohistory.armillary.enums;

import lombok.Getter;

@Getter
public enum InstrumentType {

    ARMILLARY_SIMPLIFIED("简仪", "Yuan Dynasty Simplified Armillary", 1279,
            "元代郭守敬创制，结构简化，精度更高", 4),
    ARMILLARY_SPHERE("浑仪", "Traditional Armillary Sphere", -100,
            "汉代张衡创制，多重环圈结构", 7),
    QUADRANT("象限仪", "Mural Quadrant", 1673,
            "清代制造，用于测量天体地平高度", 1),
    MODERN_PRECISE("现代精密轴承仪器", "Modern Precision Bearing Instrument", 2024,
            "现代精密轴系对比基准", 3),
    ARMILLARY_TRADITIONAL("传统浑仪", "Traditional Chinese Armillary", 1092,
            "宋代苏颂水运仪象台浑仪", 6);

    private final String displayName;
    private final String englishName;
    private final int originYear;
    private final String description;
    private final int axisCount;

    InstrumentType(String displayName, String englishName, int originYear,
                   String description, int axisCount) {
        this.displayName = displayName;
        this.englishName = englishName;
        this.originYear = originYear;
        this.description = description;
        this.axisCount = axisCount;
    }

    public static InstrumentType fromString(String value) {
        if (value == null) return ARMILLARY_SIMPLIFIED;
        for (InstrumentType type : values()) {
            if (type.name().equalsIgnoreCase(value) ||
                    type.displayName.equals(value) ||
                    type.englishName.equalsIgnoreCase(value)) {
                return type;
            }
        }
        return ARMILLARY_SIMPLIFIED;
    }
}
