-- 竞赛学科分类公共基线（MySQL 8.0）
-- 版本：1.0.0（2026-11-28）
-- 前置：已执行 2026-11-15-business-core-foundation.sql
-- 说明：分类存 contest.category，空值表示未分类；未分类不参与相似竞赛推荐，但不影响原有业务。

SET NAMES utf8mb4;
SET time_zone = '+08:00';

-- 1. contest 主档新增学科分类字段
ALTER TABLE contest
  ADD COLUMN category VARCHAR(32) NULL
    COMMENT '竞赛学科分类，对应 sys_dict_item.item_value，字典编码 CONTEST_CATEGORY；NULL 表示未分类'
    AFTER level,
  ADD KEY idx_contest_category (category);

-- 2. 竞赛学科分类字典类型（可重复执行）
INSERT INTO sys_dict_type
    (dict_code, dict_name, remark, status, created_by, deleted, version)
SELECT 'CONTEST_CATEGORY', '竞赛学科分类', '用于跨竞赛系列的相似竞赛推荐', 1, 0, 0, 0
WHERE NOT EXISTS (
    SELECT 1 FROM sys_dict_type WHERE dict_code = 'CONTEST_CATEGORY' AND deleted = 0
);

SET @contest_category_dict_type_id = (
    SELECT id FROM sys_dict_type WHERE dict_code = 'CONTEST_CATEGORY' AND deleted = 0 ORDER BY id LIMIT 1
);

-- 3. 四个初始启用分类
INSERT INTO sys_dict_item
    (dict_type_id, item_label, item_value, sort_no, status, created_by, deleted, version)
SELECT @contest_category_dict_type_id, c.item_label, c.item_value, c.sort_no, 1, 0, 0, 0
FROM (
    SELECT '数学建模类' item_label, 'MATH_MODELING' item_value, 10 sort_no
    UNION ALL SELECT '程序设计类', 'PROGRAMMING', 20
    UNION ALL SELECT '电子设计类', 'ELECTRONIC_DESIGN', 30
    UNION ALL SELECT '创新创业类', 'INNOVATION', 40
) c
WHERE NOT EXISTS (
    SELECT 1
    FROM sys_dict_item i
    WHERE i.dict_type_id = @contest_category_dict_type_id
      AND i.item_value = c.item_value
      AND i.deleted = 0
);
