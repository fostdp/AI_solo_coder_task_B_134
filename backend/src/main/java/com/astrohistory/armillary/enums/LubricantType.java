package com.astrohistory.armillary.enums;

import lombok.Getter;

@Getter
public enum LubricantType {

    ANIMAL_FAT("动物油", "Animal Fat / Tallow",
            0.045, 0.03, 38.0, 2.5,
            "古代常用，如猪油、牛油，低温易凝固",
            0.002, 5.0),
    VEGETABLE_OIL("植物油", "Vegetable Oil",
            0.035, 0.028, 25.0, 1.8,
            "芝麻油、桐油，润滑性好但易氧化",
            0.0015, 3.0),
    MINERAL_OIL("矿物油", "Mineral Oil",
            0.025, 0.02, 45.0, 1.2,
            "近现代石油基润滑油，性能稳定",
            0.001, 2.0),
    MODERN_SYNTHETIC("现代合成油", "Modern Synthetic Oil",
            0.015, 0.01, 60.0, 0.8,
            "PAO合成油、酯类油，现代精密轴承标准",
            0.0005, 0.5),
    MERCURY("水银悬浮", "Mercury Suspension",
            0.001, 0.0008, 20.0, 0.3,
            "古代特殊工艺，低摩擦但有毒",
            0.0001, 10.0),
    DRY("无润滑/干摩擦", "Dry / No Lubrication",
            0.5, 0.3, 15.0, 0.0,
            "金属直接接触，磨损极快",
            0.01, 50.0);

    private final String displayName;
    private final String englishName;
    private final double viscosityAt40C;
    private final double viscosityAt100C;
    private final double flashPointC;
    private final double frictionCoefficientBase;
    private final String description;
    private final double typicalWearRateFactor;
    private final double baseWearMultiplier;

    LubricantType(String displayName, String englishName,
                  double viscosityAt40C, double viscosityAt100C,
                  double flashPointC, double frictionCoefficientBase,
                  String description, double typicalWearRateFactor,
                  double baseWearMultiplier) {
        this.displayName = displayName;
        this.englishName = englishName;
        this.viscosityAt40C = viscosityAt40C;
        this.viscosityAt100C = viscosityAt100C;
        this.flashPointC = flashPointC;
        this.frictionCoefficientBase = frictionCoefficientBase;
        this.description = description;
        this.typicalWearRateFactor = typicalWearRateFactor;
        this.baseWearMultiplier = baseWearMultiplier;
    }

    public double getViscosityAtTemperature(double temperatureC) {
        double t = Math.max(temperatureC, 10.0);
        if (t <= 40.0) {
            return viscosityAt40C;
        } else if (t >= 100.0) {
            return viscosityAt100C;
        }
        double ratio = (t - 40.0) / 60.0;
        return viscosityAt40C * (1.0 - ratio) + viscosityAt100C * ratio;
    }

    public static LubricantType fromString(String value) {
        if (value == null) return VEGETABLE_OIL;
        for (LubricantType type : values()) {
            if (type.name().equalsIgnoreCase(value) ||
                    type.displayName.equals(value) ||
                    type.englishName.equalsIgnoreCase(value)) {
                return type;
            }
        }
        return VEGETABLE_OIL;
    }
}
