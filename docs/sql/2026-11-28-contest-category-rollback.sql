-- 竞赛学科分类公共基线回滚（MySQL 8.0）
-- 版本：1.0.0（2026-11-28）
-- 说明：仅当没有业务数据依赖时执行；若已有竞赛系列写入 category，需先清空后再回滚。

SET NAMES utf8mb4;
SET time_zone = '+08:00';

-- 1. 移除 contest.category 字段与索引
ALTER TABLE contest
  DROP INDEX idx_contest_category,
  DROP COLUMN category;

-- 2. 删除竞赛学科分类字典项与字典类型
DELETE FROM sys_dict_item
WHERE dict_type_id IN (
    SELECT id FROM sys_dict_type WHERE dict_code = 'CONTEST_CATEGORY'
)
  AND deleted = 0;

DELETE FROM sys_dict_type
WHERE dict_code = 'CONTEST_CATEGORY'
  AND deleted = 0;
