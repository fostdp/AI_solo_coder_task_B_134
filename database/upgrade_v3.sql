-- =====================================================================
-- 古代天文仪器摩擦仿真系统 V3.0 升级脚本
-- 新增功能: 多仪器对比、跨时代摩擦学对比、润滑剂对比、公众虚拟操作
-- 执行前请备份数据库
-- =====================================================================

-- 1. 扩展 armillary_instruments 表
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_name = 'armillary_instruments' AND column_name = 'instrument_type') THEN
        ALTER TABLE armillary_instruments
            ADD COLUMN instrument_type VARCHAR(50) DEFAULT 'ARMILLARY_SIMPLIFIED',
            ADD COLUMN latitude_deg NUMERIC(8, 4) DEFAULT 39.9042;
    END IF;
END $$;

-- 2. 扩展 bearing_config 表
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_name = 'bearing_config' AND column_name = 'lubricant_type') THEN
        ALTER TABLE bearing_config
            ADD COLUMN lubricant_type VARCHAR(50) DEFAULT 'VEGETABLE_OIL',
            ADD COLUMN technology_level VARCHAR(50) DEFAULT 'ANCIENT_BRONZE_IRON';
    END IF;
END $$;

-- 3. 更新现有数据
UPDATE armillary_instruments
SET instrument_type = 'ARMILLARY_SIMPLIFIED'
WHERE instrument_type IS NULL;

UPDATE bearing_config
SET lubricant_type = 'VEGETABLE_OIL'
WHERE lubricant_type IS NULL;

UPDATE bearing_config
SET technology_level = 'ANCIENT_BRONZE_IRON'
WHERE technology_level IS NULL;

-- 4. 新增索引
CREATE INDEX IF NOT EXISTS idx_instruments_type ON armillary_instruments(instrument_type);
CREATE INDEX IF NOT EXISTS idx_bearing_config_lubricant ON bearing_config(lubricant_type);
CREATE INDEX IF NOT EXISTS idx_bearing_config_tech ON bearing_config(technology_level);

-- 5. 插入多种古代天文仪器测试数据
INSERT INTO armillary_instruments (id, name, model, location, build_year, description, status, instrument_type, latitude_deg)
VALUES
    ('00000000-0000-0000-0000-000000000001',
     '元代郭守敬简仪', 'Simplified Armillary', '北京古观象台', 1279,
     '郭守敬创制，结构简化，赤道装置，精度最高的古代天文仪器之一',
     'ACTIVE', 'ARMILLARY_SIMPLIFIED', 39.9042)
ON CONFLICT (id) DO NOTHING;

INSERT INTO armillary_instruments (id, name, model, location, build_year, description, status, instrument_type, latitude_deg)
VALUES
    ('00000000-0000-0000-0000-000000000002',
     '汉代张衡浑仪', 'Traditional Armillary Sphere', '洛阳灵台', 117,
     '张衡创制，多重同心环圈结构，水运驱动自动演示天体运行',
     'ACTIVE', 'ARMILLARY_SPHERE', 34.6197)
ON CONFLICT (id) DO NOTHING;

INSERT INTO armillary_instruments (id, name, model, location, build_year, description, status, instrument_type, latitude_deg)
VALUES
    ('00000000-0000-0000-0000-000000000003',
     '清代象限仪', 'Mural Quadrant', '北京古观象台', 1673,
     '南怀仁监制，地平坐标系测量，单臂90度象限弧',
     'ACTIVE', 'QUADRANT', 39.9042)
ON CONFLICT (id) DO NOTHING;

INSERT INTO armillary_instruments (id, name, model, location, build_year, description, status, instrument_type, latitude_deg)
VALUES
    ('00000000-0000-0000-0000-000000000004',
     '宋代水运仪象台浑仪', 'Water-powered Armillary', '开封', 1092,
     '苏颂、韩公廉创制，集观测、演示、报时于一体的大型天文仪器',
     'ACTIVE', 'ARMILLARY_TRADITIONAL', 34.7971)
ON CONFLICT (id) DO NOTHING;

INSERT INTO armillary_instruments (id, name, model, location, build_year, description, status, instrument_type, latitude_deg)
VALUES
    ('00000000-0000-0000-0000-000000000005',
     '现代精密轴承基准', 'Modern Precision Bearing Reference', '实验室', 2024,
     '现代精密轴系对比基准，用于与古代仪器进行跨时代性能对比',
     'ACTIVE', 'MODERN_PRECISE', 39.9042)
ON CONFLICT (id) DO NOTHING;

-- 6. 插入汉代浑仪轴承配置 (7轴)
INSERT INTO bearing_config (id, instrument_id, axis_name, axis_type, bearing_type, material,
    inner_ring_material, outer_ring_material, rolling_element_material, surface_roughness_ra,
    perpendicularity_error, axial_runout, radial_runout,
    inner_diameter, outer_diameter, width, initial_clearance,
    lubricant_viscosity, elastic_modulus, poisson_ratio, hardness, wear_coefficient, max_allowable_wear,
    lubricant_type, technology_level)
VALUES
    ('10000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000002',
     '子午环轴', 'EQUATORIAL', '滑动轴承', '青铜-木材', '青铜', '硬木', NULL, 3.2,
     0.0015, 0.0008, 0.0012,
     180.0, 240.0, 60.0, 0.08, 0.045, 110000.0, 0.34, 180.0, 2.0e-8, 0.5,
     'ANIMAL_FAT', 'ANCIENT_BRONZE_WOOD'),
    ('10000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000002',
     '赤道环轴', 'EQUATORIAL', '滑动轴承', '青铜-木材', '青铜', '硬木', NULL, 3.5,
     0.0018, 0.0010, 0.0015,
     200.0, 260.0, 55.0, 0.10, 0.045, 110000.0, 0.34, 180.0, 2.0e-8, 0.5,
     'ANIMAL_FAT', 'ANCIENT_BRONZE_WOOD'),
    ('10000000-0000-0000-0000-000000000003', '00000000-0000-0000-0000-000000000002',
     '黄道环轴', 'ECLIPTIC', '滑动轴承', '青铜-木材', '青铜', '硬木', NULL, 4.0,
     0.0020, 0.0012, 0.0018,
     150.0, 210.0, 45.0, 0.07, 0.045, 110000.0, 0.34, 180.0, 2.5e-8, 0.5,
     'ANIMAL_FAT', 'ANCIENT_BRONZE_WOOD')
ON CONFLICT (id) DO NOTHING;

-- 7. 插入象限仪轴承配置 (1轴)
INSERT INTO bearing_config (id, instrument_id, axis_name, axis_type, bearing_type, material,
    inner_ring_material, outer_ring_material, rolling_element_material, surface_roughness_ra,
    perpendicularity_error, axial_runout, radial_runout,
    inner_diameter, outer_diameter, width, initial_clearance,
    lubricant_viscosity, elastic_modulus, poisson_ratio, hardness, wear_coefficient, max_allowable_wear,
    lubricant_type, technology_level)
VALUES
    ('20000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000003',
     '水平旋转轴', 'AZIMUTH', '滑动轴承', '青铜-铸铁', '锡青铜', '灰铸铁', NULL, 1.6,
     0.0005, 0.0003, 0.0005,
     300.0, 400.0, 100.0, 0.05, 0.035, 120000.0, 0.31, 220.0, 4.5e-9, 0.3,
     'VEGETABLE_OIL', 'ANCIENT_BRONZE_IRON')
ON CONFLICT (id) DO NOTHING;

-- 8. 插入宋代水运仪象台轴承配置 (6轴)
INSERT INTO bearing_config (id, instrument_id, axis_name, axis_type, bearing_type, material,
    inner_ring_material, outer_ring_material, rolling_element_material, surface_roughness_ra,
    perpendicularity_error, axial_runout, radial_runout,
    inner_diameter, outer_diameter, width, initial_clearance,
    lubricant_viscosity, elastic_modulus, poisson_ratio, hardness, wear_coefficient, max_allowable_wear,
    lubricant_type, technology_level)
VALUES
    ('30000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000004',
     '浑仪赤道轴', 'EQUATORIAL', '滑动轴承', '青铜-铸铁', '锡青铜', '灰铸铁', NULL, 2.0,
     0.0008, 0.0004, 0.0006,
     160.0, 220.0, 55.0, 0.05, 0.035, 120000.0, 0.31, 200.0, 5.0e-9, 0.3,
     'VEGETABLE_OIL', 'ANCIENT_BRONZE_IRON'),
    ('30000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000004',
     '水运驱动轴', 'DRIVE', '滑动轴承', '青铜-铸铁', '黄铜', '铸铁', NULL, 2.5,
     0.0010, 0.0006, 0.0008,
     120.0, 170.0, 45.0, 0.06, 0.035, 110000.0, 0.34, 150.0, 6.0e-9, 0.4,
     'VEGETABLE_OIL', 'ANCIENT_BRONZE_IRON')
ON CONFLICT (id) DO NOTHING;

-- 9. 插入现代精密轴承基准 (3轴)
INSERT INTO bearing_config (id, instrument_id, axis_name, axis_type, bearing_type, material,
    inner_ring_material, outer_ring_material, rolling_element_material, surface_roughness_ra,
    perpendicularity_error, axial_runout, radial_runout,
    inner_diameter, outer_diameter, width, initial_clearance,
    lubricant_viscosity, elastic_modulus, poisson_ratio, hardness, wear_coefficient, max_allowable_wear,
    lubricant_type, technology_level)
VALUES
    ('40000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000005',
     '方位轴', 'AZIMUTH', '气浮轴承', '陶瓷-合金', '氮化硅陶瓷', '殷钢合金', '红宝石球', 0.01,
     0.000001, 0.0000005, 0.000001,
     100.0, 150.0, 40.0, 0.002, 0.015, 310000.0, 0.27, 1800.0, 1.0e-12, 0.05,
     'MODERN_SYNTHETIC', 'MODERN_PRECISE'),
    ('40000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000005',
     '俯仰轴', 'ALTITUDE', '气浮轴承', '陶瓷-合金', '氮化硅陶瓷', '殷钢合金', '红宝石球', 0.01,
     0.000001, 0.0000005, 0.000001,
     80.0, 120.0, 35.0, 0.002, 0.015, 310000.0, 0.27, 1800.0, 1.0e-12, 0.05,
     'MODERN_SYNTHETIC', 'MODERN_PRECISE'),
    ('40000000-0000-0000-0000-000000000003', '00000000-0000-0000-0000-000000000005',
     '赤经轴', 'EQUATORIAL', '滚动轴承', '轴承钢', 'GCr15', 'GCr15', 'Si3N4陶瓷球', 0.02,
     0.000002, 0.000001, 0.000002,
     70.0, 110.0, 25.0, 0.005, 0.015, 210000.0, 0.30, 850.0, 1.0e-11, 0.02,
     'MODERN_SYNTHETIC', 'MODERN_ROLLING')
ON CONFLICT (id) DO NOTHING;

-- 10. 润滑剂参数参考表 (视图)
CREATE OR REPLACE VIEW lubricant_reference AS
SELECT
    'ANIMAL_FAT' AS lubricant_code,
    '动物油' AS lubricant_name,
    0.045 AS viscosity_40c,
    0.03 AS viscosity_100c,
    38.0 AS flash_point_c,
    '古代常用，如猪油、牛油，低温易凝固' AS description,
    TRUE AS historically_available
UNION ALL
SELECT
    'VEGETABLE_OIL' AS lubricant_code,
    '植物油' AS lubricant_name,
    0.035 AS viscosity_40c,
    0.028 AS viscosity_100c,
    25.0 AS flash_point_c,
    '芝麻油、桐油，润滑性好但易氧化' AS description,
    TRUE AS historically_available
UNION ALL
SELECT
    'MINERAL_OIL' AS lubricant_code,
    '矿物油' AS lubricant_name,
    0.025 AS viscosity_40c,
    0.02 AS viscosity_100c,
    45.0 AS flash_point_c,
    '近现代石油基润滑油，性能稳定' AS description,
    FALSE AS historically_available
UNION ALL
SELECT
    'MODERN_SYNTHETIC' AS lubricant_code,
    '现代合成油' AS lubricant_name,
    0.015 AS viscosity_40c,
    0.01 AS viscosity_100c,
    60.0 AS flash_point_c,
    'PAO合成油、酯类油，现代精密轴承标准' AS description,
    FALSE AS historically_available
UNION ALL
SELECT
    'MERCURY' AS lubricant_code,
    '水银悬浮' AS lubricant_name,
    0.001 AS viscosity_40c,
    0.0008 AS viscosity_100c,
    20.0 AS flash_point_c,
    '古代特殊工艺，低摩擦但有毒' AS description,
    TRUE AS historically_available
UNION ALL
SELECT
    'DRY' AS lubricant_code,
    '无润滑/干摩擦' AS lubricant_name,
    0.5 AS viscosity_40c,
    0.3 AS viscosity_100c,
    15.0 AS flash_point_c,
    '金属直接接触，磨损极快' AS description,
    TRUE AS historically_available;

-- 11. 轴承技术等级参考视图
CREATE OR REPLACE VIEW bearing_technology_reference AS
SELECT
    'ANCIENT_BRONZE_WOOD' AS tech_code,
    '古代青铜-木质轴承' AS tech_name,
    -200 AS era_start_year,
    1000 AS era_end_year,
    6.3 AS typical_runout_micrometers,
    3.2 AS typical_wear_rate,
    0.8 AS friction_coeff_min,
    1.5 AS friction_coeff_typical,
    '滑动摩擦为主，手工加工精度低' AS description
UNION ALL
SELECT
    'ANCIENT_BRONZE_IRON' AS tech_code,
    '古代青铜-铸铁轴承' AS tech_name,
    1000 AS era_start_year,
    1800 AS era_end_year,
    3.2 AS typical_runout_micrometers,
    1.6 AS typical_wear_rate,
    0.4 AS friction_coeff_min,
    0.8 AS friction_coeff_typical,
    '郭守敬简仪典型轴承，精度显著提升' AS description
UNION ALL
SELECT
    'EARLY_MODERN' AS tech_code,
    '近代机械轴承' AS tech_name,
    1800 AS era_start_year,
    1900 AS era_end_year,
    1.0 AS typical_runout_micrometers,
    0.5 AS typical_wear_rate,
    0.1 AS friction_coeff_min,
    0.2 AS friction_coeff_typical,
    '19世纪工业革命时期，初步标准化' AS description
UNION ALL
SELECT
    'MODERN_PLAIN' AS tech_code,
    '现代滑动轴承' AS tech_name,
    1900 AS era_start_year,
    1950 AS era_end_year,
    0.5 AS typical_runout_micrometers,
    0.2 AS typical_wear_rate,
    0.05 AS friction_coeff_min,
    0.1 AS friction_coeff_typical,
    '精密加工+流体润滑' AS description
UNION ALL
SELECT
    'MODERN_ROLLING' AS tech_code,
    '现代滚动轴承' AS tech_name,
    1950 AS era_start_year,
    2000 AS era_end_year,
    0.1 AS typical_runout_micrometers,
    0.05 AS typical_wear_rate,
    0.01 AS friction_coeff_min,
    0.02 AS friction_coeff_typical,
    '滚珠/滚柱轴承，标准工业级' AS description
UNION ALL
SELECT
    'MODERN_PRECISE' AS tech_code,
    '现代精密气浮轴承' AS tech_name,
    2000 AS era_start_year,
    2100 AS era_end_year,
    0.005 AS typical_runout_micrometers,
    0.002 AS typical_wear_rate,
    0.001 AS friction_coeff_min,
    0.0005 AS friction_coeff_typical,
    '航天级精度，摩擦接近零' AS description;
