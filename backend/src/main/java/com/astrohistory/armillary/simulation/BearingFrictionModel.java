package com.astrohistory.armillary.simulation;

import com.astrohistory.armillary.dto.LubricantComparisonDTO;
import com.astrohistory.armillary.dto.SensorDataDTO;
import com.astrohistory.armillary.entity.BearingConfig;
import com.astrohistory.armillary.entity.FrictionSimulation;
import com.astrohistory.armillary.enums.BearingTechnologyLevel;
import com.astrohistory.armillary.enums.InstrumentType;
import com.astrohistory.armillary.enums.LubricantType;
import org.apache.commons.math3.special.Erf;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;

@Component
public class BearingFrictionModel {

    private static final double PI = Math.PI;
    private static final double ROUGHNESS_COMBINED = 0.4e-6;

    private static final Map<String, MaterialPair> EXPERIMENTAL_WEAR_COEFFICIENTS = new HashMap<>();

    static {
        EXPERIMENTAL_WEAR_COEFFICIENTS.put("BRONZE_CASTIRON", new MaterialPair(
                "锡青铜", "灰铸铁",
                180e6, 220e6,
                new LubricationRegimeData(5.0e-4, 2.0e-3, 0.0),
                new LubricationRegimeData(1.0e-5, 5.0e-5, 1.0),
                new LubricationRegimeData(5.0e-6, 1.0e-5, 2.0),
                new LubricationRegimeData(1.0e-7, 5.0e-7, 3.5),
                4.5e-5, 0.85
        ));

        EXPERIMENTAL_WEAR_COEFFICIENTS.put("BRONZE_STEEL", new MaterialPair(
                "锡青铜", "碳素钢",
                180e6, 785e6,
                new LubricationRegimeData(3.0e-4, 1.5e-3, 0.0),
                new LubricationRegimeData(8.0e-6, 4.0e-5, 1.0),
                new LubricationRegimeData(4.0e-6, 8.0e-6, 2.0),
                new LubricationRegimeData(8.0e-8, 3.0e-7, 3.5),
                3.2e-5, 0.80
        ));

        EXPERIMENTAL_WEAR_COEFFICIENTS.put("CASTIRON_CASTIRON", new MaterialPair(
                "灰铸铁", "灰铸铁",
                220e6, 220e6,
                new LubricationRegimeData(8.0e-4, 3.0e-3, 0.0),
                new LubricationRegimeData(2.0e-5, 8.0e-5, 1.0),
                new LubricationRegimeData(8.0e-6, 2.0e-5, 2.0),
                new LubricationRegimeData(2.0e-7, 8.0e-7, 3.5),
                6.5e-5, 0.90
        ));

        EXPERIMENTAL_WEAR_COEFFICIENTS.put("BRASS_STEEL", new MaterialPair(
                "黄铜", "碳素钢",
                120e6, 785e6,
                new LubricationRegimeData(4.0e-4, 1.8e-3, 0.0),
                new LubricationRegimeData(1.2e-5, 5.5e-5, 1.0),
                new LubricationRegimeData(5.5e-6, 1.2e-5, 2.0),
                new LubricationRegimeData(1.0e-7, 4.0e-7, 3.5),
                3.8e-5, 0.82
        ));
    }

    public FrictionSimulationResult simulate(BearingConfig config, SensorDataDTO sensorData,
                                             double accumulatedWear, LocalDateTime simulationTime) {

        double innerDiameter = config.getInnerDiameter().doubleValue() / 1000.0;
        double outerDiameter = config.getOuterDiameter().doubleValue() / 1000.0;
        double width = config.getWidth().doubleValue() / 1000.0;
        double radialClearance = config.getInitialClearance().doubleValue() / 1000.0 - accumulatedWear / 1000.0;
        double lubricantViscosity = config.getLubricantViscosity().doubleValue();
        double elasticModulus = config.getElasticModulus().doubleValue() * 1e6;
        double poissonRatio = config.getPoissonRatio().doubleValue();
        double hardness = config.getHardness().doubleValue() * 1e6;
        double wearCoefficient = config.getWearCoefficient().doubleValue();

        String innerMaterial = config.getInnerRingMaterial() != null ?
                config.getInnerRingMaterial() : "锡青铜";
        String outerMaterial = config.getOuterRingMaterial() != null ?
                config.getOuterRingMaterial() : "灰铸铁";
        MaterialPair materialPair = lookupMaterialPair(innerMaterial, outerMaterial);
        double surfaceRoughnessRa = config.getSurfaceRoughnessRa() != null ?
                config.getSurfaceRoughnessRa().doubleValue() * 1e-6 : 0.2e-6;
        double roughnessCombined = Math.sqrt(2) * surfaceRoughnessRa;

        double rotationalSpeed = sensorData.getRotationalSpeed() != null ?
                sensorData.getRotationalSpeed().doubleValue() : 0.0;
        double radialLoad = sensorData.getLoadRadial() != null ?
                sensorData.getLoadRadial().doubleValue() * 9.81 : 500.0;
        double axialLoad = sensorData.getLoadAxial() != null ?
                sensorData.getLoadAxial().doubleValue() * 9.81 : 100.0;
        double temperature = sensorData.getTemperature() != null ?
                sensorData.getTemperature().doubleValue() : 25.0;

        double effectiveRadius = (innerDiameter + outerDiameter) / 4.0;
        double surfaceVelocity = PI * effectiveRadius * rotationalSpeed / 60.0;

        double adjustedViscosity = lubricantViscosity * Math.exp(-0.03 * (temperature - 40.0));

        double equivalentRadius = effectiveRadius;
        double equivalentModulus = 2.0 * elasticModulus / (1.0 - poissonRatio * poissonRatio);

        double hertzHalfWidth = Math.sqrt(4.0 * radialLoad * equivalentRadius / (PI * width * equivalentModulus));
        double maxContactPressure = 2.0 * radialLoad / (PI * width * hertzHalfWidth);

        double filmThickness = calculateElastohydrodynamicFilmThickness(
                adjustedViscosity, surfaceVelocity, equivalentModulus, equivalentRadius,
                radialLoad, width, poissonRatio);

        double lambdaRatio = filmThickness / roughnessCombined;

        double asperityContactRatio = calculateAsperityContactRatio(lambdaRatio);
        double frictionCoefficient = calculateFrictionCoefficient(
                asperityContactRatio, adjustedViscosity, surfaceVelocity,
                maxContactPressure, hardness, lambdaRatio);

        double totalLoad = Math.sqrt(radialLoad * radialLoad + axialLoad * axialLoad);
        double frictionForce = frictionCoefficient * totalLoad;
        double frictionTorque = frictionForce * effectiveRadius;

        double experimentalWearCoefficient = calculateExperimentalWearCoefficient(
                materialPair, lambdaRatio, hardness,
                frictionCoefficient);

        double wearRate = calculateArchardWearRate(
                experimentalWearCoefficient, frictionCoefficient, totalLoad,
                surfaceVelocity, materialPair.softerMaterialHardness);

        double timeStep = 60.0;
        double wearIncrement = wearRate * timeStep;
        double totalWearDepth = accumulatedWear + wearIncrement;

        return new FrictionSimulationResult(
                lambdaRatio, filmThickness * 1e6, maxContactPressure / 1e6,
                frictionCoefficient, asperityContactRatio, wearRate,
                totalWearDepth, frictionTorque
        );
    }

    private double calculateElastohydrodynamicFilmThickness(
            double viscosity, double velocity, double eModulus,
            double radius, double load, double width, double poisson) {

        if (velocity < 0.001) {
            return 0.05e-6;
        }

        double dimensionlessLoad = load / (eModulus * radius * width);
        double dimensionlessVelocity = 6.0 * viscosity * velocity / (eModulus * radius);
        double dimensionlessMaterial = 2.0 * (1.0 - poisson * poisson) *
                Math.pow(viscosity * velocity / (eModulus * Math.sqrt(radius * dimensionlessLoad)), 0.5);

        double hamrockDowson = 3.63 * Math.pow(dimensionlessVelocity, 0.68)
                * Math.pow(1.0 / dimensionlessLoad, 0.073)
                * (1.0 - Math.exp(-0.68 * dimensionlessMaterial));

        double centralFilmThickness = hamrockDowson * radius *
                Math.pow(viscosity * velocity / (eModulus * radius), 0.5);

        return Math.max(centralFilmThickness, 0.01e-6);
    }

    private double calculateAsperityContactRatio(double lambdaRatio) {
        if (lambdaRatio > 5.0) {
            return 0.0;
        } else if (lambdaRatio < 0.5) {
            return 1.0;
        } else {
            double x = (lambdaRatio - 3.0) / Math.sqrt(2.0);
            return 0.5 * (1.0 + Erf.erf(x));
        }
    }

    private double calculateFrictionCoefficient(
            double asperityRatio, double viscosity, double velocity,
            double maxPressure, double hardness, double lambdaRatio) {

        double hydrodynamicFriction = 0.0;
        if (velocity > 0.001 && lambdaRatio > 1.0) {
            double sommerfeldNumber = viscosity * velocity / maxPressure;
            hydrodynamicFriction = 0.001 + 0.1 * Math.sqrt(sommerfeldNumber);
        }

        double boundaryFriction = 0.15 + 0.3 * (1.0 - Math.exp(-lambdaRatio / 1.5));

        return (1.0 - asperityRatio) * Math.min(hydrodynamicFriction, 0.05) +
                asperityRatio * boundaryFriction;
    }

    private double calculateArchardWearRate(
            double wearCoefficient, double frictionCoeff,
            double load, double slidingDistancePerSecond, double hardness) {

        double k = wearCoefficient;
        double w = load;
        double s = slidingDistancePerSecond;
        double h = hardness;

        return k * frictionCoeff * w * s / h;
    }

    private MaterialPair lookupMaterialPair(String innerMaterial, String outerMaterial) {
        String normalizedInner = normalizeMaterialName(innerMaterial);
        String normalizedOuter = normalizeMaterialName(outerMaterial);

        String key = normalizedInner + "_" + normalizedOuter;
        MaterialPair pair = EXPERIMENTAL_WEAR_COEFFICIENTS.get(key);
        if (pair == null) {
            String reverseKey = normalizedOuter + "_" + normalizedInner;
            pair = EXPERIMENTAL_WEAR_COEFFICIENTS.get(reverseKey);
        }
        return pair != null ? pair : EXPERIMENTAL_WEAR_COEFFICIENTS.get("BRONZE_CASTIRON");
    }

    private String normalizeMaterialName(String material) {
        if (material == null) return "BRONZE";
        String lower = material.toLowerCase();
        if (lower.contains("青铜") || lower.contains("bronze") || lower.contains("锡青铜")) {
            return "BRONZE";
        } else if (lower.contains("黄铜") || lower.contains("brass")) {
            return "BRASS";
        } else if (lower.contains("铸铁") || lower.contains("cast") || lower.contains("铁")) {
            return "CASTIRON";
        } else if (lower.contains("钢") || lower.contains("steel")) {
            return "STEEL";
        }
        return "BRONZE";
    }

    private double calculateExperimentalWearCoefficient(
            MaterialPair materialPair, double lambdaRatio,
            double hardness, double frictionCoeff) {

        LubricationRegimeData regime;
        if (lambdaRatio < 0.5) {
            regime = materialPair.boundaryLubrication;
        } else if (lambdaRatio < 1.5) {
            double t = (lambdaRatio - 0.5) / 1.0;
            regime = interpolateRegime(
                    materialPair.boundaryLubrication,
                    materialPair.mixedLubrication, t);
        } else if (lambdaRatio < 3.0) {
            double t = (lambdaRatio - 1.5) / 1.5;
            regime = interpolateRegime(
                    materialPair.mixedLubrication,
                    materialPair.elastohydrodynamicLubrication, t);
        } else {
            regime = materialPair.hydrodynamicLubrication;
        }

        double baseK = regime.minWearCoeff + Math.random() *
                (regime.maxWearCoeff - regime.minWearCoeff);

        double hardnessRatio = materialPair.referenceHardness / hardness;
        double hardnessCorrection = Math.pow(hardnessRatio, materialPair.hardnessExponent);

        double pressureVelocityFactor = Math.min(1.0 + frictionCoeff * 0.5, 3.0);

        return baseK * hardnessCorrection * pressureVelocityFactor;
    }

    private LubricationRegimeData interpolateRegime(
            LubricationRegimeData from, LubricationRegimeData to, double t) {
        double minK = from.minWearCoeff + t * (to.minWearCoeff - from.minWearCoeff);
        double maxK = from.maxWearCoeff + t * (to.maxWearCoeff - from.maxWearCoeff);
        double lambda = from.lambdaThreshold + t * (to.lambdaThreshold - from.lambdaThreshold);
        return new LubricationRegimeData(minK, maxK, lambda);
    }

    public FrictionSimulationResult simulateWithLubricantOverride(
            BearingConfig config, SensorDataDTO sensorData,
            double accumulatedWear, LocalDateTime simulationTime,
            LubricantType lubricantType) {

        double lubricantViscosity = lubricantType.getViscosityAtTemperature(
                sensorData.getTemperature() != null ?
                        sensorData.getTemperature().doubleValue() : 25.0);

        BearingConfig configCopy = BearingConfig.builder()
                .id(config.getId())
                .instrument(config.getInstrument())
                .axisName(config.getAxisName())
                .axisType(config.getAxisType())
                .bearingType(config.getBearingType())
                .material(config.getMaterial())
                .innerRingMaterial(config.getInnerRingMaterial())
                .outerRingMaterial(config.getOuterRingMaterial())
                .rollingElementMaterial(config.getRollingElementMaterial())
                .surfaceRoughnessRa(config.getSurfaceRoughnessRa())
                .perpendicularityError(config.getPerpendicularityError())
                .axialRunout(config.getAxialRunout())
                .radialRunout(config.getRadialRunout())
                .innerDiameter(config.getInnerDiameter())
                .outerDiameter(config.getOuterDiameter())
                .width(config.getWidth())
                .initialClearance(config.getInitialClearance())
                .lubricantViscosity(BigDecimal.valueOf(lubricantViscosity))
                .lubricantType(lubricantType)
                .technologyLevel(config.getTechnologyLevel())
                .elasticModulus(config.getElasticModulus())
                .poissonRatio(config.getPoissonRatio())
                .hardness(config.getHardness())
                .wearCoefficient(BigDecimal.valueOf(
                        config.getWearCoefficient().doubleValue() *
                                lubricantType.getBaseWearMultiplier()))
                .maxAllowableWear(config.getMaxAllowableWear())
                .build();

        FrictionSimulationResult baseResult = simulate(
                configCopy, sensorData, accumulatedWear, simulationTime);

        double adjustedWearRate = baseResult.getWearRate() *
                lubricantType.getBaseWearMultiplier();
        double adjustedFrictionCoeff = Math.max(
                baseResult.getFrictionCoefficient() *
                        (lubricantType.getFrictionCoefficientBase() / 0.025),
                lubricantType.getFrictionCoefficientBase() * 0.5);

        return new FrictionSimulationResult(
                baseResult.getLambdaRatio(),
                baseResult.getFilmThickness(),
                baseResult.getContactPressure(),
                adjustedFrictionCoeff,
                baseResult.getAsperityContactRatio(),
                adjustedWearRate,
                baseResult.getTotalWearDepth(),
                baseResult.getFrictionTorque()
        );
    }

    public FrictionSimulationResult simulateWithTechnologyLevel(
            BearingConfig config, SensorDataDTO sensorData,
            double accumulatedWear, LocalDateTime simulationTime,
            BearingTechnologyLevel techLevel) {

        FrictionSimulationResult baseResult = simulate(
                config, sensorData, accumulatedWear, simulationTime);

        double techFrictionFactor = techLevel.getFrictionCoefficientTypical() / 0.08;
        double techWearFactor = techLevel.getTypicalWearRate() / 1.0;
        double techRunoutFactor = techLevel.getTypicalRunoutMicrometers() / 3.2;

        double adjustedFrictionCoeff = Math.max(
                baseResult.getFrictionCoefficient() * techFrictionFactor,
                techLevel.getFrictionCoefficientMin());

        double adjustedWearRate = baseResult.getWearRate() * techWearFactor;
        double adjustedLambda = baseResult.getLambdaRatio() / techRunoutFactor;

        return new FrictionSimulationResult(
                adjustedLambda,
                baseResult.getFilmThickness() / techRunoutFactor,
                baseResult.getContactPressure(),
                adjustedFrictionCoeff,
                Math.min(baseResult.getAsperityContactRatio() * techRunoutFactor, 1.0),
                adjustedWearRate,
                baseResult.getTotalWearDepth(),
                baseResult.getFrictionTorque() * techFrictionFactor
        );
    }

    public String getLubricationRegimeName(double lambdaRatio) {
        if (lambdaRatio >= 3.0) {
            return "全膜流体润滑";
        } else if (lambdaRatio >= 1.5) {
            return "弹流润滑";
        } else if (lambdaRatio >= 0.5) {
            return "混合润滑";
        } else {
            return "边界润滑";
        }
    }

    public List<LubricantComparisonDTO.LubricantDataPoint> compareAllLubricants(
            BearingConfig config, SensorDataDTO baseSensorData,
            double accumulatedWear, LocalDateTime time) {

        List<LubricantComparisonDTO.LubricantDataPoint> results = new ArrayList<>();

        for (LubricantType lubricant : LubricantType.values()) {
            FrictionSimulationResult simResult = simulateWithLubricantOverride(
                    config, baseSensorData, accumulatedWear, time, lubricant);

            double wearAfter1000h = simResult.getWearRate() * 1000.0 * 3600.0 * 1000.0;
            double maxWear = config.getMaxAllowableWear() != null ?
                    config.getMaxAllowableWear().doubleValue() : 0.1;
            double lifetime = simResult.getWearRate() > 0 ?
                    (maxWear * 0.001) / (simResult.getWearRate() * 3600.0) : 99999.0;

            double viscosityAtTemp = lubricant.getViscosityAtTemperature(
                    baseSensorData.getTemperature() != null ?
                            baseSensorData.getTemperature().doubleValue() : 25.0);
            double tempStability = 1.0 - Math.abs(viscosityAtTemp - lubricant.getViscosityAt40C())
                    / lubricant.getViscosityAt40C();

            List<String> historicalApps = getHistoricalApplications(lubricant);

            results.add(LubricantComparisonDTO.LubricantDataPoint.builder()
                    .lubricantType(lubricant)
                    .displayName(lubricant.getDisplayName())
                    .frictionCoefficient(simResult.getFrictionCoefficient())
                    .lambdaRatio(simResult.getLambdaRatio())
                    .filmThicknessUm(simResult.getFilmThickness())
                    .wearRateMPerSec(simResult.getWearRate())
                    .wearAfter1000HoursMm(wearAfter1000h)
                    .estimatedLifetimeHours(lifetime)
                    .viscosityAtOperatingTemp(viscosityAtTemp)
                    .temperatureStabilityIndex(Math.max(0.1, tempStability))
                    .oxidationResistanceScore(getOxidationResistanceScore(lubricant))
                    .historicallyAvailable(isHistoricallyAvailable(lubricant))
                    .historicalCentury(getHistoricalCentury(lubricant))
                    .historicalApplications(historicalApps)
                    .build());
        }

        return results;
    }

    public Map<BearingTechnologyLevel, FrictionSimulationResult> compareTechnologyLevels(
            BearingConfig config, SensorDataDTO sensorData,
            double accumulatedWear, LocalDateTime time) {

        Map<BearingTechnologyLevel, FrictionSimulationResult> results = new LinkedHashMap<>();

        for (BearingTechnologyLevel techLevel : BearingTechnologyLevel.values()) {
            results.put(techLevel, simulateWithTechnologyLevel(
                    config, sensorData, accumulatedWear, time, techLevel));
        }

        return results;
    }

    private List<String> getHistoricalApplications(LubricantType type) {
        return switch (type) {
            case ANIMAL_FAT -> Arrays.asList("汉代浑仪轴承", "唐代水运浑仪", "中世纪欧洲天文仪器");
            case VEGETABLE_OIL -> Arrays.asList("元代简仪", "明代浑仪", "宋代水运仪象台", "清代象限仪");
            case MINERAL_OIL -> Arrays.asList("清末洋务运动仪器", "民国时期天文台", "近代工业机械");
            case MODERN_SYNTHETIC -> Arrays.asList("现代望远镜", "卫星姿态控制", "精密数控机床");
            case MERCURY -> Arrays.asList("北宋水运仪象台（传说）", "伊斯兰天文仪器");
            case DRY -> Arrays.asList("早期简单仪器", "临时装置");
        };
    }

    private boolean isHistoricallyAvailable(LubricantType type) {
        return switch (type) {
            case ANIMAL_FAT, VEGETABLE_OIL, DRY, MERCURY -> true;
            case MINERAL_OIL -> false;
            case MODERN_SYNTHETIC -> false;
        };
    }

    private int getHistoricalCentury(LubricantType type) {
        return switch (type) {
            case DRY -> -20;
            case ANIMAL_FAT -> -10;
            case VEGETABLE_OIL -> 1;
            case MERCURY -> 10;
            case MINERAL_OIL -> 19;
            case MODERN_SYNTHETIC -> 20;
        };
    }

    private double getOxidationResistanceScore(LubricantType type) {
        return switch (type) {
            case ANIMAL_FAT -> 0.3;
            case VEGETABLE_OIL -> 0.5;
            case MINERAL_OIL -> 0.75;
            case MODERN_SYNTHETIC -> 0.95;
            case MERCURY -> 0.99;
            case DRY -> 1.0;
        };
    }

    public Map<String, Object> getInstrumentTypeBearingCharacteristics(InstrumentType type) {
        Map<String, Object> characteristics = new LinkedHashMap<>();

        BearingTechnologyLevel techLevel = BearingTechnologyLevel.fromInstrumentType(type);
        LubricantType typicalLubricant = switch (type) {
            case ARMILLARY_SPHERE -> LubricantType.ANIMAL_FAT;
            case ARMILLARY_TRADITIONAL, ARMILLARY_SIMPLIFIED, QUADRANT -> LubricantType.VEGETABLE_OIL;
            case MODERN_PRECISE -> LubricantType.MODERN_SYNTHETIC;
        };

        characteristics.put("instrumentType", type);
        characteristics.put("displayName", type.getDisplayName());
        characteristics.put("originYear", type.getOriginYear());
        characteristics.put("axisCount", type.getAxisCount());
        characteristics.put("description", type.getDescription());
        characteristics.put("technologyLevel", techLevel);
        characteristics.put("typicalLubricant", typicalLubricant);
        characteristics.put("typicalFrictionCoefficient", techLevel.getFrictionCoefficientTypical());
        characteristics.put("typicalRunoutMicrometers", techLevel.getTypicalRunoutMicrometers());
        characteristics.put("estimatedPrecisionArcsec",
                techLevel.getTypicalRunoutMicrometers() * 10.0 / type.getAxisCount());

        List<String> keyFeatures = switch (type) {
            case ARMILLARY_SIMPLIFIED -> Arrays.asList(
                    "郭守敬1279年创制",
                    "简化结构，减少圈环遮挡",
                    "赤道装置，便于观测",
                    "青铜-铸铁摩擦副"
            );
            case ARMILLARY_SPHERE -> Arrays.asList(
                    "汉代张衡创制",
                    "多重同心环圈结构",
                    "可以直接读数",
                    "结构复杂，摩擦较大"
            );
            case QUADRANT -> Arrays.asList(
                    "清代南怀仁监制",
                    "单臂90度象限弧",
                    "地平坐标系测量",
                    "精度受单轴限制"
            );
            case ARMILLARY_TRADITIONAL -> Arrays.asList(
                    "宋代苏颂水运仪象台",
                    "三重环圈结构",
                    "水力驱动自动运转",
                    "集观测、演示、报时于一体"
            );
            case MODERN_PRECISE -> Arrays.asList(
                    "现代精密轴系",
                    "气浮/磁浮轴承",
                    "纳米级跳动精度",
                    "闭环反馈控制"
            );
        };
        characteristics.put("keyFeatures", keyFeatures);

        return characteristics;
    }

    public static class LubricationRegimeData {
        public final double minWearCoeff;
        public final double maxWearCoeff;
        public final double lambdaThreshold;

        public LubricationRegimeData(double minWearCoeff, double maxWearCoeff, double lambdaThreshold) {
            this.minWearCoeff = minWearCoeff;
            this.maxWearCoeff = maxWearCoeff;
            this.lambdaThreshold = lambdaThreshold;
        }
    }

    public static class MaterialPair {
        public final String material1;
        public final String material2;
        public final double material1Hardness;
        public final double material2Hardness;
        public final double softerMaterialHardness;
        public final LubricationRegimeData boundaryLubrication;
        public final LubricationRegimeData mixedLubrication;
        public final LubricationRegimeData elastohydrodynamicLubrication;
        public final LubricationRegimeData hydrodynamicLubrication;
        public final double referenceWearCoefficient;
        public final double hardnessExponent;
        public final double referenceHardness;

        public MaterialPair(String material1, String material2,
                           double material1Hardness, double material2Hardness,
                           LubricationRegimeData boundary, LubricationRegimeData mixed,
                           LubricationRegimeData ehl, LubricationRegimeData hd,
                           double referenceK, double hardnessExp) {
            this.material1 = material1;
            this.material2 = material2;
            this.material1Hardness = material1Hardness;
            this.material2Hardness = material2Hardness;
            this.softerMaterialHardness = Math.min(material1Hardness, material2Hardness);
            this.boundaryLubrication = boundary;
            this.mixedLubrication = mixed;
            this.elastohydrodynamicLubrication = ehl;
            this.hydrodynamicLubrication = hd;
            this.referenceWearCoefficient = referenceK;
            this.hardnessExponent = hardnessExp;
            this.referenceHardness = this.softerMaterialHardness;
        }
    }

    public static class FrictionSimulationResult {
        private final double lambdaRatio;
        private final double filmThickness;
        private final double contactPressure;
        private final double frictionCoefficient;
        private final double asperityContactRatio;
        private final double wearRate;
        private final double totalWearDepth;
        private final double frictionTorque;

        public FrictionSimulationResult(double lambdaRatio, double filmThickness,
                                        double contactPressure, double frictionCoefficient,
                                        double asperityContactRatio, double wearRate,
                                        double totalWearDepth, double frictionTorque) {
            this.lambdaRatio = lambdaRatio;
            this.filmThickness = filmThickness;
            this.contactPressure = contactPressure;
            this.frictionCoefficient = frictionCoefficient;
            this.asperityContactRatio = asperityContactRatio;
            this.wearRate = wearRate;
            this.totalWearDepth = totalWearDepth;
            this.frictionTorque = frictionTorque;
        }

        public FrictionSimulation toEntity(BearingConfig config, LocalDateTime time) {
            return FrictionSimulation.builder()
                    .instrument(config.getInstrument())
                    .axisName(config.getAxisName())
                    .simulationTime(time)
                    .lambdaRatio(BigDecimal.valueOf(lambdaRatio).setScale(6, RoundingMode.HALF_UP))
                    .filmThickness(BigDecimal.valueOf(filmThickness).setScale(8, RoundingMode.HALF_UP))
                    .contactPressure(BigDecimal.valueOf(contactPressure).setScale(4, RoundingMode.HALF_UP))
                    .frictionCoefficient(BigDecimal.valueOf(frictionCoefficient).setScale(8, RoundingMode.HALF_UP))
                    .asperityContactRatio(BigDecimal.valueOf(asperityContactRatio).setScale(6, RoundingMode.HALF_UP))
                    .wearRate(BigDecimal.valueOf(wearRate).setScale(10, RoundingMode.HALF_UP))
                    .totalWearDepth(BigDecimal.valueOf(totalWearDepth).setScale(8, RoundingMode.HALF_UP))
                    .build();
        }

        public double getLambdaRatio() { return lambdaRatio; }
        public double getFilmThickness() { return filmThickness; }
        public double getContactPressure() { return contactPressure; }
        public double getFrictionCoefficient() { return frictionCoefficient; }
        public double getAsperityContactRatio() { return asperityContactRatio; }
        public double getWearRate() { return wearRate; }
        public double getTotalWearDepth() { return totalWearDepth; }
        public double getFrictionTorque() { return frictionTorque; }
    }
}
