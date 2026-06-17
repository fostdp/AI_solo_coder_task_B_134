package com.astrohistory.armillary.simulation;

import com.astrohistory.armillary.dto.LubricantComparisonDTO;
import com.astrohistory.armillary.dto.SensorDataDTO;
import com.astrohistory.armillary.entity.ArmillaryInstrument;
import com.astrohistory.armillary.entity.BearingConfig;
import com.astrohistory.armillary.enums.LubricantType;
import org.junit.jupiter.api.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("润滑剂对比仿真测试")
class LubricantComparisonTest {

    private BearingFrictionModel model;

    @BeforeEach
    void setUp() {
        model = new BearingFrictionModel();
    }

    private BearingConfig createTestConfig() {
        ArmillaryInstrument instrument = ArmillaryInstrument.builder()
                .id(UUID.randomUUID()).name("测试简仪").model("TEST-01")
                .location("北京").buildYear(1279).build();
        return BearingConfig.builder()
                .id(UUID.randomUUID()).instrument(instrument).axisName("赤道轴")
                .axisType("赤道").bearingType("滑动轴承").material("锡青铜-灰铸铁")
                .innerRingMaterial("锡青铜").outerRingMaterial("灰铸铁")
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

    private SensorDataDTO createTestSensorData() {
        return SensorDataDTO.builder()
                .instrumentId(UUID.randomUUID())
                .instrumentName("测试简仪")
                .axisName("赤道轴")
                .timestamp(LocalDateTime.of(2024, 1, 1, 12, 0))
                .rotationalSpeed(BigDecimal.valueOf(10.0))
                .frictionTorque(BigDecimal.valueOf(0.5))
                .wearDepth(BigDecimal.valueOf(0.01))
                .pointingErrorAz(BigDecimal.valueOf(0.001))
                .pointingErrorAlt(BigDecimal.valueOf(0.001))
                .temperature(BigDecimal.valueOf(25.0))
                .loadRadial(BigDecimal.valueOf(50.0))
                .loadAxial(BigDecimal.valueOf(10.0))
                .build();
    }

    private LubricantComparisonDTO.LubricantDataPoint findByType(
            List<LubricantComparisonDTO.LubricantDataPoint> points, LubricantType type) {
        return points.stream()
                .filter(p -> p.getLubricantType() == type)
                .findFirst()
                .orElseThrow();
    }

    @Nested
    @DisplayName("正常用例")
    class NormalCases {

        @Test
        @DisplayName("DRY润滑剂具有最高磨损率和最低寿命")
        void dryHasHighestWearRateAndLowestLifetime() {
            BearingConfig config = createTestConfig();
            SensorDataDTO sensorData = createTestSensorData();
            List<LubricantComparisonDTO.LubricantDataPoint> results =
                    model.compareAllLubricants(config, sensorData, 0.0, LocalDateTime.now());

            LubricantComparisonDTO.LubricantDataPoint dry = findByType(results, LubricantType.DRY);

            for (LubricantComparisonDTO.LubricantDataPoint point : results) {
                if (point.getLubricantType() != LubricantType.DRY) {
                    assertTrue(dry.getWearRateMPerSec() >= point.getWearRateMPerSec(),
                            "DRY磨损率应高于" + point.getLubricantType());
                    assertTrue(dry.getEstimatedLifetimeHours() <= point.getEstimatedLifetimeHours(),
                            "DRY寿命应低于" + point.getLubricantType());
                }
            }
        }

        @Test
        @DisplayName("MODERN_SYNTHETIC具有最低磨损率和最高寿命")
        void modernSyntheticHasLowestWearRateAndHighestLifetime() {
            BearingConfig config = createTestConfig();
            SensorDataDTO sensorData = createTestSensorData();
            List<LubricantComparisonDTO.LubricantDataPoint> results =
                    model.compareAllLubricants(config, sensorData, 0.0, LocalDateTime.now());

            LubricantComparisonDTO.LubricantDataPoint synthetic = findByType(results, LubricantType.MODERN_SYNTHETIC);

            for (LubricantComparisonDTO.LubricantDataPoint point : results) {
                if (point.getLubricantType() != LubricantType.MODERN_SYNTHETIC) {
                    assertTrue(synthetic.getWearRateMPerSec() <= point.getWearRateMPerSec(),
                            "MODERN_SYNTHETIC磨损率应低于" + point.getLubricantType());
                    assertTrue(synthetic.getEstimatedLifetimeHours() >= point.getEstimatedLifetimeHours(),
                            "MODERN_SYNTHETIC寿命应高于" + point.getLubricantType());
                }
            }
        }

        @Test
        @DisplayName("寿命排序: MODERN_SYNTHETIC > MINERAL_OIL > VEGETABLE_OIL > ANIMAL_FAT > DRY")
        void lifetimeOrdering() {
            BearingConfig config = createTestConfig();
            SensorDataDTO sensorData = createTestSensorData();
            List<LubricantComparisonDTO.LubricantDataPoint> results =
                    model.compareAllLubricants(config, sensorData, 0.0, LocalDateTime.now());

            double modern = findByType(results, LubricantType.MODERN_SYNTHETIC).getEstimatedLifetimeHours();
            double mineral = findByType(results, LubricantType.MINERAL_OIL).getEstimatedLifetimeHours();
            double vegetable = findByType(results, LubricantType.VEGETABLE_OIL).getEstimatedLifetimeHours();
            double animal = findByType(results, LubricantType.ANIMAL_FAT).getEstimatedLifetimeHours();
            double dry = findByType(results, LubricantType.DRY).getEstimatedLifetimeHours();

            assertTrue(modern > mineral, "MODERN_SYNTHETIC寿命应大于MINERAL_OIL");
            assertTrue(mineral > vegetable, "MINERAL_OIL寿命应大于VEGETABLE_OIL");
            assertTrue(vegetable > animal, "VEGETABLE_OIL寿命应大于ANIMAL_FAT");
            assertTrue(animal > dry, "ANIMAL_FAT寿命应大于DRY");
        }

        @Test
        @DisplayName("compareAllLubricants返回6个数据点")
        void compareAllLubricantsReturnsSixDataPoints() {
            BearingConfig config = createTestConfig();
            SensorDataDTO sensorData = createTestSensorData();
            List<LubricantComparisonDTO.LubricantDataPoint> results =
                    model.compareAllLubricants(config, sensorData, 0.0, LocalDateTime.now());

            assertEquals(6, results.size());
        }

        @Test
        @DisplayName("历史可用性: ANIMAL_FAT/VEGETABLE_OIL/MERCURY/DRY=true, MINERAL_OIL/MODERN_SYNTHETIC=false")
        void historicallyAvailableFlags() {
            BearingConfig config = createTestConfig();
            SensorDataDTO sensorData = createTestSensorData();
            List<LubricantComparisonDTO.LubricantDataPoint> results =
                    model.compareAllLubricants(config, sensorData, 0.0, LocalDateTime.now());

            assertTrue(findByType(results, LubricantType.ANIMAL_FAT).isHistoricallyAvailable());
            assertTrue(findByType(results, LubricantType.VEGETABLE_OIL).isHistoricallyAvailable());
            assertTrue(findByType(results, LubricantType.MERCURY).isHistoricallyAvailable());
            assertTrue(findByType(results, LubricantType.DRY).isHistoricallyAvailable());
            assertFalse(findByType(results, LubricantType.MINERAL_OIL).isHistoricallyAvailable());
            assertFalse(findByType(results, LubricantType.MODERN_SYNTHETIC).isHistoricallyAvailable());
        }

        @Test
        @DisplayName("摩擦系数排序: DRY > ANIMAL_FAT > VEGETABLE_OIL > MINERAL_OIL > MODERN_SYNTHETIC")
        void frictionCoefficientOrdering() {
            BearingConfig config = createTestConfig();
            SensorDataDTO sensorData = createTestSensorData();
            List<LubricantComparisonDTO.LubricantDataPoint> results =
                    model.compareAllLubricants(config, sensorData, 0.0, LocalDateTime.now());

            double dry = findByType(results, LubricantType.DRY).getFrictionCoefficient();
            double animal = findByType(results, LubricantType.ANIMAL_FAT).getFrictionCoefficient();
            double vegetable = findByType(results, LubricantType.VEGETABLE_OIL).getFrictionCoefficient();
            double mineral = findByType(results, LubricantType.MINERAL_OIL).getFrictionCoefficient();
            double modern = findByType(results, LubricantType.MODERN_SYNTHETIC).getFrictionCoefficient();

            assertTrue(dry > animal, "DRY摩擦系数应大于ANIMAL_FAT");
            assertTrue(animal > vegetable, "ANIMAL_FAT摩擦系数应大于VEGETABLE_OIL");
            assertTrue(vegetable > mineral, "VEGETABLE_OIL摩擦系数应大于MINERAL_OIL");
            assertTrue(mineral > modern, "MINERAL_OIL摩擦系数应大于MODERN_SYNTHETIC");
        }

        @Test
        @DisplayName("MODERN_SYNTHETIC寿命至少为VEGETABLE_OIL的3倍")
        void modernSyntheticExtendsLifetime3xOverVegetableOil() {
            BearingConfig config = createTestConfig();
            SensorDataDTO sensorData = createTestSensorData();
            List<LubricantComparisonDTO.LubricantDataPoint> results =
                    model.compareAllLubricants(config, sensorData, 0.0, LocalDateTime.now());

            double modernLifetime = findByType(results, LubricantType.MODERN_SYNTHETIC).getEstimatedLifetimeHours();
            double vegetableLifetime = findByType(results, LubricantType.VEGETABLE_OIL).getEstimatedLifetimeHours();

            assertTrue(modernLifetime >= 3.0 * vegetableLifetime,
                    "MODERN_SYNTHETIC寿命应至少为VEGETABLE_OIL的3倍");
        }

        @Test
        @DisplayName("所有润滑剂的估计寿命为正且有限")
        void estimatedLifetimePositiveAndFinite() {
            BearingConfig config = createTestConfig();
            SensorDataDTO sensorData = createTestSensorData();
            List<LubricantComparisonDTO.LubricantDataPoint> results =
                    model.compareAllLubricants(config, sensorData, 0.0, LocalDateTime.now());

            for (LubricantComparisonDTO.LubricantDataPoint point : results) {
                assertTrue(point.getEstimatedLifetimeHours() > 0,
                        point.getLubricantType() + "寿命应为正数");
                assertTrue(Double.isFinite(point.getEstimatedLifetimeHours()),
                        point.getLubricantType() + "寿命应为有限值");
            }

            double dryLifetime = findByType(results, LubricantType.DRY).getEstimatedLifetimeHours();
            for (LubricantComparisonDTO.LubricantDataPoint point : results) {
                if (point.getLubricantType() != LubricantType.DRY) {
                    assertTrue(dryLifetime < point.getEstimatedLifetimeHours(),
                            "DRY寿命应远低于" + point.getLubricantType());
                }
            }
        }

        @Test
        @DisplayName("getViscosityAtTemperature插值计算")
        void viscosityAtTemperatureInterpolation() {
            assertEquals(0.045, LubricantType.ANIMAL_FAT.getViscosityAtTemperature(25.0),
                    "25°C应返回viscosityAt40C");
            assertEquals(0.03, LubricantType.ANIMAL_FAT.getViscosityAtTemperature(100.0),
                    "100°C应返回viscosityAt100C");

            double viscosityAt70 = LubricantType.ANIMAL_FAT.getViscosityAtTemperature(70.0);
            double expectedAt70 = 0.045 * (1.0 - (70.0 - 40.0) / 60.0) + 0.03 * ((70.0 - 40.0) / 60.0);
            assertEquals(expectedAt70, viscosityAt70, 1e-10, "70°C应线性插值");
        }
    }

    @Nested
    @DisplayName("边界用例")
    class BoundaryCases {

        @Test
        @DisplayName("温度10°C（最小值钳位）")
        void temperatureAtMinClamp() {
            double viscosity = LubricantType.VEGETABLE_OIL.getViscosityAtTemperature(10.0);
            assertEquals(LubricantType.VEGETABLE_OIL.getViscosityAt40C(), viscosity,
                    "10°C钳位后应返回viscosityAt40C");
        }

        @Test
        @DisplayName("温度200°C（使用viscosityAt100C）")
        void temperatureAbove100C() {
            double viscosity = LubricantType.VEGETABLE_OIL.getViscosityAtTemperature(200.0);
            assertEquals(LubricantType.VEGETABLE_OIL.getViscosityAt100C(), viscosity,
                    "200°C应返回viscosityAt100C");
        }

        @Test
        @DisplayName("温度-50°C（钳位到10°C）")
        void temperatureBelowMinClamp() {
            double viscosity = LubricantType.VEGETABLE_OIL.getViscosityAtTemperature(-50.0);
            assertEquals(LubricantType.VEGETABLE_OIL.getViscosityAt40C(), viscosity,
                    "-50°C应钳位到10°C后返回viscosityAt40C");
        }

        @Test
        @DisplayName("累积磨损接近最大允许磨损")
        void accumulatedWearNearMaxAllowable() {
            BearingConfig config = createTestConfig();
            SensorDataDTO sensorData = createTestSensorData();
            double nearMax = config.getMaxAllowableWear().doubleValue() - 0.001;

            BearingFrictionModel.FrictionSimulationResult result =
                    model.simulateWithLubricantOverride(config, sensorData, nearMax, LocalDateTime.now(),
                            LubricantType.VEGETABLE_OIL);

            assertNotNull(result);
            assertTrue(result.getTotalWearDepth() > nearMax);
            assertTrue(result.getWearRate() > 0);
        }

        @Test
        @DisplayName("极高载荷")
        void extremelyHighLoad() {
            BearingConfig config = createTestConfig();
            SensorDataDTO sensorData = SensorDataDTO.builder()
                    .instrumentId(UUID.randomUUID())
                    .instrumentName("测试简仪")
                    .axisName("赤道轴")
                    .timestamp(LocalDateTime.of(2024, 1, 1, 12, 0))
                    .rotationalSpeed(BigDecimal.valueOf(10.0))
                    .temperature(BigDecimal.valueOf(25.0))
                    .loadRadial(BigDecimal.valueOf(10000.0))
                    .loadAxial(BigDecimal.valueOf(5000.0))
                    .build();

            List<LubricantComparisonDTO.LubricantDataPoint> results =
                    model.compareAllLubricants(config, sensorData, 0.0, LocalDateTime.now());

            assertEquals(6, results.size());
            for (LubricantComparisonDTO.LubricantDataPoint point : results) {
                assertTrue(Double.isFinite(point.getFrictionCoefficient()),
                        point.getLubricantType() + "摩擦系数应为有限值");
                assertTrue(Double.isFinite(point.getWearRateMPerSec()),
                        point.getLubricantType() + "磨损率应为有限值");
                assertTrue(point.getFrictionCoefficient() >= 0,
                        point.getLubricantType() + "摩擦系数应为非负");
            }
        }

        @Test
        @DisplayName("零累积磨损时润滑剂差异对比")
        void zeroAccumulatedWearCompareLubricants() {
            BearingConfig config = createTestConfig();
            SensorDataDTO sensorData = createTestSensorData();
            List<LubricantComparisonDTO.LubricantDataPoint> results =
                    model.compareAllLubricants(config, sensorData, 0.0, LocalDateTime.now());

            double modernWear = findByType(results, LubricantType.MODERN_SYNTHETIC).getWearRateMPerSec();
            double dryWear = findByType(results, LubricantType.DRY).getWearRateMPerSec();

            assertTrue(dryWear > modernWear, "零磨损时DRY磨损率仍应高于MODERN_SYNTHETIC");

            for (LubricantComparisonDTO.LubricantDataPoint point : results) {
                assertTrue(point.getWearRateMPerSec() > 0,
                        point.getLubricantType() + "磨损率应为正数");
            }
        }
    }

    @Nested
    @DisplayName("异常用例")
    class AbnormalCases {

        @Test
        @DisplayName("LubricantType.fromString(null)返回VEGETABLE_OIL")
        void fromStringNull() {
            assertEquals(LubricantType.VEGETABLE_OIL, LubricantType.fromString(null));
        }

        @Test
        @DisplayName("LubricantType.fromString无效值返回VEGETABLE_OIL")
        void fromStringInvalid() {
            assertEquals(LubricantType.VEGETABLE_OIL, LubricantType.fromString("invalid"));
        }

        @Test
        @DisplayName("LubricantType.fromString有效值返回对应枚举")
        void fromStringValid() {
            assertEquals(LubricantType.ANIMAL_FAT, LubricantType.fromString("ANIMAL_FAT"));
        }

        @Test
        @DisplayName("BearingConfig可选字段为null")
        void nullBearingConfigOptionalFields() {
            ArmillaryInstrument instrument = ArmillaryInstrument.builder()
                    .id(UUID.randomUUID()).name("测试简仪").build();
            BearingConfig config = BearingConfig.builder()
                    .id(UUID.randomUUID())
                    .instrument(instrument)
                    .axisName("赤道轴")
                    .axisType("赤道")
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

            SensorDataDTO sensorData = createTestSensorData();

            BearingFrictionModel.FrictionSimulationResult result =
                    model.simulateWithLubricantOverride(config, sensorData, 0.0, LocalDateTime.now(),
                            LubricantType.VEGETABLE_OIL);

            assertNotNull(result);
            assertTrue(result.getFrictionCoefficient() >= 0);
            assertTrue(result.getWearRate() > 0);
        }

        @Test
        @DisplayName("零转速仍返回结果")
        void zeroSpeedReturnsResults() {
            BearingConfig config = createTestConfig();
            SensorDataDTO sensorData = SensorDataDTO.builder()
                    .instrumentId(UUID.randomUUID())
                    .instrumentName("测试简仪")
                    .axisName("赤道轴")
                    .timestamp(LocalDateTime.of(2024, 1, 1, 12, 0))
                    .rotationalSpeed(BigDecimal.ZERO)
                    .temperature(BigDecimal.valueOf(25.0))
                    .loadRadial(BigDecimal.valueOf(50.0))
                    .loadAxial(BigDecimal.valueOf(10.0))
                    .build();

            BearingFrictionModel.FrictionSimulationResult result =
                    model.simulateWithLubricantOverride(config, sensorData, 0.0, LocalDateTime.now(),
                            LubricantType.VEGETABLE_OIL);

            assertNotNull(result);
            assertTrue(result.getFrictionCoefficient() >= 0);
            assertTrue(Double.isFinite(result.getFrictionCoefficient()));
        }
    }
}
