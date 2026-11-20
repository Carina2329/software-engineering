-- 多组织开发测试数据（MySQL 8.0）
-- 仅用于开发环境，不用于生产。

SET NAMES utf8mb4;
SET time_zone = '+08:00';

INSERT INTO organization
  (org_code, org_name, org_type, province_code, status, created_by, version)
SELECT 'PLATFORM', '赛事平台', 'PLATFORM', NULL, 1, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM organization WHERE org_code = 'PLATFORM' AND deleted = 0);

INSERT INTO organization
  (org_code, org_name, org_type, province_code, status, created_by, version)
SELECT 'SC-SCHOOL-A', '四川A大学', 'SCHOOL', '510000', 1, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM organization WHERE org_code = 'SC-SCHOOL-A' AND deleted = 0);

INSERT INTO organization
  (org_code, org_name, org_type, province_code, status, created_by, version)
SELECT 'SC-SCHOOL-B', '四川B大学', 'SCHOOL', '510000', 1, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM organization WHERE org_code = 'SC-SCHOOL-B' AND deleted = 0);

INSERT INTO organization
  (org_code, org_name, org_type, province_code, status, created_by, version)
SELECT 'SC-SCHOOL-C', '四川C大学', 'SCHOOL', '510000', 1, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM organization WHERE org_code = 'SC-SCHOOL-C' AND deleted = 0);

INSERT INTO organization
  (org_code, org_name, org_type, province_code, status, created_by, version)
SELECT 'HN-SCHOOL-D', '湖南D大学', 'SCHOOL', '430000', 1, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM organization WHERE org_code = 'HN-SCHOOL-D' AND deleted = 0);

INSERT INTO organization
  (org_code, org_name, org_type, province_code, status, created_by, version)
SELECT 'SC-PROVINCE-ORG', '四川省赛事主办方', 'PROVINCE_ORGANIZER', '510000', 1, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM organization WHERE org_code = 'SC-PROVINCE-ORG' AND deleted = 0);

INSERT INTO organization
  (org_code, org_name, org_type, province_code, status, created_by, version)
SELECT 'NATIONAL-ORG', '全国赛事组委会', 'NATIONAL_ORGANIZER', NULL, 1, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM organization WHERE org_code = 'NATIONAL-ORG' AND deleted = 0);

SET @school_a = (SELECT id FROM organization WHERE org_code = 'SC-SCHOOL-A' AND deleted = 0);
SET @school_b = (SELECT id FROM organization WHERE org_code = 'SC-SCHOOL-B' AND deleted = 0);
SET @school_c = (SELECT id FROM organization WHERE org_code = 'SC-SCHOOL-C' AND deleted = 0);
SET @school_d = (SELECT id FROM organization WHERE org_code = 'HN-SCHOOL-D' AND deleted = 0);
SET @province_org = (SELECT id FROM organization WHERE org_code = 'SC-PROVINCE-ORG' AND deleted = 0);
SET @national_org = (SELECT id FROM organization WHERE org_code = 'NATIONAL-ORG' AND deleted = 0);

UPDATE sys_user
SET school_id = NULL,
    school_verification_status = 'VERIFIED',
    school_reviewed_by = 0,
    school_reviewed_at = NOW(),
    school_review_reason = '平台超级管理员'
WHERE username = 'admin_demo' AND deleted = 0;

UPDATE sys_user
SET school_id = @school_a,
    school_verification_status = 'VERIFIED',
    school_reviewed_by = 0,
    school_reviewed_at = NOW(),
    school_review_reason = '开发种子数据'
WHERE username = 'student_a' AND deleted = 0;

UPDATE sys_user
SET school_id = @school_b,
    school_verification_status = 'VERIFIED',
    school_reviewed_by = 0,
    school_reviewed_at = NOW(),
    school_review_reason = '开发种子数据'
WHERE username = 'student_b' AND deleted = 0;

UPDATE sys_user
SET school_id = @school_a,
    school_verification_status = 'VERIFIED',
    school_reviewed_by = 0,
    school_reviewed_at = NOW(),
    school_review_reason = '开发种子数据'
WHERE username IN ('it_teacher', 'it_reviewer') AND deleted = 0;

INSERT INTO sys_user
  (username, real_name, password_hash, email, student_no, school_id,
   school_verification_status, school_reviewed_by, school_reviewed_at, school_review_reason,
   college, major, grade, status, created_by, version)
SELECT 'student_hunan', '湖南测试学生', u.password_hash, 'student_hunan@example.edu',
       '20240001', @school_d, 'VERIFIED', 0, NOW(), '开发种子数据',
       '计算机学院', '软件工程', '2024', 1, 0, 0
FROM sys_user u
WHERE u.username = 'student_a' AND u.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_user WHERE username = 'student_hunan' AND deleted = 0);

INSERT INTO sys_user
  (username, real_name, password_hash, email, school_id,
   school_verification_status, school_reviewed_by, school_reviewed_at, school_review_reason,
   college, title, status, created_by, version)
SELECT 'school_admin_a', 'A大学管理员', u.password_hash, 'school_admin_a@example.edu',
       @school_a, 'VERIFIED', 0, NOW(), '开发种子数据',
       '教务处', '管理员', 1, 0, 0
FROM sys_user u
WHERE u.username = 'admin_demo' AND u.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_user WHERE username = 'school_admin_a' AND deleted = 0);

INSERT INTO sys_user
  (username, real_name, password_hash, email, school_id,
   school_verification_status, school_reviewed_by, school_reviewed_at, school_review_reason,
   college, title, status, created_by, version)
SELECT 'province_admin_sc', '四川省赛事管理员', u.password_hash, 'province_admin_sc@example.edu',
       NULL, 'VERIFIED', 0, NOW(), '开发种子数据',
       '竞赛管理处', '管理员', 1, 0, 0
FROM sys_user u
WHERE u.username = 'admin_demo' AND u.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_user WHERE username = 'province_admin_sc' AND deleted = 0);

INSERT INTO sys_user
  (username, real_name, password_hash, email, school_id,
   school_verification_status, school_reviewed_by, school_reviewed_at, school_review_reason,
   college, title, status, created_by, version)
SELECT 'national_admin', '全国赛事管理员', u.password_hash, 'national_admin@example.edu',
       NULL, 'VERIFIED', 0, NOW(), '开发种子数据',
       '竞赛组委会', '管理员', 1, 0, 0
FROM sys_user u
WHERE u.username = 'admin_demo' AND u.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_user WHERE username = 'national_admin' AND deleted = 0);

INSERT IGNORE INTO sys_user_role (user_id, role_id, scope_type, scope_id)
SELECT u.id, r.id, 'ORGANIZATION', @school_a
FROM sys_user u JOIN sys_role r ON r.role_code = 'SCHOOL_ADMIN' AND r.deleted = 0
WHERE u.username = 'school_admin_a' AND u.deleted = 0;

INSERT IGNORE INTO sys_user_role (user_id, role_id, scope_type, scope_id)
SELECT u.id, r.id, 'PROVINCE', 510000
FROM sys_user u JOIN sys_role r ON r.role_code = 'PROVINCE_ADMIN' AND r.deleted = 0
WHERE u.username = 'province_admin_sc' AND u.deleted = 0;

INSERT IGNORE INTO sys_user_role (user_id, role_id, scope_type, scope_id)
SELECT u.id, r.id, 'ORGANIZATION', @national_org
FROM sys_user u JOIN sys_role r ON r.role_code = 'NATIONAL_ADMIN' AND r.deleted = 0
WHERE u.username = 'national_admin' AND u.deleted = 0;

INSERT INTO contest
  (contest_name, series_code, level, organizer, intro, created_by, version)
SELECT '全国大学生统计建模大赛', 'SERIES-STAT-MODEL', 'NATIONAL',
       '全国赛事组委会', '统计建模校赛、省赛和全国赛演示系列', 0, 0
WHERE NOT EXISTS (SELECT 1 FROM contest WHERE series_code = 'SERIES-STAT-MODEL' AND deleted = 0);

INSERT INTO contest
  (contest_name, series_code, level, organizer, intro, created_by, version)
SELECT '四川省软件设计大赛', 'SERIES-SICHUAN-SOFT', 'PROVINCE',
       '四川省赛事主办方', '用于验证不同赛题模式和规则的第二个比赛', 0, 0
WHERE NOT EXISTS (SELECT 1 FROM contest WHERE series_code = 'SERIES-SICHUAN-SOFT' AND deleted = 0);

SET @series_stat = (SELECT id FROM contest WHERE series_code = 'SERIES-STAT-MODEL' AND deleted = 0);
SET @series_soft = (SELECT id FROM contest WHERE series_code = 'SERIES-SICHUAN-SOFT' AND deleted = 0);

INSERT INTO contest_edition
  (contest_id, owner_org_id, edition_name, year, level, school_id, province_code,
   status, register_deadline, work_deadline, review_start, publish_start, publish_end,
   min_team_size, max_team_size, team_size_mode, max_teams_per_teacher,
   topic_mode, topic_review_required, topic_allocation_mode, topic_change_limit,
   visibility, entry_mode, cross_school_team_allowed, created_by, version)
SELECT @series_stat, @school_a, '2026年A大学统计建模校赛', 2026, 'SCHOOL', @school_a, NULL,
       0, DATE_ADD(NOW(), INTERVAL 10 DAY), DATE_ADD(NOW(), INTERVAL 30 DAY),
       DATE_ADD(NOW(), INTERVAL 35 DAY), DATE_ADD(NOW(), INTERVAL 45 DAY),
       DATE_ADD(NOW(), INTERVAL 50 DAY), 2, 5, 'RANGE', 3,
       'SELF_PROPOSED', 1, 'REVIEW', 1, 'DRAFT', 'OPEN', 0, 0, 0
WHERE NOT EXISTS (
  SELECT 1 FROM contest_edition
  WHERE owner_org_id = @school_a AND year = 2026 AND edition_name = '2026年A大学统计建模校赛' AND deleted = 0
);

INSERT INTO contest_edition
  (contest_id, owner_org_id, edition_name, year, level, school_id, province_code,
   status, register_deadline, work_deadline, review_start, publish_start, publish_end,
   min_team_size, max_team_size, team_size_mode, max_teams_per_teacher,
   topic_mode, topic_review_required, topic_allocation_mode, topic_change_limit,
   visibility, entry_mode, cross_school_team_allowed, created_by, version)
SELECT @series_stat, @school_b, '2026年B大学统计建模校赛', 2026, 'SCHOOL', @school_b, NULL,
       0, DATE_ADD(NOW(), INTERVAL 12 DAY), DATE_ADD(NOW(), INTERVAL 32 DAY),
       DATE_ADD(NOW(), INTERVAL 37 DAY), DATE_ADD(NOW(), INTERVAL 47 DAY),
       DATE_ADD(NOW(), INTERVAL 52 DAY), 2, 5, 'RANGE', 3,
       'SELF_PROPOSED', 1, 'REVIEW', 1, 'DRAFT', 'OPEN', 0, 0, 0
WHERE NOT EXISTS (
  SELECT 1 FROM contest_edition
  WHERE owner_org_id = @school_b AND year = 2026 AND edition_name = '2026年B大学统计建模校赛' AND deleted = 0
);

INSERT INTO contest_edition
  (contest_id, owner_org_id, edition_name, year, level, school_id, province_code,
   status, register_deadline, work_deadline, review_start, publish_start, publish_end,
   min_team_size, max_team_size, team_size_mode, max_teams_per_teacher,
   topic_mode, topic_review_required, topic_allocation_mode, topic_change_limit,
   visibility, entry_mode, cross_school_team_allowed, created_by, version)
SELECT @series_stat, @province_org, '2026年四川省统计建模省赛', 2026, 'PROVINCE', NULL, '510000',
       0, DATE_ADD(NOW(), INTERVAL 40 DAY), DATE_ADD(NOW(), INTERVAL 70 DAY),
       DATE_ADD(NOW(), INTERVAL 75 DAY), DATE_ADD(NOW(), INTERVAL 85 DAY),
       DATE_ADD(NOW(), INTERVAL 90 DAY), 2, 5, 'RANGE', 3,
       'PRESET_LIMITED', 1, 'REVIEW', 1, 'DRAFT', 'QUALIFIED', 0, 0, 0
WHERE NOT EXISTS (
  SELECT 1 FROM contest_edition
  WHERE owner_org_id = @province_org AND year = 2026 AND edition_name = '2026年四川省统计建模省赛' AND deleted = 0
);

INSERT INTO contest_edition
  (contest_id, owner_org_id, edition_name, year, level, school_id, province_code,
   status, register_deadline, work_deadline, review_start, publish_start, publish_end,
   min_team_size, max_team_size, team_size_mode, max_teams_per_teacher,
   topic_mode, topic_review_required, topic_allocation_mode, topic_change_limit,
   visibility, entry_mode, cross_school_team_allowed, created_by, version)
SELECT @series_stat, @national_org, '2026年全国统计建模决赛', 2026, 'NATIONAL', NULL, NULL,
       0, DATE_ADD(NOW(), INTERVAL 100 DAY), DATE_ADD(NOW(), INTERVAL 130 DAY),
       DATE_ADD(NOW(), INTERVAL 135 DAY), DATE_ADD(NOW(), INTERVAL 145 DAY),
       DATE_ADD(NOW(), INTERVAL 150 DAY), 3, 3, 'FIXED', 2,
       'NO_TOPIC', 0, 'REVIEW', 0, 'PUBLIC', 'QUALIFIED', 1, 0, 0
WHERE NOT EXISTS (
  SELECT 1 FROM contest_edition
  WHERE owner_org_id = @national_org AND year = 2026 AND edition_name = '2026年全国统计建模决赛' AND deleted = 0
);

INSERT INTO contest_edition
  (contest_id, owner_org_id, edition_name, year, level, school_id, province_code,
   status, register_deadline, work_deadline, review_start, publish_start, publish_end,
   min_team_size, max_team_size, team_size_mode, max_teams_per_teacher,
   topic_mode, topic_review_required, topic_allocation_mode, topic_change_limit,
   visibility, entry_mode, cross_school_team_allowed, created_by, version)
SELECT @series_soft, @province_org, '2026年四川省软件设计赛', 2026, 'PROVINCE', NULL, '510000',
       0, DATE_ADD(NOW(), INTERVAL 20 DAY), DATE_ADD(NOW(), INTERVAL 55 DAY),
       DATE_ADD(NOW(), INTERVAL 60 DAY), DATE_ADD(NOW(), INTERVAL 70 DAY),
       DATE_ADD(NOW(), INTERVAL 75 DAY), 3, 3, 'FIXED', 3,
       'NO_TOPIC', 0, 'REVIEW', 0, 'PROVINCE', 'OPEN', 1, 0, 0
WHERE NOT EXISTS (
  SELECT 1 FROM contest_edition
  WHERE owner_org_id = @province_org AND year = 2026 AND edition_name = '2026年四川省软件设计赛' AND deleted = 0
);
