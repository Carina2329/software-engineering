-- 业务核心开发测试数据（MySQL 8.0）
-- 版本：1.1.0（2026-11-22）
-- 仅用于开发环境；不得在生产环境执行。
-- 依赖：当前多组织业务基表和 CN_PROVINCE 省份数据。

SET NAMES utf8mb4;
SET time_zone = '+08:00';

INSERT INTO organization
    (org_code, org_name, org_type, province_code, status,
     created_at, created_by, deleted, version)
SELECT 'BIZ-CORE-DEMO-SCHOOL', '业务核心开发演示学校', 'SCHOOL', '510000', 1,
       NOW(), 0, 0, 0
WHERE NOT EXISTS (
    SELECT 1 FROM organization
    WHERE org_code = 'BIZ-CORE-DEMO-SCHOOL'
      AND deleted = 0
);

SET @demo_school_id = (
    SELECT id FROM organization
    WHERE org_code = 'BIZ-CORE-DEMO-SCHOOL'
      AND deleted = 0
    ORDER BY id LIMIT 1
);

INSERT INTO contest
    (contest_name, series_code, level, organizer, intro,
     created_at, created_by, deleted, version)
SELECT '软件工程课程综合实践演示赛', 'BIZ-CORE-DEMO-SERIES', 'SCHOOL', '软件工程课程组',
       '用于业务核心和后续全流程联调的开发测试竞赛。',
       NOW(), 0, 0, 0
WHERE NOT EXISTS (
    SELECT 1 FROM contest
    WHERE contest_name = '软件工程课程综合实践演示赛'
      AND organizer = '软件工程课程组'
      AND deleted = 0
);

SET @demo_contest_id = (
    SELECT id FROM contest
    WHERE contest_name = '软件工程课程综合实践演示赛'
      AND organizer = '软件工程课程组'
      AND deleted = 0
    ORDER BY id LIMIT 1
);

INSERT INTO contest_edition
    (contest_id, owner_org_id, edition_name, year, level, school_id, province_code, status,
     register_deadline, work_deadline, review_start, publish_start, publish_end,
     min_team_size, max_team_size, team_size_mode, max_teams_per_teacher,
     topic_mode, topic_review_required, topic_allocation_mode, topic_change_limit,
     visibility, entry_mode, cross_school_team_allowed,
     created_at, created_by, deleted, version)
SELECT @demo_contest_id, @demo_school_id, '2026 统一赛题不限量测试届',
       2026, 'SCHOOL', @demo_school_id, NULL, 0,
       DATE_ADD(NOW(), INTERVAL 30 DAY),
       DATE_ADD(NOW(), INTERVAL 90 DAY),
       DATE_ADD(NOW(), INTERVAL 95 DAY),
       DATE_ADD(NOW(), INTERVAL 110 DAY),
       DATE_ADD(NOW(), INTERVAL 117 DAY),
       1, 5, 'RANGE', 3, 'PRESET_UNLIMITED', 1, 'REVIEW', 1,
       'DRAFT', 'OPEN', 0,
       NOW(), 0, 0, 0
WHERE NOT EXISTS (
    SELECT 1 FROM contest_edition
    WHERE contest_id = @demo_contest_id
      AND edition_name = '2026 统一赛题不限量测试届'
      AND deleted = 0
);

INSERT INTO contest_edition
    (contest_id, owner_org_id, edition_name, year, level, school_id, province_code, status,
     register_deadline, work_deadline, review_start, publish_start, publish_end,
     min_team_size, max_team_size, team_size_mode, max_teams_per_teacher,
     topic_mode, topic_review_required, topic_allocation_mode, topic_change_limit,
     visibility, entry_mode, cross_school_team_allowed,
     created_at, created_by, deleted, version)
SELECT @demo_contest_id, @demo_school_id, '2026 固定三人限额赛题测试届',
       2026, 'SCHOOL', @demo_school_id, NULL, 0,
       DATE_ADD(NOW(), INTERVAL 30 DAY),
       DATE_ADD(NOW(), INTERVAL 90 DAY),
       DATE_ADD(NOW(), INTERVAL 95 DAY),
       DATE_ADD(NOW(), INTERVAL 110 DAY),
       DATE_ADD(NOW(), INTERVAL 117 DAY),
       3, 3, 'FIXED', 2, 'PRESET_LIMITED', 1, 'REVIEW', 1,
       'DRAFT', 'OPEN', 0,
       NOW(), 0, 0, 0
WHERE NOT EXISTS (
    SELECT 1 FROM contest_edition
    WHERE contest_id = @demo_contest_id
      AND edition_name = '2026 固定三人限额赛题测试届'
      AND deleted = 0
);

INSERT INTO contest_edition
    (contest_id, owner_org_id, edition_name, year, level, school_id, province_code, status,
     register_deadline, work_deadline, review_start, publish_start, publish_end,
     min_team_size, max_team_size, team_size_mode, max_teams_per_teacher,
     topic_mode, topic_review_required, topic_allocation_mode, topic_change_limit,
     visibility, entry_mode, cross_school_team_allowed,
     created_at, created_by, deleted, version)
SELECT @demo_contest_id, @demo_school_id, '2026 自拟项目测试届',
       2026, 'SCHOOL', @demo_school_id, NULL, 0,
       DATE_ADD(NOW(), INTERVAL 30 DAY),
       DATE_ADD(NOW(), INTERVAL 90 DAY),
       DATE_ADD(NOW(), INTERVAL 95 DAY),
       DATE_ADD(NOW(), INTERVAL 110 DAY),
       DATE_ADD(NOW(), INTERVAL 117 DAY),
       2, 4, 'RANGE', 3, 'SELF_PROPOSED', 1, 'REVIEW', 1,
       'DRAFT', 'OPEN', 0,
       NOW(), 0, 0, 0
WHERE NOT EXISTS (
    SELECT 1 FROM contest_edition
    WHERE contest_id = @demo_contest_id
      AND edition_name = '2026 自拟项目测试届'
      AND deleted = 0
);

INSERT INTO contest_edition
    (contest_id, owner_org_id, edition_name, year, level, school_id, province_code, status,
     register_deadline, work_deadline, review_start, publish_start, publish_end,
     min_team_size, max_team_size, team_size_mode, max_teams_per_teacher,
     topic_mode, topic_review_required, topic_allocation_mode, topic_change_limit,
     visibility, entry_mode, cross_school_team_allowed,
     created_at, created_by, deleted, version)
SELECT @demo_contest_id, @demo_school_id, '2026 无赛题固定四人测试届',
       2026, 'SCHOOL', @demo_school_id, NULL, 0,
       DATE_ADD(NOW(), INTERVAL 30 DAY),
       DATE_ADD(NOW(), INTERVAL 90 DAY),
       DATE_ADD(NOW(), INTERVAL 95 DAY),
       DATE_ADD(NOW(), INTERVAL 110 DAY),
       DATE_ADD(NOW(), INTERVAL 117 DAY),
       4, 4, 'FIXED', 2, 'NO_TOPIC', 0, 'REVIEW', 0,
       'DRAFT', 'OPEN', 0,
       NOW(), 0, 0, 0
WHERE NOT EXISTS (
    SELECT 1 FROM contest_edition
    WHERE contest_id = @demo_contest_id
      AND edition_name = '2026 无赛题固定四人测试届'
      AND deleted = 0
);
