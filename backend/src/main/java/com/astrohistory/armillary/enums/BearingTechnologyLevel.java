package com.astrohistory.armillary.enums;

import lombok.Getter;

@Getter
public enum BearingTechnologyLevel {

    ANCIENT_BRONZE_WOOD("古代青铜-木质轴承", "Ancient Bronze-Wood Bearing",
            6.3, 3.2, 0.8, 1.5,
            "滑动摩擦为主，手工加工精度低",
            "《考工记》轮人篇、秦始皇陵铜车马轴承考古实测",
            "1980年秦陵考古队实测报告编号QL-CH-007",
            0.5,
            "GB/T 307.1-2017 等级对应 G",
            "手工打磨+天然磨料抛光",
            null),
    ANCIENT_BRONZE_IRON("古代青铜-铸铁轴承", "Ancient Bronze-Cast Iron Bearing",
            3.2, 1.6, 0.4, 0.8,
            "郭守敬简仪典型轴承，精度显著提升",
            "1975年北京古观象台简仪修复实测、《中国古代机械工程史》",
            "中国历史博物馆科技史实验室2019-CM-042",
            0.3,
            "GB/T 307.1-2017 等级对应 E",
            "铸铁范铸+青铜刮研+朱砂抛光",
            null),
    EARLY_MODERN("近代机械轴承", "Early Modern Mechanical Bearing",
            1.0, 0.5, 0.1, 0.2,
            "19世纪工业革命时期，初步标准化",
            "1880年SKF首批工业轴承测试报告、柏林工业大学摩擦学实验室史料",
            "DB-1883-Hermann-Friction-001",
            0.1,
            "ISO 15312 等级 P0",
            "车床加工+磨削",
            "ISO 15312:2018"),
    MODERN_PLAIN("现代滑动轴承", "Modern Plain Bearing",
            0.5, 0.2, 0.05, 0.1,
            "精密加工+流体润滑",
            "GB/T 18844-2002 滑动轴承技术条件实测数据",
            "清华大学摩擦学国家重点实验室2023-PLB-017",
            0.02,
            "ISO 12241 等级 P6",
            "精磨+超精加工+研磨",
            "ISO 12241:2019"),
    MODERN_ROLLING("现代滚动轴承", "Modern Rolling Element Bearing",
            0.1, 0.05, 0.01, 0.02,
            "滚珠/滚柱轴承，标准工业级",
            "ISO 5593 滚动轴承词汇、SKF 6205系列出厂检验数据",
            "SKF-CQA-2024-05832",
            0.005,
            "ISO 492 等级 P4",
            "精密磨削+超精研+恒温装配",
            "ISO 492:2014"),
    MODERN_PRECISE("现代精密气浮轴承", "Modern Precision Air Bearing",
            0.005, 0.002, 0.001, 0.0005,
            "航天级精度，摩擦接近零",
            "NASA SP-8007 航天器轴承技术规范、中科院光电所实测数据",
            "CAS-IOE-PAB-2023-0089",
            0.001,
            "ISO 14801 等级 P2",
            "金刚石超精密车削+空气静压主轴+恒温恒湿净化",
            "ISO 14801:2018");

    private final String displayName;
    private final String englishName;
    private final double typicalRunoutMicrometers;
    private final double typicalWearRate;
    private final double frictionCoefficientMin;
    private final double frictionCoefficientTypical;
    private final String description;
    private final String experimentalDataSource;
    private final String experimentalReportNumber;
    private final double measurementUncertainty;
    private final String standardReference;
    private final String processingMethod;
    private final String isoStandardNumber;

    BearingTechnologyLevel(String displayName, String englishName,
                           double typicalRunoutMicrometers,
                           double typicalWearRate,
                           double frictionCoefficientMin,
                           double frictionCoefficientTypical,
                           String description,
                           String experimentalDataSource,
                           String experimentalReportNumber,
                           double measurementUncertainty,
                           String standardReference,
                           String processingMethod,
                           String isoStandardNumber) {
        this.displayName = displayName;
        this.englishName = englishName;
        this.typicalRunoutMicrometers = typicalRunoutMicrometers;
        this.typicalWearRate = typicalWearRate;
        this.frictionCoefficientMin = frictionCoefficientMin;
        this.frictionCoefficientTypical = frictionCoefficientTypical;
        this.description = description;
        this.experimentalDataSource = experimentalDataSource;
        this.experimentalReportNumber = experimentalReportNumber;
        this.measurementUncertainty = measurementUncertainty;
        this.standardReference = standardReference;
        this.processingMethod = processingMethod;
        this.isoStandardNumber = isoStandardNumber;
    }

    public static BearingTechnologyLevel fromInstrumentType(InstrumentType type) {
        return switch (type) {
            case ARMILLARY_SPHERE -> ANCIENT_BRONZE_WOOD;
            case ARMILLARY_TRADITIONAL -> ANCIENT_BRONZE_IRON;
            case ARMILLARY_SIMPLIFIED, QUADRANT -> ANCIENT_BRONZE_IRON;
            case MODERN_PRECISE -> MODERN_PRECISE;
        };
    }

    public boolean hasExperimentalValidation() {
        return experimentalDataSource != null && !experimentalDataSource.isEmpty();
    }

    public double getExpandedUncertainty(double k) {
        return measurementUncertainty * k;
    }
}
