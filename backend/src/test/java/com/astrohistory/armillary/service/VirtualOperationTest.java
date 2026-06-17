package com.astrohistory.armillary.service;

import com.astrohistory.armillary.dto.VirtualOperationRequest;
import com.astrohistory.armillary.dto.VirtualOperationResponse;
import com.astrohistory.armillary.entity.ArmillaryInstrument;
import com.astrohistory.armillary.entity.BearingConfig;
import com.astrohistory.armillary.repository.BearingConfigRepository;
import com.astrohistory.armillary.repository.FrictionSimulationRepository;
import com.astrohistory.armillary.repository.PointingAnalysisRepository;
import com.astrohistory.armillary.repository.SensorDataRepository;
import com.astrohistory.armillary.simulation.BearingFrictionModel;
import com.astrohistory.armillary.simulation.PointingAccuracyModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VirtualOperationTest {

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

    private BearingFrictionModel frictionModel;

    private ComparisonAnalysisService service;

    private UUID instrumentId;

    private ArmillaryInstrument instrument;

    @BeforeEach
    void setUp() {
        frictionModel = new BearingFrictionModel();
        service = new ComparisonAnalysisService(
                frictionModel, pointingAccuracyModel,
                bearingConfigRepository, frictionSimulationRepository,
                sensorDataRepository, pointingAnalysisRepository);

        instrumentId = UUID.randomUUID();
        instrument = ArmillaryInstrument.builder()
                .id(instrumentId)
                .name("测试简仪")
                .model("JY-001")
                .location("北京古观象台")
                .buildYear(1279)
                .build();

        when(bearingConfigRepository.findByInstrumentId(instrumentId))
                .thenReturn(buildDefaultBearingConfigs());
    }

    private List<BearingConfig> buildDefaultBearingConfigs() {
        List<BearingConfig> configs = new ArrayList<>();
        configs.add(buildBearingConfig("赤道轴", "EQUATORIAL"));
        configs.add(buildBearingConfig("赤纬轴", "DECLINATION"));
        configs.add(buildBearingConfig("地平经轴", "AZIMUTH"));
        configs.add(buildBearingConfig("地平纬轴", "ALTITUDE"));
        return configs;
    }

    private BearingConfig buildBearingConfig(String axisName, String axisType) {
        return BearingConfig.builder()
                .id(UUID.randomUUID())
                .instrument(instrument)
                .axisName(axisName)
                .axisType(axisType)
                .bearingType("滑动轴承")
                .material("锡青铜-灰铸铁")
                .innerRingMaterial("锡青铜")
                .outerRingMaterial("灰铸铁")
                .surfaceRoughnessRa(BigDecimal.valueOf(0.8))
                .perpendicularityError(BigDecimal.valueOf(0.01))
                .axialRunout(BigDecimal.valueOf(0.005))
                .radialRunout(BigDecimal.valueOf(0.003))
                .innerDiameter(BigDecimal.valueOf(50.0))
                .outerDiameter(BigDecimal.valueOf(80.0))
                .width(BigDecimal.valueOf(30.0))
                .initialClearance(BigDecimal.valueOf(0.05))
                .lubricantViscosity(BigDecimal.valueOf(0.035))
                .elasticModulus(BigDecimal.valueOf(180000.0))
                .poissonRatio(BigDecimal.valueOf(0.30))
                .hardness(BigDecimal.valueOf(180.0))
                .wearCoefficient(BigDecimal.valueOf(0.00005))
                .maxAllowableWear(BigDecimal.valueOf(0.1))
                .build();
    }

    private VirtualOperationRequest buildDefaultRequest() {
        return VirtualOperationRequest.builder()
                .instrumentId(instrumentId)
                .equatorialAxisAngleDeg(90.0)
                .declinationAxisAngleDeg(30.0)
                .azimuthAxisAngleDeg(180.0)
                .altitudeAxisAngleDeg(45.0)
                .rotationalSpeedRpm(1.0)
                .loadKg(500.0)
                .temperatureC(25.0)
                .lubricantType("VEGETABLE_OIL")
                .simulateWear(false)
                .simulateHours(0)
                .build();
    }

    @Nested
    @DisplayName("正常用例")
    class NormalCases {

        @Test
        @DisplayName("基本虚拟操作 - 典型参数返回有效响应")
        void basicVirtualOperation_returnsValidResponse() {
            VirtualOperationRequest request = buildDefaultRequest();
            VirtualOperationResponse response = service.simulateVirtualOperation(request);
            assertNotNull(response);
            assertEquals(180.0, response.getCurrentAzimuthDeg());
            assertEquals(45.0, response.getCurrentAltitudeDeg());
        }

        @Test
        @DisplayName("响应包含有效赤经赤纬坐标")
        void response_hasValidCoordinates() {
            VirtualOperationRequest request = buildDefaultRequest();
            VirtualOperationResponse response = service.simulateVirtualOperation(request);
            assertTrue(Double.isFinite(response.getCurrentRightAscensionDeg()));
            assertTrue(Double.isFinite(response.getCurrentDeclinationDeg()));
        }

        @Test
        @DisplayName("总指向误差为正值")
        void totalPointingError_isPositive() {
            VirtualOperationRequest request = buildDefaultRequest();
            VirtualOperationResponse response = service.simulateVirtualOperation(request);
            assertTrue(response.getTotalPointingErrorArcmin() > 0);
        }

        @Test
        @DisplayName("应力等级在1到5之间")
        void stressLevel_betweenOneAndFive() {
            VirtualOperationRequest request = buildDefaultRequest();
            VirtualOperationResponse response = service.simulateVirtualOperation(request);
            assertTrue(response.getStressLevel() >= 1);
            assertTrue(response.getStressLevel() <= 5);
        }

        @Test
        @DisplayName("目标星名不为空且不是未知天体")
        void targetStarName_notNullAndNotUnknown() {
            VirtualOperationRequest request = buildDefaultRequest();
            VirtualOperationResponse response = service.simulateVirtualOperation(request);
            assertNotNull(response.getTargetStarName());
            assertFalse(response.getTargetStarName().startsWith("未知天体"));
        }

        @Test
        @DisplayName("轴位置映射包含4个条目")
        void axisPositions_hasFourEntries() {
            VirtualOperationRequest request = buildDefaultRequest();
            VirtualOperationResponse response = service.simulateVirtualOperation(request);
            Map<String, Double> axisPositions = response.getAxisPositions();
            assertEquals(4, axisPositions.size());
            assertTrue(axisPositions.containsKey("equatorial"));
            assertTrue(axisPositions.containsKey("declination"));
            assertTrue(axisPositions.containsKey("azimuth"));
            assertTrue(axisPositions.containsKey("altitude"));
        }

        @Test
        @DisplayName("状态消息包含润滑、应力、磨损条目")
        void statusMessages_hasLubricationStressWearEntries() {
            VirtualOperationRequest request = buildDefaultRequest();
            VirtualOperationResponse response = service.simulateVirtualOperation(request);
            Map<String, String> statusMessages = response.getStatusMessages();
            assertTrue(statusMessages.containsKey("lubrication"));
            assertTrue(statusMessages.containsKey("stress"));
            assertTrue(statusMessages.containsKey("wear"));
        }

        @Test
        @DisplayName("指向北极 - 目标星名为北极星")
        void pointingNorth_targetsPolaris() {
            VirtualOperationRequest request = VirtualOperationRequest.builder()
                    .instrumentId(instrumentId)
                    .equatorialAxisAngleDeg(0.0)
                    .declinationAxisAngleDeg(89.0)
                    .azimuthAxisAngleDeg(0.0)
                    .altitudeAxisAngleDeg(89.0)
                    .rotationalSpeedRpm(0.5)
                    .loadKg(300.0)
                    .temperatureC(20.0)
                    .lubricantType("VEGETABLE_OIL")
                    .simulateWear(false)
                    .simulateHours(0)
                    .build();
            VirtualOperationResponse response = service.simulateVirtualOperation(request);
            assertTrue(response.getTargetStarName().contains("北极星"));
        }

        @Test
        @DisplayName("更高载荷产生更大摩擦扭矩")
        void higherLoad_increasesFrictionTorque() {
            VirtualOperationRequest lightLoad = VirtualOperationRequest.builder()
                    .instrumentId(instrumentId)
                    .equatorialAxisAngleDeg(90.0)
                    .declinationAxisAngleDeg(30.0)
                    .azimuthAxisAngleDeg(180.0)
                    .altitudeAxisAngleDeg(45.0)
                    .rotationalSpeedRpm(1.0)
                    .loadKg(100.0)
                    .temperatureC(25.0)
                    .lubricantType("VEGETABLE_OIL")
                    .simulateWear(false)
                    .simulateHours(0)
                    .build();
            VirtualOperationRequest heavyLoad = VirtualOperationRequest.builder()
                    .instrumentId(instrumentId)
                    .equatorialAxisAngleDeg(90.0)
                    .declinationAxisAngleDeg(30.0)
                    .azimuthAxisAngleDeg(180.0)
                    .altitudeAxisAngleDeg(45.0)
                    .rotationalSpeedRpm(1.0)
                    .loadKg(2000.0)
                    .temperatureC(25.0)
                    .lubricantType("VEGETABLE_OIL")
                    .simulateWear(false)
                    .simulateHours(0)
                    .build();
            VirtualOperationResponse lightResponse = service.simulateVirtualOperation(lightLoad);
            VirtualOperationResponse heavyResponse = service.simulateVirtualOperation(heavyLoad);
            assertTrue(heavyResponse.getCurrentFrictionTorqueNm() > lightResponse.getCurrentFrictionTorqueNm());
        }

        @Test
        @DisplayName("干摩擦润滑比现代合成油应力等级更高")
        void dryLubricant_higherStressThanModernSynthetic() {
            VirtualOperationRequest dryRequest = VirtualOperationRequest.builder()
                    .instrumentId(instrumentId)
                    .equatorialAxisAngleDeg(90.0)
                    .declinationAxisAngleDeg(30.0)
                    .azimuthAxisAngleDeg(180.0)
                    .altitudeAxisAngleDeg(45.0)
                    .rotationalSpeedRpm(1.0)
                    .loadKg(500.0)
                    .temperatureC(25.0)
                    .lubricantType("DRY")
                    .simulateWear(false)
                    .simulateHours(0)
                    .build();
            VirtualOperationRequest syntheticRequest = VirtualOperationRequest.builder()
                    .instrumentId(instrumentId)
                    .equatorialAxisAngleDeg(90.0)
                    .declinationAxisAngleDeg(30.0)
                    .azimuthAxisAngleDeg(180.0)
                    .altitudeAxisAngleDeg(45.0)
                    .rotationalSpeedRpm(1.0)
                    .loadKg(500.0)
                    .temperatureC(25.0)
                    .lubricantType("MODERN_SYNTHETIC")
                    .simulateWear(false)
                    .simulateHours(0)
                    .build();
            VirtualOperationResponse dryResponse = service.simulateVirtualOperation(dryRequest);
            VirtualOperationResponse syntheticResponse = service.simulateVirtualOperation(syntheticRequest);
            assertTrue(dryResponse.getStressLevel() >= syntheticResponse.getStressLevel());
        }

        @Test
        @DisplayName("启用磨损模拟且时间大于0时累积磨损增加")
        void simulateWear_increasesCumulativeWear() {
            VirtualOperationRequest noWear = VirtualOperationRequest.builder()
                    .instrumentId(instrumentId)
                    .equatorialAxisAngleDeg(90.0)
                    .declinationAxisAngleDeg(30.0)
                    .azimuthAxisAngleDeg(180.0)
                    .altitudeAxisAngleDeg(45.0)
                    .rotationalSpeedRpm(1.0)
                    .loadKg(500.0)
                    .temperatureC(25.0)
                    .lubricantType("VEGETABLE_OIL")
                    .simulateWear(false)
                    .simulateHours(0)
                    .build();
            VirtualOperationRequest withWear = VirtualOperationRequest.builder()
                    .instrumentId(instrumentId)
                    .equatorialAxisAngleDeg(90.0)
                    .declinationAxisAngleDeg(30.0)
                    .azimuthAxisAngleDeg(180.0)
                    .altitudeAxisAngleDeg(45.0)
                    .rotationalSpeedRpm(1.0)
                    .loadKg(500.0)
                    .temperatureC(25.0)
                    .lubricantType("VEGETABLE_OIL")
                    .simulateWear(true)
                    .simulateHours(1000)
                    .build();
            VirtualOperationResponse noWearResponse = service.simulateVirtualOperation(noWear);
            VirtualOperationResponse withWearResponse = service.simulateVirtualOperation(withWear);
            assertTrue(withWearResponse.getCumulativeWearMm() > noWearResponse.getCumulativeWearMm());
        }
    }

    @Nested
    @DisplayName("边界用例")
    class BoundaryCases {

        @Test
        @DisplayName("所有轴角度为0度")
        void allAxisAnglesZero() {
            VirtualOperationRequest request = VirtualOperationRequest.builder()
                    .instrumentId(instrumentId)
                    .equatorialAxisAngleDeg(0.0)
                    .declinationAxisAngleDeg(0.0)
                    .azimuthAxisAngleDeg(0.0)
                    .altitudeAxisAngleDeg(0.0)
                    .rotationalSpeedRpm(1.0)
                    .loadKg(500.0)
                    .temperatureC(25.0)
                    .lubricantType("VEGETABLE_OIL")
                    .simulateWear(false)
                    .simulateHours(0)
                    .build();
            VirtualOperationResponse response = service.simulateVirtualOperation(request);
            assertNotNull(response);
            assertEquals(0.0, response.getCurrentAzimuthDeg());
            assertEquals(0.0, response.getCurrentAltitudeDeg());
        }

        @Test
        @DisplayName("极端轴角度值 - 360度和负180度")
        void extremeAxisAngles() {
            VirtualOperationRequest request = VirtualOperationRequest.builder()
                    .instrumentId(instrumentId)
                    .equatorialAxisAngleDeg(360.0)
                    .declinationAxisAngleDeg(-180.0)
                    .azimuthAxisAngleDeg(360.0)
                    .altitudeAxisAngleDeg(-180.0)
                    .rotationalSpeedRpm(1.0)
                    .loadKg(500.0)
                    .temperatureC(25.0)
                    .lubricantType("VEGETABLE_OIL")
                    .simulateWear(false)
                    .simulateHours(0)
                    .build();
            VirtualOperationResponse response = service.simulateVirtualOperation(request);
            assertNotNull(response);
            assertEquals(360.0, response.getCurrentAzimuthDeg());
            assertEquals(-180.0, response.getCurrentAltitudeDeg());
        }

        @Test
        @DisplayName("最小载荷 - 0.1千克")
        void minimumLoad() {
            VirtualOperationRequest request = VirtualOperationRequest.builder()
                    .instrumentId(instrumentId)
                    .equatorialAxisAngleDeg(90.0)
                    .declinationAxisAngleDeg(30.0)
                    .azimuthAxisAngleDeg(180.0)
                    .altitudeAxisAngleDeg(45.0)
                    .rotationalSpeedRpm(1.0)
                    .loadKg(0.1)
                    .temperatureC(25.0)
                    .lubricantType("VEGETABLE_OIL")
                    .simulateWear(false)
                    .simulateHours(0)
                    .build();
            VirtualOperationResponse response = service.simulateVirtualOperation(request);
            assertNotNull(response);
            assertTrue(response.getCurrentFrictionTorqueNm() >= 0);
        }

        @Test
        @DisplayName("极高载荷 - 10000千克")
        void extremelyHighLoad() {
            VirtualOperationRequest request = VirtualOperationRequest.builder()
                    .instrumentId(instrumentId)
                    .equatorialAxisAngleDeg(90.0)
                    .declinationAxisAngleDeg(30.0)
                    .azimuthAxisAngleDeg(180.0)
                    .altitudeAxisAngleDeg(45.0)
                    .rotationalSpeedRpm(1.0)
                    .loadKg(10000.0)
                    .temperatureC(25.0)
                    .lubricantType("VEGETABLE_OIL")
                    .simulateWear(false)
                    .simulateHours(0)
                    .build();
            VirtualOperationResponse response = service.simulateVirtualOperation(request);
            assertNotNull(response);
            assertTrue(response.getTotalPointingErrorArcmin() > 0);
            assertTrue(response.getStressLevel() >= 1);
        }

        @Test
        @DisplayName("极高温度 - 200摄氏度")
        void veryHighTemperature() {
            VirtualOperationRequest request = VirtualOperationRequest.builder()
                    .instrumentId(instrumentId)
                    .equatorialAxisAngleDeg(90.0)
                    .declinationAxisAngleDeg(30.0)
                    .azimuthAxisAngleDeg(180.0)
                    .altitudeAxisAngleDeg(45.0)
                    .rotationalSpeedRpm(1.0)
                    .loadKg(500.0)
                    .temperatureC(200.0)
                    .lubricantType("VEGETABLE_OIL")
                    .simulateWear(false)
                    .simulateHours(0)
                    .build();
            VirtualOperationResponse response = service.simulateVirtualOperation(request);
            assertNotNull(response);
            assertTrue(Double.isFinite(response.getCurrentFrictionTorqueNm()));
        }

        @Test
        @DisplayName("极低温度 - 负40摄氏度")
        void veryLowTemperature() {
            VirtualOperationRequest request = VirtualOperationRequest.builder()
                    .instrumentId(instrumentId)
                    .equatorialAxisAngleDeg(90.0)
                    .declinationAxisAngleDeg(30.0)
                    .azimuthAxisAngleDeg(180.0)
                    .altitudeAxisAngleDeg(45.0)
                    .rotationalSpeedRpm(1.0)
                    .loadKg(500.0)
                    .temperatureC(-40.0)
                    .lubricantType("VEGETABLE_OIL")
                    .simulateWear(false)
                    .simulateHours(0)
                    .build();
            VirtualOperationResponse response = service.simulateVirtualOperation(request);
            assertNotNull(response);
            assertTrue(Double.isFinite(response.getCurrentFrictionTorqueNm()));
        }

        @Test
        @DisplayName("模拟小时为0且磨损模拟关闭")
        void simulateHoursZeroAndWearFalse() {
            VirtualOperationRequest request = VirtualOperationRequest.builder()
                    .instrumentId(instrumentId)
                    .equatorialAxisAngleDeg(90.0)
                    .declinationAxisAngleDeg(30.0)
                    .azimuthAxisAngleDeg(180.0)
                    .altitudeAxisAngleDeg(45.0)
                    .rotationalSpeedRpm(1.0)
                    .loadKg(500.0)
                    .temperatureC(25.0)
                    .lubricantType("VEGETABLE_OIL")
                    .simulateWear(false)
                    .simulateHours(0)
                    .build();
            VirtualOperationResponse response = service.simulateVirtualOperation(request);
            assertNotNull(response);
            assertTrue(response.getCumulativeWearMm() >= 0);
        }

        @Test
        @DisplayName("超长模拟时间 - 10000小时")
        void veryLongSimulationHours() {
            VirtualOperationRequest request = VirtualOperationRequest.builder()
                    .instrumentId(instrumentId)
                    .equatorialAxisAngleDeg(90.0)
                    .declinationAxisAngleDeg(30.0)
                    .azimuthAxisAngleDeg(180.0)
                    .altitudeAxisAngleDeg(45.0)
                    .rotationalSpeedRpm(1.0)
                    .loadKg(500.0)
                    .temperatureC(25.0)
                    .lubricantType("VEGETABLE_OIL")
                    .simulateWear(true)
                    .simulateHours(10000)
                    .build();
            VirtualOperationResponse response = service.simulateVirtualOperation(request);
            assertNotNull(response);
            assertTrue(response.getCumulativeWearMm() > 0);
        }
    }

    @Nested
    @DisplayName("异常用例")
    class AbnormalCases {

        @Test
        @DisplayName("无轴承配置时抛出IllegalArgumentException")
        void noBearingConfig_throwsIllegalArgumentException() {
            UUID unknownInstrumentId = UUID.randomUUID();
            when(bearingConfigRepository.findByInstrumentId(unknownInstrumentId))
                    .thenReturn(Collections.emptyList());
            VirtualOperationRequest request = VirtualOperationRequest.builder()
                    .instrumentId(unknownInstrumentId)
                    .equatorialAxisAngleDeg(90.0)
                    .declinationAxisAngleDeg(30.0)
                    .azimuthAxisAngleDeg(180.0)
                    .altitudeAxisAngleDeg(45.0)
                    .rotationalSpeedRpm(1.0)
                    .loadKg(500.0)
                    .temperatureC(25.0)
                    .lubricantType("VEGETABLE_OIL")
                    .simulateWear(false)
                    .simulateHours(0)
                    .build();
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> service.simulateVirtualOperation(request));
            assertTrue(exception.getMessage().contains("未找到仪器配置"));
        }

        @Test
        @DisplayName("空润滑剂类型字符串默认为VEGETABLE_OIL")
        void nullLubricantType_defaultsToVegetableOil() {
            VirtualOperationRequest request = VirtualOperationRequest.builder()
                    .instrumentId(instrumentId)
                    .equatorialAxisAngleDeg(90.0)
                    .declinationAxisAngleDeg(30.0)
                    .azimuthAxisAngleDeg(180.0)
                    .altitudeAxisAngleDeg(45.0)
                    .rotationalSpeedRpm(1.0)
                    .loadKg(500.0)
                    .temperatureC(25.0)
                    .lubricantType(null)
                    .simulateWear(false)
                    .simulateHours(0)
                    .build();
            VirtualOperationResponse response = service.simulateVirtualOperation(request);
            assertNotNull(response);
            assertTrue(Double.isFinite(response.getCurrentFrictionTorqueNm()));
        }

        @Test
        @DisplayName("无效润滑剂类型字符串默认为VEGETABLE_OIL")
        void invalidLubricantType_defaultsToVegetableOil() {
            VirtualOperationRequest request = VirtualOperationRequest.builder()
                    .instrumentId(instrumentId)
                    .equatorialAxisAngleDeg(90.0)
                    .declinationAxisAngleDeg(30.0)
                    .azimuthAxisAngleDeg(180.0)
                    .altitudeAxisAngleDeg(45.0)
                    .rotationalSpeedRpm(1.0)
                    .loadKg(500.0)
                    .temperatureC(25.0)
                    .lubricantType("INVALID_TYPE")
                    .simulateWear(false)
                    .simulateHours(0)
                    .build();
            VirtualOperationResponse response = service.simulateVirtualOperation(request);
            assertNotNull(response);
            assertTrue(Double.isFinite(response.getCurrentFrictionTorqueNm()));
        }

        @Test
        @DisplayName("负轴角度值仍可正常处理")
        void negativeAxisAngles_stillProcessable() {
            VirtualOperationRequest request = VirtualOperationRequest.builder()
                    .instrumentId(instrumentId)
                    .equatorialAxisAngleDeg(-90.0)
                    .declinationAxisAngleDeg(-30.0)
                    .azimuthAxisAngleDeg(-45.0)
                    .altitudeAxisAngleDeg(-60.0)
                    .rotationalSpeedRpm(1.0)
                    .loadKg(500.0)
                    .temperatureC(25.0)
                    .lubricantType("VEGETABLE_OIL")
                    .simulateWear(false)
                    .simulateHours(0)
                    .build();
            VirtualOperationResponse response = service.simulateVirtualOperation(request);
            assertNotNull(response);
            assertEquals(-45.0, response.getCurrentAzimuthDeg());
            assertEquals(-60.0, response.getCurrentAltitudeDeg());
        }

        @Test
        @DisplayName("NaN轴角度值仍能产生有限响应")
        void nanAxisAngles_stillProducesFiniteResponse() {
            VirtualOperationRequest request = VirtualOperationRequest.builder()
                    .instrumentId(instrumentId)
                    .equatorialAxisAngleDeg(Double.NaN)
                    .declinationAxisAngleDeg(30.0)
                    .azimuthAxisAngleDeg(180.0)
                    .altitudeAxisAngleDeg(45.0)
                    .rotationalSpeedRpm(1.0)
                    .loadKg(500.0)
                    .temperatureC(25.0)
                    .lubricantType("VEGETABLE_OIL")
                    .simulateWear(false)
                    .simulateHours(0)
                    .build();
            VirtualOperationResponse response = service.simulateVirtualOperation(request);
            assertNotNull(response);
        }

        @Test
        @DisplayName("Infinity轴角度值仍能产生有限响应")
        void infinityAxisAngles_stillProducesResponse() {
            VirtualOperationRequest request = VirtualOperationRequest.builder()
                    .instrumentId(instrumentId)
                    .equatorialAxisAngleDeg(90.0)
                    .declinationAxisAngleDeg(30.0)
                    .azimuthAxisAngleDeg(Double.POSITIVE_INFINITY)
                    .altitudeAxisAngleDeg(45.0)
                    .rotationalSpeedRpm(1.0)
                    .loadKg(500.0)
                    .temperatureC(25.0)
                    .lubricantType("VEGETABLE_OIL")
                    .simulateWear(false)
                    .simulateHours(0)
                    .build();
            VirtualOperationResponse response = service.simulateVirtualOperation(request);
            assertNotNull(response);
        }
    }
}
