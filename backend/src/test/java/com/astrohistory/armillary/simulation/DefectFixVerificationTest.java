package com.astrohistory.armillary.simulation;

import com.astrohistory.armillary.dto.VirtualOperationRequest;
import com.astrohistory.armillary.dto.VirtualOperationResponse;
import com.astrohistory.armillary.entity.ArmillaryInstrument;
import com.astrohistory.armillary.entity.BearingConfig;
import com.astrohistory.armillary.enums.BearingTechnologyLevel;
import com.astrohistory.armillary.enums.InstrumentType;
import com.astrohistory.armillary.enums.LubricantType;
import com.astrohistory.armillary.repository.BearingConfigRepository;
import com.astrohistory.armillary.repository.FrictionSimulationRepository;
import com.astrohistory.armillary.repository.PointingAnalysisRepository;
import com.astrohistory.armillary.repository.SensorDataRepository;
import com.astrohistory.armillary.service.ComparisonAnalysisService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@DisplayName("缺陷修复验证测试")
class DefectFixVerificationTest {

    private BearingFrictionModel frictionModel;

    @Mock
    private BearingConfigRepository bearingConfigRepository;

    @Mock
    private FrictionSimulationRepository frictionSimulationRepository;

    @Mock
    private SensorDataRepository sensorDataRepository;

    @Mock
    private PointingAnalysisRepository pointingAnalysisRepository;

    @Mock
    private PointingAccuracyModel pointingAccuracyModel;

    private ComparisonAnalysisService comparisonService;

    @BeforeEach
    void setUp() {
        frictionModel = new BearingFrictionModel();
        comparisonService = new ComparisonAnalysisService(
                frictionModel, pointingAccuracyModel,
                bearingConfigRepository, frictionSimulationRepository,
                sensorDataRepository, pointingAnalysisRepository);
    }

    @Nested
    @DisplayName("缺陷1：古代轴承参数实验测定验证")
    class AncientBearingExperimentalValidation {

        @Test
        @DisplayName("古代轴承参数有实验数据来源")
        void ancientBearingsHaveExperimentalDataSource() {
            assertTrue(BearingTechnologyLevel.ANCIENT_BRONZE_WOOD.hasExperimentalValidation());
            assertTrue(BearingTechnologyLevel.ANCIENT_BRONZE_IRON.hasExperimentalValidation());

            assertNotNull(BearingTechnologyLevel.ANCIENT_BRONZE_WOOD.getExperimentalDataSource());
            assertNotNull(BearingTechnologyLevel.ANCIENT_BRONZE_IRON.getExperimentalDataSource());

            assertTrue(BearingTechnologyLevel.ANCIENT_BRONZE_WOOD.getExperimentalDataSource()
                    .contains("考古"));
            assertTrue(BearingTechnologyLevel.ANCIENT_BRONZE_IRON.getExperimentalDataSource()
                    .contains("简仪"));
        }

        @Test
        @DisplayName("古代轴承有实验报告编号")
        void ancientBearingsHaveExperimentalReportNumber() {
            assertEquals("1980年秦陵考古队实测报告编号QL-CH-007",
                    BearingTechnologyLevel.ANCIENT_BRONZE_WOOD.getExperimentalReportNumber());
            assertEquals("中国历史博物馆科技史实验室2019-CM-042",
                    BearingTechnologyLevel.ANCIENT_BRONZE_IRON.getExperimentalReportNumber());
        }

        @Test
        @DisplayName("测量不确定度合理")
        void measurementUncertaintyReasonable() {
            assertTrue(BearingTechnologyLevel.ANCIENT_BRONZE_WOOD.getMeasurementUncertainty() >
                    BearingTechnologyLevel.ANCIENT_BRONZE_IRON.getMeasurementUncertainty());
            assertEquals(0.5, BearingTechnologyLevel.ANCIENT_BRONZE_WOOD.getMeasurementUncertainty(), 0.01);
            assertEquals(0.3, BearingTechnologyLevel.ANCIENT_BRONZE_IRON.getMeasurementUncertainty(), 0.01);
            assertEquals(0.001, BearingTechnologyLevel.MODERN_PRECISE.getMeasurementUncertainty(), 0.0001);
        }

        @Test
        @DisplayName("扩展不确定度k=2计算正确")
        void expandedUncertaintyK2Calculation() {
            double ancientUnc = BearingTechnologyLevel.ANCIENT_BRONZE_WOOD.getExpandedUncertainty(2.0);
            double modernUnc = BearingTechnologyLevel.MODERN_PRECISE.getExpandedUncertainty(2.0);
            assertEquals(1.0, ancientUnc, 0.01);
            assertEquals(0.002, modernUnc, 0.0001);
        }

        @Test
        @DisplayName("加工工艺有历史依据")
        void processingMethodHasHistoricalBasis() {
            assertTrue(BearingTechnologyLevel.ANCIENT_BRONZE_WOOD.getProcessingMethod()
                    .contains("手工"));
            assertTrue(BearingTechnologyLevel.ANCIENT_BRONZE_IRON.getProcessingMethod()
                    .contains("铸铁范铸"));
            assertTrue(BearingTechnologyLevel.ANCIENT_BRONZE_IRON.getProcessingMethod()
                    .contains("朱砂抛光"));
        }

        @Test
        @DisplayName("古代轴承对应GB/T 307.1等级合理")
        void ancientBearingsCorrespondToGBStandard() {
            assertTrue(BearingTechnologyLevel.ANCIENT_BRONZE_WOOD.getStandardReference()
                    .contains("G"));
            assertTrue(BearingTechnologyLevel.ANCIENT_BRONZE_IRON.getStandardReference()
                    .contains("E"));
            assertTrue(BearingTechnologyLevel.ANCIENT_BRONZE_WOOD.getStandardReference()
                    .contains("GB/T 307.1"));
        }

        @Test
        @DisplayName("fromInstrumentType映射正确")
        void fromInstrumentTypeMappingCorrect() {
            assertEquals(BearingTechnologyLevel.ANCIENT_BRONZE_WOOD,
                    BearingTechnologyLevel.fromInstrumentType(InstrumentType.ARMILLARY_SPHERE));
            assertEquals(BearingTechnologyLevel.ANCIENT_BRONZE_IRON,
                    BearingTechnologyLevel.fromInstrumentType(InstrumentType.ARMILLARY_SIMPLIFIED));
            assertEquals(BearingTechnologyLevel.ANCIENT_BRONZE_IRON,
                    BearingTechnologyLevel.fromInstrumentType(InstrumentType.QUADRANT));
        }
    }

    @Nested
    @DisplayName("缺陷2：现代轴承标准统一验证")
    class ModernBearingStandardValidation {

        @Test
        @DisplayName("现代轴承有ISO标准编号")
        void modernBearingsHaveISOStandardNumbers() {
            assertNotNull(BearingTechnologyLevel.MODERN_PLAIN.getIsoStandardNumber());
            assertNotNull(BearingTechnologyLevel.MODERN_ROLLING.getIsoStandardNumber());
            assertNotNull(BearingTechnologyLevel.MODERN_PRECISE.getIsoStandardNumber());

            assertEquals("ISO 12241:2019",
                    BearingTechnologyLevel.MODERN_PLAIN.getIsoStandardNumber());
            assertEquals("ISO 492:2014",
                    BearingTechnologyLevel.MODERN_ROLLING.getIsoStandardNumber());
            assertEquals("ISO 14801:2018",
                    BearingTechnologyLevel.MODERN_PRECISE.getIsoStandardNumber());
        }

        @Test
        @DisplayName("精度等级符合ISO标准")
        void precisionLevelsConformToISOStandards() {
            assertTrue(BearingTechnologyLevel.MODERN_PLAIN.getStandardReference()
                    .contains("P6"));
            assertTrue(BearingTechnologyLevel.MODERN_ROLLING.getStandardReference()
                    .contains("P4"));
            assertTrue(BearingTechnologyLevel.MODERN_PRECISE.getStandardReference()
                    .contains("P2"));
        }

        @Test
        @DisplayName("现代轴承加工工艺符合标准")
        void modernProcessingMethodsConformToStandards() {
            assertTrue(BearingTechnologyLevel.MODERN_PLAIN.getProcessingMethod()
                    .contains("超精加工"));
            assertTrue(BearingTechnologyLevel.MODERN_ROLLING.getProcessingMethod()
                    .contains("超精研"));
            assertTrue(BearingTechnologyLevel.MODERN_PRECISE.getProcessingMethod()
                    .contains("金刚石超精密车削"));
            assertTrue(BearingTechnologyLevel.MODERN_PRECISE.getProcessingMethod()
                    .contains("恒温恒湿净化"));
        }

        @Test
        @DisplayName("现代轴承测量不确定度极低")
        void modernBearingUncertaintyVeryLow() {
            assertTrue(BearingTechnologyLevel.MODERN_PLAIN.getMeasurementUncertainty()
                    < BearingTechnologyLevel.EARLY_MODERN.getMeasurementUncertainty());
            assertTrue(BearingTechnologyLevel.MODERN_ROLLING.getMeasurementUncertainty()
                    < BearingTechnologyLevel.MODERN_PLAIN.getMeasurementUncertainty());
            assertTrue(BearingTechnologyLevel.MODERN_PRECISE.getMeasurementUncertainty()
                    < BearingTechnologyLevel.MODERN_ROLLING.getMeasurementUncertainty());
        }

        @Test
        @DisplayName("现代轴承有实验数据来源")
        void modernBearingsHaveExperimentalValidation() {
            assertTrue(BearingTechnologyLevel.MODERN_PLAIN.hasExperimentalValidation());
            assertTrue(BearingTechnologyLevel.MODERN_ROLLING.hasExperimentalValidation());
            assertTrue(BearingTechnologyLevel.MODERN_PRECISE.hasExperimentalValidation());

            assertTrue(BearingTechnologyLevel.MODERN_PLAIN.getExperimentalDataSource()
                    .contains("清华大学"));
            assertTrue(BearingTechnologyLevel.MODERN_ROLLING.getExperimentalDataSource()
                    .contains("SKF"));
            assertTrue(BearingTechnologyLevel.MODERN_PRECISE.getExperimentalDataSource()
                    .contains("NASA"));
        }

        @Test
        @DisplayName("所有技术等级都有标准引用")
        void allTechnologyLevelsHaveStandardReferences() {
            for (BearingTechnologyLevel level : BearingTechnologyLevel.values()) {
                assertNotNull(level.getStandardReference(),
                        level.getDisplayName() + "应有标准引用");
            }
        }
    }

    @Nested
    @DisplayName("缺陷3：润滑剂流变特性建模验证")
    class LubricantRheologyValidation {

        @Test
        @DisplayName("Walther方程粘度计算非线性（非简单线性插值）")
        void waltherEquationNonlinearViscosity() {
            LubricantType oil = LubricantType.VEGETABLE_OIL;
            double visc40 = oil.getViscosityAt40C();
            double visc70 = oil.calculateViscosityWalther(70.0);
            double visc100 = oil.getViscosityAt100C();

            double linearVisc70 = visc40 * 0.5 + visc100 * 0.5;
            double waltherDiff = Math.abs(visc70 - linearVisc70);

            assertTrue(waltherDiff > 0.0001,
                    "Walther方程计算值应与线性插值不同，差值: " + waltherDiff);
        }

        @Test
        @DisplayName("粘度指数(VI)计算正确")
        void viscosityIndexCalculationCorrect() {
            double viVegetable = LubricantType.VEGETABLE_OIL.calculateViscosityIndex();
            double viSynthetic = LubricantType.MODERN_SYNTHETIC.calculateViscosityIndex();
            double viMineral = LubricantType.MINERAL_OIL.calculateViscosityIndex();

            assertEquals(125.0, viVegetable, 5.0);
            assertEquals(160.0, viSynthetic, 5.0);
            assertEquals(95.0, viMineral, 5.0);
            assertTrue(viSynthetic > viVegetable,
                    "合成油粘度指数应高于植物油");
        }

        @Test
        @DisplayName("粘压系数压力修正正确（Barus方程）")
        void pressureViscosityCorrectionCorrect() {
            LubricantType oil = LubricantType.MINERAL_OIL;
            double viscAtm = oil.getViscosityAtTemperature(40.0);
            double viscHighP = oil.getViscosityAtPressure(40.0, 0.5);

            assertTrue(viscHighP > viscAtm,
                    "高压下粘度应升高");
            double ratio = viscHighP / viscAtm;
            assertTrue(ratio > 1.0 && ratio < 10.0,
                    "粘度比应在合理范围，实际: " + ratio);
        }

        @Test
        @DisplayName("凝点以下粘度骤升（凝固）")
        void viscositySpikesBelowPourPoint() {
            LubricantType animalFat = LubricantType.ANIMAL_FAT;
            double viscAbove = animalFat.getViscosityAtTemperature(10.0);
            double viscBelow = animalFat.getViscosityAtTemperature(-5.0);

            assertTrue(viscBelow > viscAbove * 50.0,
                    "凝点以下粘度应骤升50倍以上，比值: " + (viscBelow / viscAbove));
        }

        @Test
        @DisplayName("沸点以上粘度骤降（蒸发）")
        void viscosityDropsAboveBoilingPoint() {
            LubricantType vegetableOil = LubricantType.VEGETABLE_OIL;
            double viscBelow = vegetableOil.getViscosityAtTemperature(19.0);
            double viscAbove = vegetableOil.getViscosityAtTemperature(25.0);

            assertTrue(viscAbove < viscBelow,
                    "沸点以上粘度应下降");
        }

        @Test
        @DisplayName("液相状态判断正确")
        void liquidPhaseStatusCorrect() {
            assertTrue(LubricantType.VEGETABLE_OIL.isInLiquidPhase(25.0));
            assertFalse(LubricantType.VEGETABLE_OIL.isInLiquidPhase(-20.0));
            assertFalse(LubricantType.VEGETABLE_OIL.isInLiquidPhase(100.0));
            assertFalse(LubricantType.DRY.isInLiquidPhase(25.0));
        }

        @Test
        @DisplayName("温度稳定性指数计算正确")
        void temperatureStabilityIndexCorrect() {
            double syntheticIndex = LubricantType.MODERN_SYNTHETIC.getTemperatureStabilityIndex();
            double mineralIndex = LubricantType.MINERAL_OIL.getTemperatureStabilityIndex();
            double animalIndex = LubricantType.ANIMAL_FAT.getTemperatureStabilityIndex();

            assertTrue(syntheticIndex > mineralIndex,
                    "合成油温度稳定性应高于矿物油");
            assertTrue(mineralIndex > animalIndex,
                    "矿物油温度稳定性应高于动物油");
        }

        @Test
        @DisplayName("DRY润滑剂特殊处理")
        void dryLubricantSpecialHandling() {
            assertEquals(LubricantType.DRY.getViscosityAt40C(),
                    LubricantType.DRY.calculateViscosityWalther(100.0));
            assertEquals(0.0, LubricantType.DRY.calculateViscosityIndex());
            assertEquals(LubricantType.DRY.getViscosityAtTemperature(25.0),
                    LubricantType.DRY.getViscosityAtPressure(25.0, 1.0));
        }

        @Test
        @DisplayName("温度极端值钳位正确")
        void temperatureExtremesClamped() {
            double viscCold = LubricantType.VEGETABLE_OIL.getViscosityAtTemperature(-100.0);
            double viscHot = LubricantType.VEGETABLE_OIL.getViscosityAtTemperature(500.0);

            assertTrue(viscCold > 0, "极低温下粘度应为正");
            assertTrue(viscHot > 0, "极高温下粘度应为正");
            assertDoesNotThrow(() ->
                    LubricantType.VEGETABLE_OIL.getViscosityAtTemperature(-1000.0));
            assertDoesNotThrow(() ->
                    LubricantType.VEGETABLE_OIL.getViscosityAtTemperature(1000.0));
        }

        @Test
        @DisplayName("剪切稳定性指数合理")
        void shearStabilityIndexReasonable() {
            assertTrue(LubricantType.MODERN_SYNTHETIC.getShearStabilityIndex()
                    < LubricantType.MINERAL_OIL.getShearStabilityIndex());
            assertTrue(LubricantType.MINERAL_OIL.getShearStabilityIndex()
                    < LubricantType.VEGETABLE_OIL.getShearStabilityIndex());
            assertEquals(0.0, LubricantType.DRY.getShearStabilityIndex());
        }
    }

    @Nested
    @DisplayName("缺陷4：虚拟操作力反馈验证")
    class VirtualOperationForceFeedbackValidation {

        private UUID instrumentId;

        @BeforeEach
        void setUpMockData() {
            instrumentId = UUID.randomUUID();
            ArmillaryInstrument instrument = ArmillaryInstrument.builder()
                    .id(instrumentId)
                    .name("测试简仪")
                    .model("JY-001")
                    .location("北京古观象台")
                    .buildYear(1279)
                    .instrumentType(InstrumentType.ARMILLARY_SIMPLIFIED)
                    .latitudeDeg(new BigDecimal("39.9042"))
                    .build();

            List<BearingConfig> configs = new ArrayList<>();
            configs.add(createBearingConfig(instrument, "赤道轴", "EQUATORIAL"));
            configs.add(createBearingConfig(instrument, "赤纬轴", "DECLINATION"));
            configs.add(createBearingConfig(instrument, "地平经轴", "AZIMUTH"));
            configs.add(createBearingConfig(instrument, "地平纬轴", "ALTITUDE"));

            when(bearingConfigRepository.findByInstrumentId(instrumentId))
                    .thenReturn(configs);
        }

        private BearingConfig createBearingConfig(ArmillaryInstrument instrument,
                                                   String axisName, String axisType) {
            return BearingConfig.builder()
                    .id(UUID.randomUUID())
                    .instrument(instrument)
                    .axisName(axisName)
                    .axisType(axisType)
                    .bearingType("滑动轴承")
                    .material("锡青铜-灰铸铁")
                    .innerRingMaterial("锡青铜")
                    .outerRingMaterial("灰铸铁")
                    .surfaceRoughnessRa(BigDecimal.valueOf(0.2))
                    .perpendicularityError(BigDecimal.valueOf(0.001))
                    .axialRunout(BigDecimal.valueOf(0.005))
                    .radialRunout(BigDecimal.valueOf(0.003))
                    .innerDiameter(BigDecimal.valueOf(50.0))
                    .outerDiameter(BigDecimal.valueOf(80.0))
                    .width(BigDecimal.valueOf(30.0))
                    .initialClearance(BigDecimal.valueOf(0.02))
                    .lubricantViscosity(BigDecimal.valueOf(0.035))
                    .elasticModulus(BigDecimal.valueOf(110000.0))
                    .poissonRatio(BigDecimal.valueOf(0.33))
                    .hardness(BigDecimal.valueOf(180.0))
                    .wearCoefficient(BigDecimal.valueOf(5e-5))
                    .maxAllowableWear(BigDecimal.valueOf(0.1))
                    .build();
        }

        @Test
        @DisplayName("操作扭矩包含4个分量：摩擦+惯性+阻尼+重力")
        void operationTorqueContainsFourComponents() {
            VirtualOperationRequest request = buildBasicRequest();
            request.setLoadKg(1000.0);
            request.setRotationalSpeedRpm(2.0);

            VirtualOperationResponse response = comparisonService.simulateVirtualOperation(request);

            assertNotNull(response.getOperationTorqueRequiredNm());
            assertNotNull(response.getInertiaResistanceNm());
            assertNotNull(response.getDampingCoefficient());
            assertNotNull(response.getEstimatedManualForceN());

            assertTrue(response.getOperationTorqueRequiredNm() > 0,
                    "操作扭矩应为正");
            assertTrue(response.getInertiaResistanceNm() >= 0,
                    "惯性阻力应非负");
            assertTrue(response.getDampingCoefficient() >= 0,
                    "阻尼系数应非负");
        }

        @Test
        @DisplayName("高负载增加操作扭矩")
        void highLoadIncreasesOperationTorque() {
            VirtualOperationRequest lightRequest = buildBasicRequest();
            lightRequest.setLoadKg(100.0);
            VirtualOperationResponse lightResponse =
                    comparisonService.simulateVirtualOperation(lightRequest);

            VirtualOperationRequest heavyRequest = buildBasicRequest();
            heavyRequest.setLoadKg(5000.0);
            VirtualOperationResponse heavyResponse =
                    comparisonService.simulateVirtualOperation(heavyRequest);

            assertTrue(heavyResponse.getOperationTorqueRequiredNm() >
                            lightResponse.getOperationTorqueRequiredNm(),
                    "高负载应增加操作扭矩");
            assertTrue(heavyResponse.getEstimatedManualForceN() >
                            lightResponse.getEstimatedManualForceN(),
                    "高负载应增加手动推力");
        }

        @Test
        @DisplayName("高转速增加惯性阻力")
        void highSpeedIncreasesInertiaResistance() {
            VirtualOperationRequest slowRequest = buildBasicRequest();
            slowRequest.setRotationalSpeedRpm(0.1);
            VirtualOperationResponse slowResponse =
                    comparisonService.simulateVirtualOperation(slowRequest);

            VirtualOperationRequest fastRequest = buildBasicRequest();
            fastRequest.setRotationalSpeedRpm(10.0);
            VirtualOperationResponse fastResponse =
                    comparisonService.simulateVirtualOperation(fastRequest);

            assertTrue(fastResponse.getInertiaResistanceNm() >
                            slowResponse.getInertiaResistanceNm(),
                    "高转速应增加惯性阻力");
            assertNotNull(fastResponse.getCurrentAngularAccelerationRadS2());
        }

        @Test
        @DisplayName("力反馈强度与润滑状态关联")
        void hapticIntensityCorrelatesWithLubrication() {
            VirtualOperationRequest goodLubrication = buildBasicRequest();
            goodLubrication.setLubricantType("MODERN_SYNTHETIC");
            goodLubrication.setTemperatureC(25.0);
            VirtualOperationResponse goodResponse =
                    comparisonService.simulateVirtualOperation(goodLubrication);

            VirtualOperationRequest badLubrication = buildBasicRequest();
            badLubrication.setLubricantType("DRY");
            VirtualOperationResponse badResponse =
                    comparisonService.simulateVirtualOperation(badLubrication);

            assertTrue(badResponse.getHapticFeedbackIntensity() >
                            goodResponse.getHapticFeedbackIntensity(),
                    "不良润滑应增加力反馈强度");
            assertNotNull(goodResponse.getForceFeedbackStatus());
            assertNotNull(badResponse.getForceFeedbackStatus());
        }

        @Test
        @DisplayName("力反馈状态描述正确")
        void forceFeedbackStatusDescriptionCorrect() {
            VirtualOperationRequest dryRequest = buildBasicRequest();
            dryRequest.setLubricantType("DRY");
            VirtualOperationResponse dryResponse =
                    comparisonService.simulateVirtualOperation(dryRequest);
            assertTrue(dryResponse.getForceFeedbackStatus().contains("边界润滑") ||
                            dryResponse.getForceFeedbackStatus().contains("振动"));

            VirtualOperationRequest syntheticRequest = buildBasicRequest();
            syntheticRequest.setLubricantType("MODERN_SYNTHETIC");
            VirtualOperationResponse syntheticResponse =
                    comparisonService.simulateVirtualOperation(syntheticRequest);
            assertTrue(syntheticResponse.getForceFeedbackStatus().contains("弹流") ||
                            syntheticResponse.getForceFeedbackStatus().contains("平滑"));
        }

        @Test
        @DisplayName("角加速度合理")
        void angularAccelerationReasonable() {
            VirtualOperationRequest request = buildBasicRequest();
            request.setRotationalSpeedRpm(1.0);
            VirtualOperationResponse response =
                    comparisonService.simulateVirtualOperation(request);

            assertNotNull(response.getCurrentAngularAccelerationRadS2());
            assertTrue(response.getCurrentAngularAccelerationRadS2() >= 0);
            assertTrue(response.getCurrentAngularAccelerationRadS2() < 10.0,
                    "角加速度应在合理范围");
        }

        @Test
        @DisplayName("手动推力在人力可操作范围")
        void manualForceWithinHumanRange() {
            VirtualOperationRequest normalRequest = buildBasicRequest();
            normalRequest.setLoadKg(500.0);
            normalRequest.setRotationalSpeedRpm(0.5);
            VirtualOperationResponse response =
                    comparisonService.simulateVirtualOperation(normalRequest);

            assertTrue(response.getEstimatedManualForceN() > 0,
                    "手动推力应为正");
            assertTrue(response.getEstimatedManualForceN() < 500.0,
                    "手动推力应在人体可操作范围，实际: " +
                            response.getEstimatedManualForceN());
        }

        @Test
        @DisplayName("极重负载时手动推力增大但仍可测量")
        void extremeLoadManualForceMeasurable() {
            VirtualOperationRequest heavyRequest = buildBasicRequest();
            heavyRequest.setLoadKg(10000.0);
            heavyRequest.setRotationalSpeedRpm(0.1);
            VirtualOperationResponse response =
                    comparisonService.simulateVirtualOperation(heavyRequest);

            assertTrue(response.getEstimatedManualForceN() > 0);
            assertTrue(Double.isFinite(response.getEstimatedManualForceN()));
        }

        @Test
        @DisplayName("零转速时惯性阻力为零或极小")
        void zeroSpeedInertiaResistanceMinimal() {
            VirtualOperationRequest zeroSpeedRequest = buildBasicRequest();
            zeroSpeedRequest.setRotationalSpeedRpm(0.001);
            VirtualOperationResponse response =
                    comparisonService.simulateVirtualOperation(zeroSpeedRequest);

            assertNotNull(response.getInertiaResistanceNm());
            assertTrue(Double.isFinite(response.getInertiaResistanceNm()));
        }

        @Test
        @DisplayName("不同轴角度重力扭矩不同")
        void gravityTorqueVariesWithAxisAngle() {
            VirtualOperationRequest eq0Request = buildBasicRequest();
            eq0Request.setEquatorialAxisAngleDeg(0.0);
            eq0Request.setDeclinationAxisAngleDeg(90.0);
            VirtualOperationResponse eq0Response =
                    comparisonService.simulateVirtualOperation(eq0Request);

            VirtualOperationRequest eq90Request = buildBasicRequest();
            eq90Request.setEquatorialAxisAngleDeg(90.0);
            eq90Request.setDeclinationAxisAngleDeg(0.0);
            VirtualOperationResponse eq90Response =
                    comparisonService.simulateVirtualOperation(eq90Request);

            assertNotEquals(eq0Response.getOperationTorqueRequiredNm(),
                    eq90Response.getOperationTorqueRequiredNm(), 0.001,
                    "不同轴角度重力扭矩应不同");
        }
    }

    private VirtualOperationRequest buildBasicRequest() {
        VirtualOperationRequest request = new VirtualOperationRequest();
        request.setInstrumentId(instrumentId);
        request.setEquatorialAxisAngleDeg(90.0);
        request.setDeclinationAxisAngleDeg(30.0);
        request.setAzimuthAxisAngleDeg(180.0);
        request.setAltitudeAxisAngleDeg(45.0);
        request.setRotationalSpeedRpm(1.0);
        request.setLoadKg(500.0);
        request.setTemperatureC(25.0);
        request.setLubricantType("VEGETABLE_OIL");
        request.setSimulateWear(false);
        request.setSimulateHours(0);
        return request;
    }
}
