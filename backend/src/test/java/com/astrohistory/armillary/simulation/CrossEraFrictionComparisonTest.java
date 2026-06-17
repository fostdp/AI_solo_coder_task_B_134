package com.astrohistory.armillary.simulation;

import com.astrohistory.armillary.dto.SensorDataDTO;
import com.astrohistory.armillary.entity.ArmillaryInstrument;
import com.astrohistory.armillary.entity.BearingConfig;
import com.astrohistory.armillary.enums.BearingTechnologyLevel;
import com.astrohistory.armillary.enums.LubricantType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("跨时代摩擦系数对比验证测试")
class CrossEraFrictionComparisonTest {

    private BearingFrictionModel frictionModel;
    private BearingConfig testConfig;
    private SensorDataDTO baseSensorData;

    @BeforeEach
    void setUp() {
        frictionModel = new BearingFrictionModel();
        testConfig = createTestConfig();
        baseSensorData = createBaseSensorData();
    }

    @Nested
    @DisplayName("正常用例")
    class NormalCases {

        @Test
        @DisplayName("摩擦系数从古代到现代单调递减验证")
        void frictionCoefficientMonotonicallyDecreases() {
            BearingTechnologyLevel[] levels = BearingTechnologyLevel.values();
            double previousFriction = Double.MAX_VALUE;

            for (BearingTechnologyLevel level : levels) {
                BearingFrictionModel.FrictionSimulationResult result =
                        frictionModel.simulateWithTechnologyLevel(
                                testConfig, baseSensorData, 0.0, LocalDateTime.now(), level);
                double currentFriction = result.getFrictionCoefficient();

                assertTrue(currentFriction <= previousFriction + 1e-10,
                        level.getDisplayName() + "摩擦系数应 <= 前一时代: " +
                                currentFriction + " <= " + previousFriction);
                previousFriction = currentFriction;
            }
        }

        @Test
        @DisplayName("古代青铜-木质 > 古代青铜-铸铁 摩擦系数验证")
        void ancientBronzeWoodFrictionHigherThanBronzeIron() {
            BearingFrictionModel.FrictionSimulationResult woodResult =
                    frictionModel.simulateWithTechnologyLevel(
                            testConfig, baseSensorData, 0.0, LocalDateTime.now(),
                            BearingTechnologyLevel.ANCIENT_BRONZE_WOOD);
            BearingFrictionModel.FrictionSimulationResult ironResult =
                    frictionModel.simulateWithTechnologyLevel(
                            testConfig, baseSensorData, 0.0, LocalDateTime.now(),
                            BearingTechnologyLevel.ANCIENT_BRONZE_IRON);

            assertTrue(woodResult.getFrictionCoefficient() > ironResult.getFrictionCoefficient(),
                    "古代青铜-木质摩擦应高于青铜-铸铁");
            assertEquals(0.8, BearingTechnologyLevel.ANCIENT_BRONZE_IRON.getFrictionCoefficientTypical(), 0.01);
        }

        @Test
        @DisplayName("现代精密轴承摩擦系数接近最小值验证")
        void modernPreciseFrictionNearMinimum() {
            BearingFrictionModel.FrictionSimulationResult result =
                    frictionModel.simulateWithTechnologyLevel(
                            testConfig, baseSensorData, 0.0, LocalDateTime.now(),
                            BearingTechnologyLevel.MODERN_PRECISE);

            double minFriction = BearingTechnologyLevel.MODERN_PRECISE.getFrictionCoefficientMin();
            double actualFriction = result.getFrictionCoefficient();

            assertTrue(actualFriction >= minFriction,
                    "现代精密轴承摩擦系数应 >= 最小值: " + actualFriction + " >= " + minFriction);
            assertTrue(actualFriction < minFriction * 2,
                    "现代精密轴承摩擦系数应接近最小值");
        }

        @Test
        @DisplayName("compareTechnologyLevels返回全部6个时代数据")
        void compareTechnologyLevelsReturnsAllSixEras() {
            Map<BearingTechnologyLevel, BearingFrictionModel.FrictionSimulationResult> results =
                    frictionModel.compareTechnologyLevels(
                            testConfig, baseSensorData, 0.0, LocalDateTime.now());

            assertEquals(6, results.size(), "应返回6个技术等级的数据");
            for (BearingTechnologyLevel level : BearingTechnologyLevel.values()) {
                assertTrue(results.containsKey(level),
                        "应包含 " + level.getDisplayName());
                assertNotNull(results.get(level),
                        level.getDisplayName() + "数据不应为null");
            }
        }

        @Test
        @DisplayName("磨损率跨时代递减验证")
        void wearRateDecreasesAcrossEras() {
            Map<BearingTechnologyLevel, BearingFrictionModel.FrictionSimulationResult> results =
                    frictionModel.compareTechnologyLevels(
                            testConfig, baseSensorData, 0.0, LocalDateTime.now());

            double previousWear = Double.MAX_VALUE;
            for (BearingTechnologyLevel level : BearingTechnologyLevel.values()) {
                double currentWear = results.get(level).getWearRate();
                assertTrue(currentWear <= previousWear + 1e-15,
                        level.getDisplayName() + "磨损率应递减");
                previousWear = currentWear;
            }
        }

        @Test
        @DisplayName("lambda比率现代优于古代验证")
        void lambdaRatioModernBetterThanAncient() {
            BearingFrictionModel.FrictionSimulationResult ancientResult =
                    frictionModel.simulateWithTechnologyLevel(
                            testConfig, baseSensorData, 0.0, LocalDateTime.now(),
                            BearingTechnologyLevel.ANCIENT_BRONZE_WOOD);
            BearingFrictionModel.FrictionSimulationResult modernResult =
                    frictionModel.simulateWithTechnologyLevel(
                            testConfig, baseSensorData, 0.0, LocalDateTime.now(),
                            BearingTechnologyLevel.MODERN_PRECISE);

            assertTrue(modernResult.getLambdaRatio() > ancientResult.getLambdaRatio(),
                    "现代轴承lambda比率应更高");
        }

        @Test
        @DisplayName("润滑状态名称正确返回验证")
        void lubricationRegimeNameCorrectlyReturns() {
            assertEquals("边界润滑", frictionModel.getLubricationRegimeName(0.3));
            assertEquals("混合润滑", frictionModel.getLubricationRegimeName(1.0));
            assertEquals("弹流润滑", frictionModel.getLubricationRegimeName(2.0));
            assertEquals("全膜流体润滑", frictionModel.getLubricationRegimeName(5.0));
        }

        @Test
        @DisplayName("古代到现代摩擦系数提升>100倍验证")
        void frictionImprovementOver100xFromAncientToModern() {
            BearingFrictionModel.FrictionSimulationResult ancientResult =
                    frictionModel.simulateWithTechnologyLevel(
                            testConfig, baseSensorData, 0.0, LocalDateTime.now(),
                            BearingTechnologyLevel.ANCIENT_BRONZE_WOOD);
            BearingFrictionModel.FrictionSimulationResult modernResult =
                    frictionModel.simulateWithTechnologyLevel(
                            testConfig, baseSensorData, 0.0, LocalDateTime.now(),
                            BearingTechnologyLevel.MODERN_PRECISE);

            double improvementRatio = ancientResult.getFrictionCoefficient() /
                    Math.max(modernResult.getFrictionCoefficient(), 1e-10);
            assertTrue(improvementRatio >= 100.0,
                    "摩擦系数提升比应 >= 100倍，实际: " + improvementRatio);
        }

        @Test
        @DisplayName("跳动精度跨时代提升验证")
        void runoutPrecisionImprovesAcrossEras() {
            double previousRunout = Double.MAX_VALUE;
            for (BearingTechnologyLevel level : BearingTechnologyLevel.values()) {
                double currentRunout = level.getTypicalRunoutMicrometers();
                assertTrue(currentRunout <= previousRunout,
                        level.getDisplayName() + "跳动精度应递增");
                previousRunout = currentRunout;
            }
            assertEquals(6.3, BearingTechnologyLevel.ANCIENT_BRONZE_WOOD.getTypicalRunoutMicrometers(), 0.01);
            assertEquals(0.005, BearingTechnologyLevel.MODERN_PRECISE.getTypicalRunoutMicrometers(), 0.0001);
        }
    }

    @Nested
    @DisplayName("边界用例")
    class BoundaryCases {

        @Test
        @DisplayName("累积磨损为0时跨时代对比")
        void zeroAccumulatedWearComparison() {
            for (BearingTechnologyLevel level : BearingTechnologyLevel.values()) {
                BearingFrictionModel.FrictionSimulationResult result =
                        frictionModel.simulateWithTechnologyLevel(
                                testConfig, baseSensorData, 0.0, LocalDateTime.now(), level);
                assertEquals(0.0, result.getTotalWearDepth(), 1e-10,
                        level.getDisplayName() + "零磨损时总磨损应为0");
            }
        }

        @Test
        @DisplayName("接近寿命末期(0.09)跨时代对比")
        void nearMaxAllowableWearComparison() {
            double accumulatedWear = 0.09;
            Map<BearingTechnologyLevel, BearingFrictionModel.FrictionSimulationResult> results =
                    frictionModel.compareTechnologyLevels(
                            testConfig, baseSensorData, accumulatedWear, LocalDateTime.now());

            for (Map.Entry<BearingTechnologyLevel, BearingFrictionModel.FrictionSimulationResult> entry : results.entrySet()) {
                assertTrue(entry.getValue().getTotalWearDepth() > 0.09,
                        entry.getKey().getDisplayName() + "总磨损应 > 0.09");
            }
        }

        @Test
        @DisplayName("极低速(0.001rpm)跨时代对比")
        void nearStallSpeedComparison() {
            SensorDataDTO lowSpeedData = SensorDataDTO.builder()
                    .instrumentId(UUID.randomUUID())
                    .axisName("赤道轴")
                    .timestamp(LocalDateTime.now())
                    .rotationalSpeed(BigDecimal.valueOf(0.001))
                    .temperature(BigDecimal.valueOf(25.0))
                    .loadRadial(BigDecimal.valueOf(500.0))
                    .loadAxial(BigDecimal.valueOf(100.0))
                    .build();

            for (BearingTechnologyLevel level : BearingTechnologyLevel.values()) {
                BearingFrictionModel.FrictionSimulationResult result =
                        frictionModel.simulateWithTechnologyLevel(
                                testConfig, lowSpeedData, 0.0, LocalDateTime.now(), level);
                assertTrue(result.getFrictionCoefficient() >= 0,
                        level.getDisplayName() + "低速下摩擦系数不应为负");
            }
        }

        @Test
        @DisplayName("高温(100°C)跨时代对比")
        void highTemperature100CComparison() {
            SensorDataDTO highTempData = SensorDataDTO.builder()
                    .instrumentId(UUID.randomUUID())
                    .axisName("赤道轴")
                    .timestamp(LocalDateTime.now())
                    .rotationalSpeed(BigDecimal.valueOf(1.0))
                    .temperature(BigDecimal.valueOf(100.0))
                    .loadRadial(BigDecimal.valueOf(500.0))
                    .loadAxial(BigDecimal.valueOf(100.0))
                    .build();

            BearingFrictionModel.FrictionSimulationResult ancientResult =
                    frictionModel.simulateWithTechnologyLevel(
                            testConfig, highTempData, 0.0, LocalDateTime.now(),
                            BearingTechnologyLevel.ANCIENT_BRONZE_IRON);
            BearingFrictionModel.FrictionSimulationResult modernResult =
                    frictionModel.simulateWithTechnologyLevel(
                            testConfig, highTempData, 0.0, LocalDateTime.now(),
                            BearingTechnologyLevel.MODERN_PRECISE);

            assertTrue(ancientResult.getFrictionCoefficient() > modernResult.getFrictionCoefficient(),
                    "高温下古代摩擦仍高于现代");
        }

        @Test
        @DisplayName("低温(-20°C)跨时代对比")
        void lowTemperatureMinus20CComparison() {
            SensorDataDTO lowTempData = SensorDataDTO.builder()
                    .instrumentId(UUID.randomUUID())
                    .axisName("赤道轴")
                    .timestamp(LocalDateTime.now())
                    .rotationalSpeed(BigDecimal.valueOf(1.0))
                    .temperature(BigDecimal.valueOf(-20.0))
                    .loadRadial(BigDecimal.valueOf(500.0))
                    .loadAxial(BigDecimal.valueOf(100.0))
                    .build();

            for (BearingTechnologyLevel level : BearingTechnologyLevel.values()) {
                BearingFrictionModel.FrictionSimulationResult result =
                        frictionModel.simulateWithTechnologyLevel(
                                testConfig, lowTempData, 0.0, LocalDateTime.now(), level);
                assertTrue(Double.isFinite(result.getFrictionCoefficient()),
                        level.getDisplayName() + "低温下摩擦系数应为有限值");
            }
        }

        @Test
        @DisplayName("lambda比率边界值验证")
        void lambdaRatioBoundaryValues() {
            assertEquals("边界润滑", frictionModel.getLubricationRegimeName(0.499999));
            assertEquals("边界润滑", frictionModel.getLubricationRegimeName(0.5));
            assertEquals("混合润滑", frictionModel.getLubricationRegimeName(0.500001));
            assertEquals("混合润滑", frictionModel.getLubricationRegimeName(1.499999));
            assertEquals("弹流润滑", frictionModel.getLubricationRegimeName(1.5));
            assertEquals("弹流润滑", frictionModel.getLubricationRegimeName(2.999999));
            assertEquals("全膜流体润滑", frictionModel.getLubricationRegimeName(3.0));
            assertEquals("全膜流体润滑", frictionModel.getLubricationRegimeName(100.0));
        }

        @Test
        @DisplayName("极高负载跨时代对比")
        void extremelyHighLoadComparison() {
            SensorDataDTO highLoadData = SensorDataDTO.builder()
                    .instrumentId(UUID.randomUUID())
                    .axisName("赤道轴")
                    .timestamp(LocalDateTime.now())
                    .rotationalSpeed(BigDecimal.valueOf(1.0))
                    .temperature(BigDecimal.valueOf(25.0))
                    .loadRadial(BigDecimal.valueOf(50000.0))
                    .loadAxial(BigDecimal.valueOf(25000.0))
                    .build();

            Map<BearingTechnologyLevel, BearingFrictionModel.FrictionSimulationResult> results =
                    frictionModel.compareTechnologyLevels(
                            testConfig, highLoadData, 0.0, LocalDateTime.now());

            BearingFrictionModel.FrictionSimulationResult ancientResult =
                    results.get(BearingTechnologyLevel.ANCIENT_BRONZE_WOOD);
            BearingFrictionModel.FrictionSimulationResult modernResult =
                    results.get(BearingTechnologyLevel.MODERN_PRECISE);

            assertTrue(ancientResult.getContactPressure() > 0,
                    "高负载下古代轴承接触压力应为正");
            assertTrue(modernResult.getContactPressure() > 0,
                    "高负载下现代轴承接触压力应为正");
        }

        @Test
        @DisplayName("6个技术等级全部存在验证")
        void allSixTechnologyLevelsPresent() {
            BearingTechnologyLevel[] levels = BearingTechnologyLevel.values();
            assertEquals(6, levels.length, "应有6个技术等级");

            assertEquals("古代青铜-木质轴承", levels[0].getDisplayName());
            assertEquals("古代青铜-铸铁轴承", levels[1].getDisplayName());
            assertEquals("近代机械轴承", levels[2].getDisplayName());
            assertEquals("现代滑动轴承", levels[3].getDisplayName());
            assertEquals("现代滚动轴承", levels[4].getDisplayName());
            assertEquals("现代精密气浮轴承", levels[5].getDisplayName());
        }
    }

    @Nested
    @DisplayName("异常用例")
    class AbnormalCases {

        @Test
        @DisplayName("BearingConfig可选字段为null时跨时代对比")
        void nullOptionalFieldsInBearingConfig() {
            BearingConfig nullFieldsConfig = BearingConfig.builder()
                    .id(UUID.randomUUID())
                    .instrument(createTestInstrument())
                    .axisName("赤道轴")
                    .axisType("赤道")
                    .bearingType("滑动轴承")
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

            for (BearingTechnologyLevel level : BearingTechnologyLevel.values()) {
                BearingFrictionModel.FrictionSimulationResult result =
                        frictionModel.simulateWithTechnologyLevel(
                                nullFieldsConfig, baseSensorData, 0.0, LocalDateTime.now(), level);

                assertNotNull(result);
                assertTrue(Double.isFinite(result.getFrictionCoefficient()));
            }
        }

        @Test
        @DisplayName("SensorDataDTO温度为null时默认处理")
        void nullTemperatureInSensorData() {
            SensorDataDTO nullTempData = SensorDataDTO.builder()
                    .instrumentId(UUID.randomUUID())
                    .axisName("赤道轴")
                    .timestamp(LocalDateTime.now())
                    .rotationalSpeed(BigDecimal.valueOf(1.0))
                    .loadRadial(BigDecimal.valueOf(500.0))
                    .loadAxial(BigDecimal.valueOf(100.0))
                    .build();

            Map<BearingTechnologyLevel, BearingFrictionModel.FrictionSimulationResult> results =
                    frictionModel.compareTechnologyLevels(
                            testConfig, nullTempData, 0.0, LocalDateTime.now());

            for (BearingTechnologyLevel level : BearingTechnologyLevel.values()) {
                assertNotNull(results.get(level));
                assertTrue(Double.isFinite(results.get(level).getFrictionCoefficient()));
            }
        }

        @Test
        @DisplayName("零转速跨时代对比")
        void zeroRotationalSpeedComparison() {
            SensorDataDTO zeroSpeedData = SensorDataDTO.builder()
                    .instrumentId(UUID.randomUUID())
                    .axisName("赤道轴")
                    .timestamp(LocalDateTime.now())
                    .rotationalSpeed(BigDecimal.valueOf(0.0))
                    .temperature(BigDecimal.valueOf(25.0))
                    .loadRadial(BigDecimal.valueOf(500.0))
                    .loadAxial(BigDecimal.valueOf(100.0))
                    .build();

            for (BearingTechnologyLevel level : BearingTechnologyLevel.values()) {
                BearingFrictionModel.FrictionSimulationResult result =
                        frictionModel.simulateWithTechnologyLevel(
                                testConfig, zeroSpeedData, 0.0, LocalDateTime.now(), level);

                assertNotNull(result);
                assertTrue(Double.isFinite(result.getFrictionCoefficient()));
            }
        }

        @Test
        @DisplayName("负负载跨时代对比")
        void negativeLoadComparison() {
            SensorDataDTO negativeLoadData = SensorDataDTO.builder()
                    .instrumentId(UUID.randomUUID())
                    .axisName("赤道轴")
                    .timestamp(LocalDateTime.now())
                    .rotationalSpeed(BigDecimal.valueOf(1.0))
                    .temperature(BigDecimal.valueOf(25.0))
                    .loadRadial(BigDecimal.valueOf(-500.0))
                    .loadAxial(BigDecimal.valueOf(-100.0))
                    .build();

            for (BearingTechnologyLevel level : BearingTechnologyLevel.values()) {
                BearingFrictionModel.FrictionSimulationResult result =
                        frictionModel.simulateWithTechnologyLevel(
                                testConfig, negativeLoadData, 0.0, LocalDateTime.now(), level);

                assertNotNull(result);
                assertTrue(Double.isFinite(result.getContactPressure()));
            }
        }

        @Test
        @DisplayName("getLubricationRegimeName边界值NaN和Infinity处理")
        void lambdaRatioNaNAndInfinityHandling() {
            assertDoesNotThrow(() -> frictionModel.getLubricationRegimeName(Double.NaN));
            assertDoesNotThrow(() -> frictionModel.getLubricationRegimeName(Double.POSITIVE_INFINITY));
            assertDoesNotThrow(() -> frictionModel.getLubricationRegimeName(Double.NEGATIVE_INFINITY));
            assertNotNull(frictionModel.getLubricationRegimeName(Double.NaN));
            assertNotNull(frictionModel.getLubricationRegimeName(Double.POSITIVE_INFINITY));
        }

        @Test
        @DisplayName("fromInstrumentType所有InstrumentType映射验证")
        void fromInstrumentTypeAllMappings() {
            assertEquals(BearingTechnologyLevel.ANCIENT_BRONZE_WOOD,
                    BearingTechnologyLevel.fromInstrumentType(
                            com.astrohistory.armillary.enums.InstrumentType.ARMILLARY_SPHERE));
            assertEquals(BearingTechnologyLevel.ANCIENT_BRONZE_IRON,
                    BearingTechnologyLevel.fromInstrumentType(
                            com.astrohistory.armillary.enums.InstrumentType.ARMILLARY_SIMPLIFIED));
            assertEquals(BearingTechnologyLevel.ANCIENT_BRONZE_IRON,
                    BearingTechnologyLevel.fromInstrumentType(
                            com.astrohistory.armillary.enums.InstrumentType.QUADRANT));
            assertEquals(BearingTechnologyLevel.ANCIENT_BRONZE_IRON,
                    BearingTechnologyLevel.fromInstrumentType(
                            com.astrohistory.armillary.enums.InstrumentType.ARMILLARY_TRADITIONAL));
            assertEquals(BearingTechnologyLevel.MODERN_PRECISE,
                    BearingTechnologyLevel.fromInstrumentType(
                            com.astrohistory.armillary.enums.InstrumentType.MODERN_PRECISE));
        }
    }

    private BearingConfig createTestConfig() {
        ArmillaryInstrument instrument = createTestInstrument();
        return BearingConfig.builder()
                .id(UUID.randomUUID())
                .instrument(instrument)
                .axisName("赤道轴")
                .axisType("赤道")
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

    private ArmillaryInstrument createTestInstrument() {
        return ArmillaryInstrument.builder()
                .id(UUID.randomUUID())
                .name("测试简仪")
                .model("TEST-01")
                .location("北京")
                .buildYear(1279)
                .build();
    }

    private SensorDataDTO createBaseSensorData() {
        return SensorDataDTO.builder()
                .instrumentId(UUID.randomUUID())
                .axisName("赤道轴")
                .timestamp(LocalDateTime.now())
                .rotationalSpeed(BigDecimal.valueOf(1.0))
                .frictionTorque(BigDecimal.valueOf(100.0))
                .wearDepth(BigDecimal.valueOf(0.001))
                .temperature(BigDecimal.valueOf(25.0))
                .loadRadial(BigDecimal.valueOf(500.0))
                .loadAxial(BigDecimal.valueOf(100.0))
                .build();
    }
}
