package com.astrohistory.armillary.enums;

import lombok.Getter;

@Getter
public enum LubricantType {

    ANIMAL_FAT("动物油", "Animal Fat / Tallow",
            0.045, 0.03, 38.0, 2.5,
            "古代常用，如猪油、牛油，低温易凝固",
            0.002, 5.0,
            98, 18.0, 0.0, 15.0, 1.2, 15.0, 2.5),
    VEGETABLE_OIL("植物油", "Vegetable Oil",
            0.035, 0.028, 25.0, 1.8,
            "芝麻油、桐油，润滑性好但易氧化",
            0.0015, 3.0,
            125, 16.5, -8.0, 20.0, 1.8, 12.0, 1.8),
    MINERAL_OIL("矿物油", "Mineral Oil",
            0.025, 0.02, 45.0, 1.2,
            "近现代石油基润滑油，性能稳定",
            0.001, 2.0,
            95, 22.0, -15.0, 35.0, 2.2, 8.0, 0.8),
    MODERN_SYNTHETIC("现代合成油", "Modern Synthetic Oil",
            0.015, 0.01, 60.0, 0.8,
            "PAO合成油、酯类油，现代精密轴承标准",
            0.0005, 0.5,
            160, 14.5, -55.0, 80.0, 3.5, 2.0, 0.3),
    MERCURY("水银悬浮", "Mercury Suspension",
            0.001, 0.0008, 20.0, 0.3,
            "古代特殊工艺，低摩擦但有毒",
            0.0001, 10.0,
            200, 0.0, -38.8, 356.6, 30.0, 0.5, 0.05),
    DRY("无润滑/干摩擦", "Dry / No Lubrication",
            0.5, 0.3, 15.0, 0.0,
            "金属直接接触，磨损极快",
            0.01, 50.0,
            0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0);

    private final String displayName;
    private final String englishName;
    private final double viscosityAt40C;
    private final double viscosityAt100C;
    private final double flashPointC;
    private final double frictionCoefficientBase;
    private final String description;
    private final double typicalWearRateFactor;
    private final double baseWearMultiplier;
    private final double viscosityIndex;
    private final double pressureViscosityCoefficient;
    private final double pourPointC;
    private final double boilingPointC;
    private final double specificGravity;
    private final double oxidationResistanceHours;
    private final double shearStabilityIndex;

    private static final double WATHER_CONSTANT_A = 10.4;
    private static final double MIN_TEMP_C = -60.0;
    private static final double MAX_TEMP_C = 400.0;

    LubricantType(String displayName, String englishName,
                  double viscosityAt40C, double viscosityAt100C,
                  double flashPointC, double frictionCoefficientBase,
                  String description, double typicalWearRateFactor,
                  double baseWearMultiplier,
                  double viscosityIndex, double pressureViscosityCoefficient,
                  double pourPointC, double boilingPointC,
                  double specificGravity, double oxidationResistanceHours,
                  double shearStabilityIndex) {
        this.displayName = displayName;
        this.englishName = englishName;
        this.viscosityAt40C = viscosityAt40C;
        this.viscosityAt100C = viscosityAt100C;
        this.flashPointC = flashPointC;
        this.frictionCoefficientBase = frictionCoefficientBase;
        this.description = description;
        this.typicalWearRateFactor = typicalWearRateFactor;
        this.baseWearMultiplier = baseWearMultiplier;
        this.viscosityIndex = viscosityIndex;
        this.pressureViscosityCoefficient = pressureViscosityCoefficient;
        this.pourPointC = pourPointC;
        this.boilingPointC = boilingPointC;
        this.specificGravity = specificGravity;
        this.oxidationResistanceHours = oxidationResistanceHours;
        this.shearStabilityIndex = shearStabilityIndex;
    }

    public double getViscosityAtTemperature(double temperatureC) {
        double t = Math.max(Math.min(temperatureC, MAX_TEMP_C), MIN_TEMP_C);
        if (t <= pourPointC && this != DRY) {
            return viscosityAt40C * 100.0;
        }
        if (t >= boilingPointC && this != DRY) {
            return viscosityAt100C * 0.1;
        }
        return calculateViscosityWalther(t);
    }

    public double calculateViscosityWalther(double temperatureC) {
        if (this == DRY) {
            return viscosityAt40C;
        }
        double tK = temperatureC + 273.15;
        double logV40 = Math.log10(Math.log10(viscosityAt40C * 1000.0 + 0.8));
        double logV100 = Math.log10(Math.log10(viscosityAt100C * 1000.0 + 0.8));
        double logT40 = Math.log10(313.15);
        double logT100 = Math.log10(373.15);
        double B = (logV40 - logV100) / (logT100 - logT40);
        double A = logV40 + B * logT40;
        double logLogV = A - B * Math.log10(tK);
        double logV = Math.pow(10.0, logLogV) - 0.8;
        double viscosityCST = Math.pow(10.0, logV);
        return Math.max(viscosityCST / 1000.0, 0.000001);
    }

    public double getViscosityAtPressure(double temperatureC, double pressureGPa) {
        double eta0 = getViscosityAtTemperature(temperatureC);
        if (this == DRY) {
            return eta0;
        }
        double alpha = pressureViscosityCoefficient * 1e-9;
        return eta0 * Math.exp(alpha * pressureGPa * 1e9);
    }

    public double calculateViscosityIndex() {
        if (this == DRY) return 0.0;
        double L = 0.8353 * Math.pow(viscosityAt40C, 1.4672);
        double H = 0.1684 * Math.pow(viscosityAt40C, 0.8219);
        if (L == H) return 100.0;
        double VI = 100.0 * (L - viscosityAt100C * 1000.0) / (L - H);
        return Math.max(0.0, Math.min(200.0, VI));
    }

    public double getShearStabilityIndex() {
        return shearStabilityIndex;
    }

    public boolean isInLiquidPhase(double temperatureC) {
        if (this == DRY) return false;
        return temperatureC > pourPointC && temperatureC < boilingPointC;
    }

    public double getTemperatureStabilityIndex() {
        if (this == DRY) return 0.0;
        double range = boilingPointC - pourPointC;
        double viNorm = viscosityIndex / 200.0;
        return range / 500.0 * 0.6 + viNorm * 0.4;
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

