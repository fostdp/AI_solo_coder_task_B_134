package com.astrohistory.armillary.simulation;

import com.astrohistory.armillary.dto.SensorDataDTO;
import com.astrohistory.armillary.entity.ArmillaryInstrument;
import com.astrohistory.armillary.entity.BearingConfig;
import com.astrohistory.armillary.enums.BearingTechnologyLevel;
import com.astrohistory.armillary.enums.InstrumentType;
import com.astrohistory.armillary.enums.LubricantType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("仪器对比磨损速率验证测试")
class InstrumentComparisonTest {

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
        @DisplayName("5种仪器类型对比：磨损速率与轴数反比验证")
        void wearRateInverselyProportionalToAxisCount() {
            Map<InstrumentType, Double> wearRates = new java.util.LinkedHashMap<>();
            for (InstrumentType type : InstrumentType.values()) {
                Map<String, Object> chars = frictionModel.getInstrumentTypeBearingCharacteristics(type);
                BearingTechnologyLevel techLevel = (BearingTechnologyLevel) chars.get("technologyLevel");
                BearingFrictionModel.FrictionSimulationResult result =
                        frictionModel.simulateWithTechnologyLevel(
                                testConfig, baseSensorData, 0.0, LocalDateTime.now(), techLevel);
                wearRates.put(type, result.getWearRate());
            }

            double quadrantWear = wearRates.get(InstrumentType.QUADRANT);
            double simplifiedWear = wearRates.get(InstrumentType.ARMILLARY_SIMPLIFIED);
            double sphereWear = wearRates.get(InstrumentType.ARMILLARY_SPHERE);

            assertTrue(quadrantWear > simplifiedWear,
                    "象限仪(1轴)磨损应高于简仪(4轴)");
            assertTrue(simplifiedWear > sphereWear,
                    "简仪(4轴)磨损应高于浑仪(7轴)");
        }

        @Test
        @DisplayName("简仪(4轴) vs 浑仪(7轴)：简仪单位轴磨损更低")
        void simplifiedHasLowerPerAxisWearThanSphere() {
            Map<String, Object> simplifiedChars = frictionModel.getInstrumentTypeBearingCharacteristics(
                    InstrumentType.ARMILLARY_SIMPLIFIED);
            Map<String, Object> sphereChars = frictionModel.getInstrumentTypeBearingCharacteristics(
                    InstrumentType.ARMILLARY_SPHERE);

            BearingTechnologyLevel simplifiedTech = (BearingTechnologyLevel) simplifiedChars.get("technologyLevel");
            BearingTechnologyLevel sphereTech = (BearingTechnologyLevel) sphereChars.get("technologyLevel");

            BearingFrictionModel.FrictionSimulationResult simplifiedResult =
                    frictionModel.simulateWithTechnologyLevel(
                            testConfig, baseSensorData, 0.0, LocalDateTime.now(), simplifiedTech);
            BearingFrictionModel.FrictionSimulationResult sphereResult =
                    frictionModel.simulateWithTechnologyLevel(
                            testConfig, baseSensorData, 0.0, LocalDateTime.now(), sphereTech);

            double simplifiedPerAxis = simplifiedResult.getWearRate() / 4.0;
            double spherePerAxis = sphereResult.getWearRate() / 7.0;

            assertTrue(simplifiedPerAxis < spherePerAxis * 1.5,
                    "简仪单位轴磨损应接近或低于浑仪");
        }

        @Test
        @DisplayName("象限仪(1轴)磨损速率最高验证")
        void quadrantHasHighestWearRate() {
            double maxWear = 0.0;
            InstrumentType maxType = null;

            for (InstrumentType type : InstrumentType.values()) {
                Map<String, Object> chars = frictionModel.getInstrumentTypeBearingCharacteristics(type);
                BearingTechnologyLevel techLevel = (BearingTechnologyLevel) chars.get("technologyLevel");
                BearingFrictionModel.FrictionSimulationResult result =
                        frictionModel.simulateWithTechnologyLevel(
                                testConfig, baseSensorData, 0.0, LocalDateTime.now(), techLevel);
                if (result.getWearRate() > maxWear) {
                    maxWear = result.getWearRate();
                    maxType = type;
                }
            }

            assertEquals(InstrumentType.QUADRANT, maxType,
                    "象限仪应具有最高磨损速率");
        }

        @Test
        @DisplayName("getInstrumentTypeBearingCharacteristics返回正确元数据")
        void getCharacteristicsReturnsCorrectMetadata() {
            Map<String, Object> chars = frictionModel.getInstrumentTypeBearingCharacteristics(
                    InstrumentType.ARMILLARY_SIMPLIFIED);

            assertEquals(InstrumentType.ARMILLARY_SIMPLIFIED, chars.get("instrumentType"));
            assertEquals("简仪", chars.get("displayName"));
            assertEquals(1279, chars.get("originYear"));
            assertEquals(4, chars.get("axisCount"));
            assertEquals(BearingTechnologyLevel.ANCIENT_BRONZE_IRON, chars.get("technologyLevel"));
            assertEquals(LubricantType.VEGETABLE_OIL, chars.get("typicalLubricant"));
            assertTrue(chars.containsKey("keyFeatures"));
            assertTrue(chars.get("keyFeatures") instanceof List);
            assertFalse(((List<?>) chars.get("keyFeatures")).isEmpty());
        }

        @Test
        @DisplayName("不同技术等级产生不同模拟结果")
        void differentTechLevelsProduceDifferentResults() {
            BearingFrictionModel.FrictionSimulationResult ancientResult =
                    frictionModel.simulateWithTechnologyLevel(
                            testConfig, baseSensorData, 0.0, LocalDateTime.now(),
                            BearingTechnologyLevel.ANCIENT_BRONZE_WOOD);
            BearingFrictionModel.FrictionSimulationResult modernResult =
                    frictionModel.simulateWithTechnologyLevel(
                            testConfig, baseSensorData, 0.0, LocalDateTime.now(),
                            BearingTechnologyLevel.MODERN_PRECISE);

            assertNotEquals(ancientResult.getFrictionCoefficient(), modernResult.getFrictionCoefficient());
            assertNotEquals(ancientResult.getWearRate(), modernResult.getWearRate());
            assertNotEquals(ancientResult.getLambdaRatio(), modernResult.getLambdaRatio());
        }

        @Test
        @DisplayName("古代青铜木质轴承磨损高于现代精密轴承")
        void ancientBronzeWoodWearHigherThanModernPrecise() {
            BearingFrictionModel.FrictionSimulationResult ancientResult =
                    frictionModel.simulateWithTechnologyLevel(
                            testConfig, baseSensorData, 0.0, LocalDateTime.now(),
                            BearingTechnologyLevel.ANCIENT_BRONZE_WOOD);
            BearingFrictionModel.FrictionSimulationResult modernResult =
                    frictionModel.simulateWithTechnologyLevel(
                            testConfig, baseSensorData, 0.0, LocalDateTime.now(),
                            BearingTechnologyLevel.MODERN_PRECISE);

            assertTrue(ancientResult.getWearRate() > modernResult.getWearRate() * 10,
                    "古代轴承磨损应至少是现代轴承的10倍");
        }

        @Test
        @DisplayName("现代精密仪器磨损速率最低验证")
        void modernPreciseHasLowestWearRate() {
            double minWear = Double.MAX_VALUE;
            InstrumentType minType = null;

            for (InstrumentType type : InstrumentType.values()) {
                Map<String, Object> chars = frictionModel.getInstrumentTypeBearingCharacteristics(type);
                BearingTechnologyLevel techLevel = (BearingTechnologyLevel) chars.get("technologyLevel");
                BearingFrictionModel.FrictionSimulationResult result =
                        frictionModel.simulateWithTechnologyLevel(
                                testConfig, baseSensorData, 0.0, LocalDateTime.now(), techLevel);
                if (result.getWearRate() < minWear) {
                    minWear = result.getWearRate();
                    minType = type;
                }
            }

            assertEquals(InstrumentType.MODERN_PRECISE, minType,
                    "现代精密仪器应具有最低磨损速率");
        }

        @Test
        @DisplayName("5种仪器都能返回有效的估计精度")
        void allInstrumentsReturnValidEstimatedPrecision() {
            for (InstrumentType type : InstrumentType.values()) {
                Map<String, Object> chars = frictionModel.getInstrumentTypeBearingCharacteristics(type);
                Double precision = (Double) chars.get("estimatedPrecisionArcsec");
                assertNotNull(precision, type.getDisplayName() + "应有估计精度");
                assertTrue(precision > 0, type.getDisplayName() + "估计精度应为正数");
            }
        }

        @Test
        @DisplayName("关键特征列表验证")
        void keyFeaturesValidation() {
            Map<String, Object> chars = frictionModel.getInstrumentTypeBearingCharacteristics(
                    InstrumentType.ARMILLARY_SIMPLIFIED);
            @SuppressWarnings("unchecked")
            List<String> features = (List<String>) chars.get("keyFeatures");

            assertTrue(features.stream().anyMatch(f -> f.contains("郭守敬")),
                    "简仪特征应包含郭守敬");
            assertTrue(features.stream().anyMatch(f -> f.contains("1279")),
                    "简仪特征应包含1279年");
        }
    }

    @Nested
    @DisplayName("边界用例")
    class BoundaryCases {

        @Test
        @DisplayName("新轴承(磨损为0)对比验证")
        void brandNewBearingComparison() {
            for (InstrumentType type : InstrumentType.values()) {
                Map<String, Object> chars = frictionModel.getInstrumentTypeBearingCharacteristics(type);
                BearingTechnologyLevel techLevel = (BearingTechnologyLevel) chars.get("technologyLevel");
                BearingFrictionModel.FrictionSimulationResult result =
                        frictionModel.simulateWithTechnologyLevel(
                                testConfig, baseSensorData, 0.0, LocalDateTime.now(), techLevel);

                assertEquals(0.0, result.getTotalWearDepth(), 1e-10,
                        type.getDisplayName() + "新轴承总磨损应为0");
            }
        }

        @Test
        @DisplayName("接近寿命末期(高累积磨损)对比")
        void nearEndOfLifeComparison() {
            double accumulatedWear = 0.09;

            BearingFrictionModel.FrictionSimulationResult ancientResult =
                    frictionModel.simulateWithTechnologyLevel(
                            testConfig, baseSensorData, accumulatedWear, LocalDateTime.now(),
                            BearingTechnologyLevel.ANCIENT_BRONZE_IRON);
            BearingFrictionModel.FrictionSimulationResult modernResult =
                    frictionModel.simulateWithTechnologyLevel(
                            testConfig, baseSensorData, accumulatedWear, LocalDateTime.now(),
                            BearingTechnologyLevel.MODERN_PRECISE);

            assertTrue(ancientResult.getTotalWearDepth() > 0.09,
                    "高磨损下古代轴承总磨损应继续增加");
            assertTrue(modernResult.getTotalWearDepth() > 0.09,
                    "高磨损下现代轴承总磨损应继续增加");
        }

        @Test
        @DisplayName("极低转速对比验证")
        void extremelyLowSpeedComparison() {
            SensorDataDTO lowSpeedData = SensorDataDTO.builder()
                    .instrumentId(UUID.randomUUID())
                    .axisName("赤道轴")
                    .timestamp(LocalDateTime.now())
                    .rotationalSpeed(BigDecimal.valueOf(0.001))
                    .temperature(BigDecimal.valueOf(25.0))
                    .loadRadial(BigDecimal.valueOf(500.0))
                    .loadAxial(BigDecimal.valueOf(100.0))
                    .build();

            for (InstrumentType type : InstrumentType.values()) {
                Map<String, Object> chars = frictionModel.getInstrumentTypeBearingCharacteristics(type);
                BearingTechnologyLevel techLevel = (BearingTechnologyLevel) chars.get("technologyLevel");
                BearingFrictionModel.FrictionSimulationResult result =
                        frictionModel.simulateWithTechnologyLevel(
                                testConfig, lowSpeedData, 0.0, LocalDateTime.now(), techLevel);

                assertTrue(result.getFrictionCoefficient() > 0,
                        type.getDisplayName() + "低转速下摩擦系数应为正");
            }
        }

        @Test
        @DisplayName("高温环境对比验证")
        void highTemperatureComparison() {
            SensorDataDTO highTempData = SensorDataDTO.builder()
                    .instrumentId(UUID.randomUUID())
                    .axisName("赤道轴")
                    .timestamp(LocalDateTime.now())
                    .rotationalSpeed(BigDecimal.valueOf(1.0))
                    .temperature(BigDecimal.valueOf(80.0))
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

            assertTrue(ancientResult.getLambdaRatio() > 0,
                    "高温下古代轴承lambda比率应为正");
            assertTrue(modernResult.getLambdaRatio() > ancientResult.getLambdaRatio(),
                    "高温下现代轴承lambda比率应更高");
        }

        @Test
        @DisplayName("低温环境对比验证")
        void lowTemperatureComparison() {
            SensorDataDTO lowTempData = SensorDataDTO.builder()
                    .instrumentId(UUID.randomUUID())
                    .axisName("赤道轴")
                    .timestamp(LocalDateTime.now())
                    .rotationalSpeed(BigDecimal.valueOf(1.0))
                    .temperature(BigDecimal.valueOf(-20.0))
                    .loadRadial(BigDecimal.valueOf(500.0))
                    .loadAxial(BigDecimal.valueOf(100.0))
                    .build();

            for (InstrumentType type : InstrumentType.values()) {
                Map<String, Object> chars = frictionModel.getInstrumentTypeBearingCharacteristics(type);
                BearingTechnologyLevel techLevel = (BearingTechnologyLevel) chars.get("technologyLevel");
                BearingFrictionModel.FrictionSimulationResult result =
                        frictionModel.simulateWithTechnologyLevel(
                                testConfig, lowTempData, 0.0, LocalDateTime.now(), techLevel);

                assertTrue(result.getFrictionCoefficient() > 0,
                        type.getDisplayName() + "低温下摩擦系数应为正");
            }
        }

        @Test
        @DisplayName("极高负载对比验证")
        void extremelyHighLoadComparison() {
            SensorDataDTO highLoadData = SensorDataDTO.builder()
                    .instrumentId(UUID.randomUUID())
                    .axisName("赤道轴")
                    .timestamp(LocalDateTime.now())
                    .rotationalSpeed(BigDecimal.valueOf(1.0))
                    .temperature(BigDecimal.valueOf(25.0))
                    .loadRadial(BigDecimal.valueOf(10000.0))
                    .loadAxial(BigDecimal.valueOf(5000.0))
                    .build();

            for (InstrumentType type : InstrumentType.values()) {
                Map<String, Object> chars = frictionModel.getInstrumentTypeBearingCharacteristics(type);
                BearingTechnologyLevel techLevel = (BearingTechnologyLevel) chars.get("technologyLevel");
                BearingFrictionModel.FrictionSimulationResult result =
                        frictionModel.simulateWithTechnologyLevel(
                                testConfig, highLoadData, 0.0, LocalDateTime.now(), techLevel);

                assertTrue(result.getContactPressure() > 0,
                        type.getDisplayName() + "高负载下接触压力应为正");
            }
        }

        @Test
        @DisplayName("InstrumentType.fromString边界值验证")
        void instrumentTypeFromStringBoundaryValues() {
            assertEquals(InstrumentType.ARMILLARY_SIMPLIFIED, InstrumentType.fromString(null));
            assertEquals(InstrumentType.ARMILLARY_SIMPLIFIED, InstrumentType.fromString(""));
            assertEquals(InstrumentType.ARMILLARY_SIMPLIFIED, InstrumentType.fromString("invalid"));
            assertEquals(InstrumentType.ARMILLARY_SIMPLIFIED, InstrumentType.fromString("简仪"));
            assertEquals(InstrumentType.ARMILLARY_SIMPLIFIED, InstrumentType.fromString("ARMILLARY_SIMPLIFIED"));
            assertEquals(InstrumentType.QUADRANT, InstrumentType.fromString("象限仪"));
        }
    }

    @Nested
    @DisplayName("异常用例")
    class AbnormalCases {

        @Test
        @DisplayName("BearingConfig可选字段为null时仍能正常计算")
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

            for (InstrumentType type : InstrumentType.values()) {
                Map<String, Object> chars = frictionModel.getInstrumentTypeBearingCharacteristics(type);
                BearingTechnologyLevel techLevel = (BearingTechnologyLevel) chars.get("technologyLevel");
                BearingFrictionModel.FrictionSimulationResult result =
                        frictionModel.simulateWithTechnologyLevel(
                                nullFieldsConfig, baseSensorData, 0.0, LocalDateTime.now(), techLevel);

                assertNotNull(result);
                assertTrue(result.getFrictionCoefficient() >= 0);
            }
        }

        @Test
        @DisplayName("SensorDataDTO字段为null时的默认值处理")
        void nullFieldsInSensorDataDTO() {
            SensorDataDTO nullData = SensorDataDTO.builder()
                    .instrumentId(UUID.randomUUID())
                    .axisName("赤道轴")
                    .timestamp(LocalDateTime.now())
                    .build();

            for (InstrumentType type : InstrumentType.values()) {
                Map<String, Object> chars = frictionModel.getInstrumentTypeBearingCharacteristics(type);
                BearingTechnologyLevel techLevel = (BearingTechnologyLevel) chars.get("technologyLevel");
                BearingFrictionModel.FrictionSimulationResult result =
                        frictionModel.simulateWithTechnologyLevel(
                                testConfig, nullData, 0.0, LocalDateTime.now(), techLevel);

                assertNotNull(result);
                assertTrue(Double.isFinite(result.getWearRate()));
            }
        }

        @Test
        @DisplayName("零负载下的对比验证")
        void zeroLoadComparison() {
            SensorDataDTO zeroLoadData = SensorDataDTO.builder()
                    .instrumentId(UUID.randomUUID())
                    .axisName("赤道轴")
                    .timestamp(LocalDateTime.now())
                    .rotationalSpeed(BigDecimal.valueOf(1.0))
                    .temperature(BigDecimal.valueOf(25.0))
                    .loadRadial(BigDecimal.valueOf(0.0))
                    .loadAxial(BigDecimal.valueOf(0.0))
                    .build();

            for (InstrumentType type : InstrumentType.values()) {
                Map<String, Object> chars = frictionModel.getInstrumentTypeBearingCharacteristics(type);
                BearingTechnologyLevel techLevel = (BearingTechnologyLevel) chars.get("technologyLevel");
                BearingFrictionModel.FrictionSimulationResult result =
                        frictionModel.simulateWithTechnologyLevel(
                                testConfig, zeroLoadData, 0.0, LocalDateTime.now(), techLevel);

                assertNotNull(result);
                assertTrue(Double.isFinite(result.getFrictionCoefficient()));
            }
        }

        @Test
        @DisplayName("负累积磨损处理验证")
        void negativeAccumulatedWear() {
            for (InstrumentType type : InstrumentType.values()) {
                Map<String, Object> chars = frictionModel.getInstrumentTypeBearingCharacteristics(type);
                BearingTechnologyLevel techLevel = (BearingTechnologyLevel) chars.get("technologyLevel");
                BearingFrictionModel.FrictionSimulationResult result =
                        frictionModel.simulateWithTechnologyLevel(
                                testConfig, baseSensorData, -0.01, LocalDateTime.now(), techLevel);

                assertNotNull(result);
                assertTrue(Double.isFinite(result.getTotalWearDepth()));
            }
        }

        @Test
        @DisplayName("所有InstrumentType枚举值都被覆盖")
        void allInstrumentTypesCovered() {
            InstrumentType[] types = InstrumentType.values();
            assertEquals(5, types.length, "应有5种仪器类型");

            for (InstrumentType type : types) {
                Map<String, Object> chars = frictionModel.getInstrumentTypeBearingCharacteristics(type);
                assertNotNull(chars.get("technologyLevel"),
                        type.name() + "应有技术等级");
                assertNotNull(chars.get("typicalLubricant"),
                        type.name() + "应有典型润滑剂");
            }
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
