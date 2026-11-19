-- CN_PROVINCE 省级行政区公共字典（MySQL 8.0）
-- 版本：1.0.0（2026-11-19）
-- 可重复执行；业务代码只使用 province_code。

SET NAMES utf8mb4;

INSERT INTO sys_dict_type
    (dict_code, dict_name, remark, status, created_by, deleted, version)
SELECT 'CN_PROVINCE', '中国省级行政区', '学校和省级赛事的省级行政区编码', 1, 0, 0, 0
WHERE NOT EXISTS (
    SELECT 1 FROM sys_dict_type WHERE dict_code = 'CN_PROVINCE' AND deleted = 0
);

SET @province_dict_type_id = (
    SELECT id FROM sys_dict_type WHERE dict_code = 'CN_PROVINCE' AND deleted = 0 ORDER BY id LIMIT 1
);

INSERT INTO sys_dict_item
    (dict_type_id, item_label, item_value, sort_no, status, created_by, deleted, version)
SELECT @province_dict_type_id, p.province_name, p.province_code, p.sort_no, 1, 0, 0, 0
FROM (
    SELECT '110000' province_code, '北京市' province_name, 10 sort_no
    UNION ALL SELECT '120000', '天津市', 20
    UNION ALL SELECT '130000', '河北省', 30
    UNION ALL SELECT '140000', '山西省', 40
    UNION ALL SELECT '150000', '内蒙古自治区', 50
    UNION ALL SELECT '210000', '辽宁省', 60
    UNION ALL SELECT '220000', '吉林省', 70
    UNION ALL SELECT '230000', '黑龙江省', 80
    UNION ALL SELECT '310000', '上海市', 90
    UNION ALL SELECT '320000', '江苏省', 100
    UNION ALL SELECT '330000', '浙江省', 110
    UNION ALL SELECT '340000', '安徽省', 120
    UNION ALL SELECT '350000', '福建省', 130
    UNION ALL SELECT '360000', '江西省', 140
    UNION ALL SELECT '370000', '山东省', 150
    UNION ALL SELECT '410000', '河南省', 160
    UNION ALL SELECT '420000', '湖北省', 170
    UNION ALL SELECT '430000', '湖南省', 180
    UNION ALL SELECT '440000', '广东省', 190
    UNION ALL SELECT '450000', '广西壮族自治区', 200
    UNION ALL SELECT '460000', '海南省', 210
    UNION ALL SELECT '500000', '重庆市', 220
    UNION ALL SELECT '510000', '四川省', 230
    UNION ALL SELECT '520000', '贵州省', 240
    UNION ALL SELECT '530000', '云南省', 250
    UNION ALL SELECT '540000', '西藏自治区', 260
    UNION ALL SELECT '610000', '陕西省', 270
    UNION ALL SELECT '620000', '甘肃省', 280
    UNION ALL SELECT '630000', '青海省', 290
    UNION ALL SELECT '640000', '宁夏回族自治区', 300
    UNION ALL SELECT '650000', '新疆维吾尔自治区', 310
    UNION ALL SELECT '710000', '台湾省', 320
    UNION ALL SELECT '810000', '香港特别行政区', 330
    UNION ALL SELECT '820000', '澳门特别行政区', 340
) p
WHERE NOT EXISTS (
    SELECT 1
    FROM sys_dict_item i
    WHERE i.dict_type_id = @province_dict_type_id
      AND i.item_value = p.province_code
      AND i.deleted = 0
);
