package com.astrohistory.armillary.integration;

import com.astrohistory.armillary.analyzer.LubricantAnalyzer;
import com.astrohistory.armillary.comparator.BearingComparator;
import com.astrohistory.armillary.comparator.EraComparator;
import com.astrohistory.armillary.dto.CrossEraComparisonDTO;
import com.astrohistory.armillary.dto.InstrumentComparisonDTO;
import com.astrohistory.armillary.dto.LubricantComparisonDTO;
import com.astrohistory.armillary.dto.VirtualOperationRequest;
import com.astrohistory.armillary.dto.VirtualOperationResponse;
import com.astrohistory.armillary.entity.ArmillaryInstrument;
import com.astrohistory.armillary.entity.BearingConfig;
import com.astrohistory.armillary.enums.InstrumentType;
import com.astrohistory.armillary.enums.LubricantType;
import com.astrohistory.armillary.repository.BearingConfigRepository;
import com.astrohistory.armillary.repository.FrictionSimulationRepository;
import com.astrohistory.armillary.repository.SensorDataRepository;
import com.astrohistory.armillary.simulation.BearingFrictionModel;
import com.astrohistory.armillary.simulation.SimulationExecutor;
import com.astrohistory.armillary.vr.VrArmillaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@DisplayName("模块重构回归测试")
@ExtendWith(MockitoExtension.class)
class ModuleRefactoringRegressionTest {

    private BearingFrictionModel frictionModel;
    private SimulationExecutor simulationExecutor;

    @Mock
    private BearingConfigRepository bearingConfigRepository;

    @Mock
    private SensorDataRepository sensorDataRepository;

    @Mock
    private FrictionSimulationRepository frictionSimulationRepository;

    private BearingComparator bearingComparator;
    private EraComparator eraComparator;
    private LubricantAnalyzer lubricantAnalyzer;
    private VrArmillaService vrArmillaService;

    private UUID instrumentId;
    private ArmillaryInstrument instrument;
    private BearingConfig config;

    @BeforeEach
    void setUp() {
        frictionModel = new BearingFrictionModel();
        simulationExecutor = new SimulationExecutor();

        bearingComparator = new BearingComparator(
                frictionModel, bearingConfigRepository, sensorDataRepository,
                frictionSimulationRepository, simulationExecutor);
        eraComparator = new EraComparator(
                frictionModel, bearingConfigRepository, sensorDataRepository,
                simulationExecutor);
        lubricantAnalyzer = new LubricantAnalyzer(
                frictionModel, bearingConfigRepository, sensorDataRepository,
                frictionSimulationRepository, simulationExecutor);
        vrArmillaService = new VrArmillaService(
                frictionModel, bearingConfigRepository, simulationExecutor);

        instrumentId = UUID.randomUUID();
        instrument = ArmillaryInstrument.builder()
                .id(instrumentId).name("测试简仪").model("JY-001")
                .location("北京").buildYear(1279).build();

        config = BearingConfig.builder()
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

        when(bearingConfigRepository.findByInstrumentIdAndAxisName(instrumentId, "赤道轴"))
                .thenReturn(Optional.of(config));
        when(bearingConfigRepository.findByInstrumentIdAndAxisNameOrderByIdDesc(instrumentId, "赤道轴"))
                .thenReturn(List.of(config));
        when(bearingConfigRepository.findByInstrumentId(instrumentId))
                .thenReturn(List.of(config, config, config, config));
        when(sensorDataRepository.findTopByInstrumentIdAndAxisNameOrderByTimestampDesc(instrumentId, "赤道轴"))
                .thenReturn(Optional.empty());
        when(frictionSimulationRepository.findTopByInstrumentIdAndAxisNameOrderBySimulationTimeDesc(instrumentId, "赤道轴"))
                .thenReturn(Optional.empty());
    }

    @Nested
    @DisplayName("BearingComparator回归测试")
    class BearingComparatorRegressionTests {

        @Test
        @DisplayName("compareInstrumentBearings返回5个结果（每个InstrumentType一个）")
        void compareInstrumentBearingsReturns5Results() {
            List<InstrumentComparisonDTO> results = bearingComparator
                    .compareInstrumentBearings(instrumentId, "赤道轴").join();

            assertEquals(5, results.size(),
                    "应有5个InstrumentType对应5个结果");
        }

        @Test
        @DisplayName("每个结果都有非空的frictionMetrics和wearMetrics")
        void eachResultHasNonNullMetrics() {
            List<InstrumentComparisonDTO> results = bearingComparator
                    .compareInstrumentBearings(instrumentId, "赤道轴").join();

            for (InstrumentComparisonDTO result : results) {
                assertNotNull(result.getFrictionMetrics(),
                        result.getInstrumentType() + " 的 frictionMetrics 不应为null");
                assertNotNull(result.getWearMetrics(),
                        result.getInstrumentType() + " 的 wearMetrics 不应为null");
            }
        }

        @Test
        @DisplayName("QUADRANT具有最高磨损率")
        void quadrantHasHighestWearRate() {
            List<InstrumentComparisonDTO> results = bearingComparator
                    .compareInstrumentBearings(instrumentId, "赤道轴").join();

            InstrumentComparisonDTO quadrantResult = results.stream()
                    .filter(r -> r.getInstrumentType() == InstrumentType.QUADRANT)
                    .findFirst().orElseThrow();

            for (InstrumentComparisonDTO result : results) {
                if (result.getInstrumentType() != InstrumentType.QUADRANT) {
                    assertTrue(
                            quadrantResult.getWearMetrics().getWearRateMPerSec() >=
                                    result.getWearMetrics().getWearRateMPerSec(),
                            "QUADRANT磨损率应不低于" + result.getInstrumentType()
                                    + "，QUADRANT=" + quadrantResult.getWearMetrics().getWearRateMPerSec()
                                    + "，" + result.getInstrumentType() + "=" + result.getWearMetrics().getWearRateMPerSec());
                }
            }
        }

        @Test
        @DisplayName("MODERN_PRECISE具有最低摩擦系数")
        void modernPreciseHasLowestFrictionCoefficient() {
            List<InstrumentComparisonDTO> results = bearingComparator
                    .compareInstrumentBearings(instrumentId, "赤道轴").join();

            InstrumentComparisonDTO modernResult = results.stream()
                    .filter(r -> r.getInstrumentType() == InstrumentType.MODERN_PRECISE)
                    .findFirst().orElseThrow();

            for (InstrumentComparisonDTO result : results) {
                if (result.getInstrumentType() != InstrumentType.MODERN_PRECISE) {
                    assertTrue(
                            modernResult.getFrictionMetrics().getFrictionCoefficient() <=
                                    result.getFrictionMetrics().getFrictionCoefficient(),
                            "MODERN_PRECISE摩擦系数应不高于" + result.getInstrumentType()
                                    + "，MODERN_PRECISE=" + modernResult.getFrictionMetrics().getFrictionCoefficient()
                                    + "，" + result.getInstrumentType() + "=" + result.getFrictionMetrics().getFrictionCoefficient());
                }
            }
        }
    }

    @Nested
    @DisplayName("EraComparator回归测试")
    class EraComparatorRegressionTests {

        @Test
        @DisplayName("compareAcrossEras返回6个时代数据")
        void compareAcrossErasReturns6EraDataEntries() {
            CrossEraComparisonDTO result = eraComparator
                    .compareAcrossEras(instrumentId, "赤道轴");

            assertEquals(6, result.getEraData().size(),
                    "应有6个BearingTechnologyLevel对应6个时代数据");
        }

        @Test
        @DisplayName("Summary的改进比率均大于1.0")
        void summaryImprovementRatiosGreaterThan1() {
            CrossEraComparisonDTO result = eraComparator
                    .compareAcrossEras(instrumentId, "赤道轴");

            CrossEraComparisonDTO.SummaryComparison summary = result.getSummary();

            assertTrue(summary.getFrictionCoefficientImprovementRatio() > 1.0,
                    "摩擦系数改进比率应大于1.0，实际: "
                            + summary.getFrictionCoefficientImprovementRatio());
            assertTrue(summary.getWearRateImprovementRatio() > 1.0,
                    "磨损率改进比率应大于1.0，实际: "
                            + summary.getWearRateImprovementRatio());
            assertTrue(summary.getPrecisionImprovementRatio() > 1.0,
                    "精度改进比率应大于1.0，实际: "
                            + summary.getPrecisionImprovementRatio());
        }

        @Test
        @DisplayName("Insights列表不为空")
        void insightsListIsNotEmpty() {
            CrossEraComparisonDTO result = eraComparator
                    .compareAcrossEras(instrumentId, "赤道轴");

            assertFalse(result.getInsights().isEmpty(),
                    "洞察列表不应为空");
        }

        @Test
        @DisplayName("摩擦系数从ANCIENT到MODERN递减")
        void frictionCoefficientDecreasesFromAncientToModern() {
            CrossEraComparisonDTO result = eraComparator
                    .compareAcrossEras(instrumentId, "赤道轴");

            List<CrossEraComparisonDTO.EraBearingData> eraData = result.getEraData();

            for (int i = 1; i < eraData.size(); i++) {
                double prev = eraData.get(i - 1).getTypicalFrictionCoefficient();
                double curr = eraData.get(i).getTypicalFrictionCoefficient();

                assertTrue(curr <= prev,
                        "摩擦系数应递减：" + eraData.get(i - 1).getEraName()
                                + "=" + prev + "，" + eraData.get(i).getEraName()
                                + "=" + curr);
            }
        }
    }

    @Nested
    @DisplayName("LubricantAnalyzer回归测试")
    class LubricantAnalyzerRegressionTests {

        @Test
        @DisplayName("compareLubricants返回6个润滑剂数据点")
        void compareLubricantsReturns6DataPoints() {
            LubricantComparisonDTO result = lubricantAnalyzer
                    .compareLubricants(instrumentId, "赤道轴");

            assertEquals(6, result.getLubricantData().size(),
                    "应有6个LubricantType对应6个数据点");
        }

        @Test
        @DisplayName("DRY具有最高磨损率")
        void dryHasHighestWearRate() {
            LubricantComparisonDTO result = lubricantAnalyzer
                    .compareLubricants(instrumentId, "赤道轴");

            LubricantComparisonDTO.LubricantDataPoint dryData = result.getLubricantData().stream()
                    .filter(d -> d.getLubricantType() == LubricantType.DRY)
                    .findFirst().orElseThrow();

            for (LubricantComparisonDTO.LubricantDataPoint dataPoint : result.getLubricantData()) {
                if (dataPoint.getLubricantType() != LubricantType.DRY) {
                    assertTrue(
                            dryData.getWearRateMPerSec() >= dataPoint.getWearRateMPerSec(),
                            "DRY磨损率应不低于" + dataPoint.getLubricantType().getDisplayName()
                                    + "，DRY=" + dryData.getWearRateMPerSec()
                                    + "，" + dataPoint.getLubricantType().getDisplayName()
                                    + "=" + dataPoint.getWearRateMPerSec());
                }
            }
        }

        @Test
        @DisplayName("RankingSummary有4个排名Map")
        void rankingSummaryHas4RankingMaps() {
            LubricantComparisonDTO result = lubricantAnalyzer
                    .compareLubricants(instrumentId, "赤道轴");

            LubricantComparisonDTO.RankingSummary ranking = result.getRanking();

            assertNotNull(ranking.getByWearResistance(), "byWearResistance不应为null");
            assertNotNull(ranking.getByFrictionReduction(), "byFrictionReduction不应为null");
            assertNotNull(ranking.getByHistoricalPracticality(), "byHistoricalPracticality不应为null");
            assertNotNull(ranking.getByOverallScore(), "byOverallScore不应为null");

            assertFalse(ranking.getByWearResistance().isEmpty(), "byWearResistance不应为空");
            assertFalse(ranking.getByFrictionReduction().isEmpty(), "byFrictionReduction不应为空");
            assertFalse(ranking.getByHistoricalPracticality().isEmpty(), "byHistoricalPracticality不应为空");
            assertFalse(ranking.getByOverallScore().isEmpty(), "byOverallScore不应为空");
        }

        @Test
        @DisplayName("历史注释列表不为空")
        void historicalNotesListIsNotEmpty() {
            LubricantComparisonDTO result = lubricantAnalyzer
                    .compareLubricants(instrumentId, "赤道轴");

            assertFalse(result.getHistoricalNotes().isEmpty(),
                    "历史注释列表不应为空");
        }
    }

    @Nested
    @DisplayName("VrArmillaService回归测试")
    class VrArmillaServiceRegressionTests {

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

        @Test
        @DisplayName("simulateVirtualOperation返回有效响应包含坐标")
        void simulateVirtualOperationReturnsValidResponseWithCoordinates() {
            VirtualOperationResponse response = vrArmillaService
                    .simulateVirtualOperation(buildBasicRequest());

            assertNotNull(response);
            assertTrue(Double.isFinite(response.getCurrentAzimuthDeg()),
                    "方位角应为有限值");
            assertTrue(Double.isFinite(response.getCurrentAltitudeDeg()),
                    "高度角应为有限值");
            assertTrue(Double.isFinite(response.getCurrentRightAscensionDeg()),
                    "赤经应为有限值");
            assertTrue(Double.isFinite(response.getCurrentDeclinationDeg()),
                    "赤纬应为有限值");
        }

        @Test
        @DisplayName("响应包含力反馈字段")
        void responseContainsForceFeedbackFields() {
            VirtualOperationResponse response = vrArmillaService
                    .simulateVirtualOperation(buildBasicRequest());

            assertNotNull(response.getOperationTorqueRequiredNm(),
                    "operationTorqueRequiredNm不应为null");
            assertNotNull(response.getInertiaResistanceNm(),
                    "inertiaResistanceNm不应为null");

            assertTrue(response.getOperationTorqueRequiredNm() > 0,
                    "操作扭矩应为正");
            assertTrue(response.getInertiaResistanceNm() >= 0,
                    "惯性阻力应非负");
        }

        @Test
        @DisplayName("targetStarName不为null")
        void targetStarNameIsNotNull() {
            VirtualOperationResponse response = vrArmillaService
                    .simulateVirtualOperation(buildBasicRequest());

            assertNotNull(response.getTargetStarName(),
                    "目标星名不应为null");
            assertFalse(response.getTargetStarName().isBlank(),
                    "目标星名不应为空白");
        }

        @Test
        @DisplayName("stressLevel在1到5之间")
        void stressLevelBetween1And5() {
            VirtualOperationResponse response = vrArmillaService
                    .simulateVirtualOperation(buildBasicRequest());

            assertNotNull(response.getStressLevel(), "应力等级不应为null");
            assertTrue(response.getStressLevel() >= 1 && response.getStressLevel() <= 5,
                    "应力等级应在1到5之间，实际: " + response.getStressLevel());
        }

        @Test
        @DisplayName("axisPositions有4个条目")
        void axisPositionsHas4Entries() {
            VirtualOperationResponse response = vrArmillaService
                    .simulateVirtualOperation(buildBasicRequest());

            assertNotNull(response.getAxisPositions(), "轴位置不应为null");
            assertEquals(4, response.getAxisPositions().size(),
                    "应有4个轴位置条目（赤道、赤纬、方位、高度）");

            assertTrue(response.getAxisPositions().containsKey("equatorial"),
                    "应包含equatorial轴");
            assertTrue(response.getAxisPositions().containsKey("declination"),
                    "应包含declination轴");
            assertTrue(response.getAxisPositions().containsKey("azimuth"),
                    "应包含azimuth轴");
            assertTrue(response.getAxisPositions().containsKey("altitude"),
                    "应包含altitude轴");
        }
    }
}
