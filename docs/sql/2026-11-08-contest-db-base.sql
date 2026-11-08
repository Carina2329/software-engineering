-- AI 学科竞赛全流程智能服务平台 数据库建表脚本（MySQL 8.0）
-- 版本：1.0.1（2026-11-08）
-- 说明：业务关系由应用层保证，相关字段均已建索引；关联表仅含 id 与 created_at。
-- 注意：脚本会先 DROP 同名表再重建，只能在开发库执行，切勿在生产库运行！
-- 字符集：utf8mb4；引擎：InnoDB

SET NAMES utf8mb4;

DROP TABLE IF EXISTS `sys_user`;
CREATE TABLE `sys_user` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `username` VARCHAR(32) NOT NULL COMMENT '登录账号',
  `real_name` VARCHAR(32) NOT NULL COMMENT '真实姓名',
  `password_hash` VARCHAR(100) NOT NULL COMMENT '密码散列值',
  `student_no` VARCHAR(20) COMMENT '学号',
  `college` VARCHAR(64) COMMENT '学院',
  `major` VARCHAR(64) COMMENT '专业',
  `grade` VARCHAR(8) COMMENT '年级',
  `skill_tags` VARCHAR(255) COMMENT '技能标签',
  `title` VARCHAR(32) COMMENT '职称（教师）',
  `research_area` VARCHAR(128) COMMENT '研究方向（教师）',
  `max_teams` INT DEFAULT 3 COMMENT '可指导队伍数（教师）',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '账号状态',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `created_by` BIGINT NOT NULL COMMENT '创建人',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后修改时间',
  `updated_by` BIGINT COMMENT '最后修改人',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标志',
  `deleted_at` DATETIME COMMENT '删除时间',
  `version` INT NOT NULL DEFAULT 0 COMMENT '版本号（乐观锁）',
  UNIQUE KEY `uk_sys_user_username` (username),
  UNIQUE KEY `uk_sys_user_student_no` (student_no),
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='保存四类角色的账号与档案信息（FR-01）';

DROP TABLE IF EXISTS `sys_role`;
CREATE TABLE `sys_role` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `role_code` VARCHAR(16) NOT NULL COMMENT '角色编码',
  `role_name` VARCHAR(32) NOT NULL COMMENT '角色名称',
  `data_scope` VARCHAR(16) NOT NULL COMMENT '数据权限范围',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `created_by` BIGINT NOT NULL COMMENT '创建人',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后修改时间',
  `updated_by` BIGINT COMMENT '最后修改人',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标志',
  `deleted_at` DATETIME COMMENT '删除时间',
  `version` INT NOT NULL DEFAULT 0 COMMENT '版本号（乐观锁）',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='保存角色及其权限范围（FR-01，RBAC）';

DROP TABLE IF EXISTS `sys_user_role`;
CREATE TABLE `sys_user_role` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` BIGINT NOT NULL COMMENT '用户标识',
  `role_id` BIGINT NOT NULL COMMENT '角色标识',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  UNIQUE KEY `uk_user_role` (user_id, role_id),
  KEY `idx_user_role_role` (role_id),
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户与角色的多对多关系（FR-01）';

DROP TABLE IF EXISTS `contest`;
CREATE TABLE `contest` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `contest_name` VARCHAR(128) NOT NULL COMMENT '竞赛名称',
  `level` VARCHAR(16) NOT NULL COMMENT '竞赛级别',
  `organizer` VARCHAR(128) COMMENT '主办方',
  `intro` VARCHAR(1000) COMMENT '竞赛简介',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `created_by` BIGINT NOT NULL COMMENT '创建人',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后修改时间',
  `updated_by` BIGINT COMMENT '最后修改人',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标志',
  `deleted_at` DATETIME COMMENT '删除时间',
  `version` INT NOT NULL DEFAULT 0 COMMENT '版本号（乐观锁）',
  KEY `idx_contest_level` (level),
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='竞赛的稳定主档信息（FR-04）';

DROP TABLE IF EXISTS `contest_edition`;
CREATE TABLE `contest_edition` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `contest_id` BIGINT NOT NULL COMMENT '所属竞赛',
  `edition_name` VARCHAR(128) NOT NULL COMMENT '届次名称',
  `year` SMALLINT NOT NULL COMMENT '举办年份',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '届次状态',
  `register_deadline` DATETIME COMMENT '报名截止时间',
  `work_deadline` DATETIME COMMENT '作品截止时间',
  `review_start` DATETIME COMMENT '评审开始时间',
  `publish_start` DATETIME COMMENT '公示开始时间',
  `publish_end` DATETIME COMMENT '公示结束时间',
  `min_team_size` TINYINT NOT NULL DEFAULT 1 COMMENT '最少队伍人数',
  `max_team_size` TINYINT NOT NULL DEFAULT 5 COMMENT '最多队伍人数',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `created_by` BIGINT NOT NULL COMMENT '创建人',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后修改时间',
  `updated_by` BIGINT COMMENT '最后修改人',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标志',
  `deleted_at` DATETIME COMMENT '删除时间',
  `version` INT NOT NULL DEFAULT 0 COMMENT '版本号（乐观锁）',
  KEY `idx_edition_contest` (contest_id),
  KEY `idx_edition_status` (status),
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='竞赛的一次具体举办（FR-04，业务数据组织单位）';

DROP TABLE IF EXISTS `contest_milestone`;
CREATE TABLE `contest_milestone` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `edition_id` BIGINT NOT NULL COMMENT '所属届次',
  `node_type` VARCHAR(24) NOT NULL COMMENT '节点类型',
  `node_name` VARCHAR(64) NOT NULL COMMENT '节点名称',
  `start_time` DATETIME NOT NULL COMMENT '开始时间',
  `end_time` DATETIME NOT NULL COMMENT '结束时间',
  `enabled` TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `created_by` BIGINT NOT NULL COMMENT '创建人',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后修改时间',
  `updated_by` BIGINT COMMENT '最后修改人',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标志',
  `deleted_at` DATETIME COMMENT '删除时间',
  `version` INT NOT NULL DEFAULT 0 COMMENT '版本号（乐观锁）',
  KEY `idx_milestone_edition` (edition_id),
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='届次下的时间节点配置（FR-04）';

DROP TABLE IF EXISTS `contest_topic`;
CREATE TABLE `contest_topic` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `edition_id` BIGINT NOT NULL COMMENT '所属届次',
  `topic_name` VARCHAR(128) NOT NULL COMMENT '赛题名称',
  `direction` VARCHAR(64) COMMENT '方向',
  `difficulty` TINYINT COMMENT '难度',
  `tech_requirement` VARCHAR(512) COMMENT '技术需求',
  `max_teams` INT NOT NULL DEFAULT 1 COMMENT '可报名队伍数',
  `chosen_count` INT NOT NULL DEFAULT 0 COMMENT '已选题队伍数',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `created_by` BIGINT NOT NULL COMMENT '创建人',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后修改时间',
  `updated_by` BIGINT COMMENT '最后修改人',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标志',
  `deleted_at` DATETIME COMMENT '删除时间',
  `version` INT NOT NULL DEFAULT 0 COMMENT '版本号（乐观锁）',
  KEY `idx_topic_edition` (edition_id),
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='届次下设的赛题与容量（FR-06）';

DROP TABLE IF EXISTS `team`;
CREATE TABLE `team` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `edition_id` BIGINT NOT NULL COMMENT '所属届次',
  `team_name` VARCHAR(64) NOT NULL COMMENT '队伍名称',
  `team_code` VARCHAR(16) NOT NULL COMMENT '队伍编号',
  `topic_id` BIGINT COMMENT '所选赛题',
  `leader_id` BIGINT NOT NULL COMMENT '队长',
  `teacher_id` BIGINT COMMENT '指导教师',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '报名状态',
  `member_count` TINYINT NOT NULL DEFAULT 1 COMMENT '当前人数',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `created_by` BIGINT NOT NULL COMMENT '创建人',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后修改时间',
  `updated_by` BIGINT COMMENT '最后修改人',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标志',
  `deleted_at` DATETIME COMMENT '删除时间',
  `version` INT NOT NULL DEFAULT 0 COMMENT '版本号（乐观锁）',
  UNIQUE KEY `uk_team_edition_name` (edition_id, team_name),
  UNIQUE KEY `uk_team_code` (team_code),
  KEY `idx_team_edition_status` (edition_id, status),
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='参赛队伍及其当前状态（FR-05）';

DROP TABLE IF EXISTS `team_member`;
CREATE TABLE `team_member` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `team_id` BIGINT NOT NULL COMMENT '所属队伍',
  `user_id` BIGINT NOT NULL COMMENT '学生',
  `is_leader` TINYINT NOT NULL DEFAULT 0 COMMENT '是否队长',
  `join_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '加入时间',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '在队状态',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `created_by` BIGINT NOT NULL COMMENT '创建人',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后修改时间',
  `updated_by` BIGINT COMMENT '最后修改人',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标志',
  `deleted_at` DATETIME COMMENT '删除时间',
  `version` INT NOT NULL DEFAULT 0 COMMENT '版本号（乐观锁）',
  UNIQUE KEY `uk_team_member` (team_id, user_id),
  KEY `idx_team_member_user` (user_id),
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='队伍与学生的成员关系（FR-05）';

DROP TABLE IF EXISTS `work_submission`;
CREATE TABLE `work_submission` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `team_id` BIGINT NOT NULL COMMENT '提交队伍',
  `edition_id` BIGINT NOT NULL COMMENT '所属届次',
  `version_no` INT NOT NULL COMMENT '版本号',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '提交状态',
  `summary` VARCHAR(500) COMMENT '提交说明',
  `submit_time` DATETIME COMMENT '提交时间',
  `lock_time` DATETIME COMMENT '锁定时间',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `created_by` BIGINT NOT NULL COMMENT '创建人',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后修改时间',
  `updated_by` BIGINT COMMENT '最后修改人',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标志',
  `deleted_at` DATETIME COMMENT '删除时间',
  `version` INT NOT NULL DEFAULT 0 COMMENT '版本号（乐观锁）',
  UNIQUE KEY `uk_submission_version` (team_id, version_no),
  KEY `idx_submission_edition_status` (edition_id, status),
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='作品的一次提交版本（FR-07、FR-08）';

DROP TABLE IF EXISTS `work_file`;
CREATE TABLE `work_file` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `submission_id` BIGINT NOT NULL COMMENT '所属提交版本',
  `file_id` BIGINT NOT NULL COMMENT '文件',
  `file_type` TINYINT NOT NULL COMMENT '附件用途',
  `sort_no` TINYINT NOT NULL DEFAULT 0 COMMENT '排序号',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  UNIQUE KEY `uk_work_file_type` (submission_id, file_type),
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='作品版本与文件的关联（FR-07）。文件本体统一存 file 表，这里只记录“哪一次提交用了哪个文件”';

DROP TABLE IF EXISTS `ai_precheck`;
CREATE TABLE `ai_precheck` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `submission_id` BIGINT NOT NULL COMMENT '被预审的作品版本',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '预审状态',
  `issue_count` INT DEFAULT 0 COMMENT '问题数量',
  `severity_summary` VARCHAR(64) COMMENT '严重程度摘要',
  `engine_version` VARCHAR(32) COMMENT 'AI 引擎版本',
  `started_at` DATETIME COMMENT '开始时间',
  `finished_at` DATETIME COMMENT '完成时间',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `created_by` BIGINT NOT NULL COMMENT '创建人',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后修改时间',
  `updated_by` BIGINT COMMENT '最后修改人',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标志',
  `deleted_at` DATETIME COMMENT '删除时间',
  `version` INT NOT NULL DEFAULT 0 COMMENT '版本号（乐观锁）',
  KEY `idx_precheck_submission` (submission_id),
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='一次 AI 预审任务与结果（FR-08）';

DROP TABLE IF EXISTS `review_rubric`;
CREATE TABLE `review_rubric` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `edition_id` BIGINT NOT NULL COMMENT '所属届次',
  `dimension_name` VARCHAR(64) NOT NULL COMMENT '维度名称',
  `weight` DECIMAL(5,2) NOT NULL COMMENT '权重',
  `score_range` VARCHAR(16) NOT NULL DEFAULT 0 COMMENT '分值范围',
  `description` VARCHAR(500) COMMENT '评分说明',
  `sort_no` TINYINT NOT NULL DEFAULT 0 COMMENT '排序号',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `created_by` BIGINT NOT NULL COMMENT '创建人',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后修改时间',
  `updated_by` BIGINT COMMENT '最后修改人',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标志',
  `deleted_at` DATETIME COMMENT '删除时间',
  `version` INT NOT NULL DEFAULT 0 COMMENT '版本号（乐观锁）',
  UNIQUE KEY `uk_rubric_edition_name` (edition_id, dimension_name),
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='届次的评审维度与权重（FR-08、FR-09）';

DROP TABLE IF EXISTS `review_assignment`;
CREATE TABLE `review_assignment` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `edition_id` BIGINT NOT NULL COMMENT '所属届次',
  `submission_id` BIGINT NOT NULL COMMENT '被评审作品版本',
  `reviewer_id` BIGINT NOT NULL COMMENT '评审专家',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '评审状态',
  `total_score` DECIMAL(6,2) COMMENT '加权总分',
  `comment` VARCHAR(1000) COMMENT '评审评语',
  `submitted_at` DATETIME COMMENT '提交时间',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `created_by` BIGINT NOT NULL COMMENT '创建人',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后修改时间',
  `updated_by` BIGINT COMMENT '最后修改人',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标志',
  `deleted_at` DATETIME COMMENT '删除时间',
  `version` INT NOT NULL DEFAULT 0 COMMENT '版本号（乐观锁）',
  KEY `idx_assignment_submission` (submission_id),
  KEY `idx_assignment_reviewer` (reviewer_id, status),
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='一次专家评阅任务（FR-09）';

DROP TABLE IF EXISTS `review_score`;
CREATE TABLE `review_score` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `assignment_id` BIGINT NOT NULL COMMENT '所属评审任务',
  `rubric_id` BIGINT NOT NULL COMMENT '评分维度',
  `score` DECIMAL(5,2) NOT NULL COMMENT '维度得分',
  `comment` VARCHAR(500) COMMENT '维度评语',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `created_by` BIGINT NOT NULL COMMENT '创建人',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后修改时间',
  `updated_by` BIGINT COMMENT '最后修改人',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标志',
  `deleted_at` DATETIME COMMENT '删除时间',
  `version` INT NOT NULL DEFAULT 0 COMMENT '版本号（乐观锁）',
  UNIQUE KEY `uk_score_assignment_rubric` (assignment_id, rubric_id),
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='评审任务下的各维度得分（FR-09）';

DROP TABLE IF EXISTS `result_publication`;
CREATE TABLE `result_publication` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `edition_id` BIGINT NOT NULL COMMENT '所属届次',
  `team_id` BIGINT NOT NULL COMMENT '队伍',
  `total_score` DECIMAL(6,2) NOT NULL COMMENT '最终总分',
  `rank_no` INT NOT NULL COMMENT '排名',
  `award_level` VARCHAR(16) NOT NULL COMMENT '获奖等级',
  `publish_start` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '公示开始时间',
  `publish_end` DATETIME NOT NULL COMMENT '公示结束时间',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '公示状态',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `created_by` BIGINT NOT NULL COMMENT '创建人',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后修改时间',
  `updated_by` BIGINT COMMENT '最后修改人',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标志',
  `deleted_at` DATETIME COMMENT '删除时间',
  `version` INT NOT NULL DEFAULT 0 COMMENT '版本号（乐观锁）',
  UNIQUE KEY `uk_publication_team` (edition_id, team_id),
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='公示期内公开的成绩与获奖信息（FR-11）';

DROP TABLE IF EXISTS `appeal`;
CREATE TABLE `appeal` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `edition_id` BIGINT NOT NULL COMMENT '所属届次',
  `team_id` BIGINT NOT NULL COMMENT '提出队伍',
  `publication_id` BIGINT NOT NULL COMMENT '被异议的公示记录',
  `reason` VARCHAR(1000) NOT NULL COMMENT '异议理由',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '处理状态',
  `handler_id` BIGINT COMMENT '处理人',
  `handle_result` VARCHAR(500) COMMENT '处理结果说明',
  `handled_at` DATETIME COMMENT '处理时间',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `created_by` BIGINT NOT NULL COMMENT '创建人',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后修改时间',
  `updated_by` BIGINT COMMENT '最后修改人',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标志',
  `deleted_at` DATETIME COMMENT '删除时间',
  `version` INT NOT NULL DEFAULT 0 COMMENT '版本号（乐观锁）',
  UNIQUE KEY `uk_appeal_team` (edition_id, team_id),
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='队伍提出的成绩异议与处理记录（FR-11）';

DROP TABLE IF EXISTS `sys_menu`;
CREATE TABLE `sys_menu` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `parent_id` BIGINT NOT NULL COMMENT '上级菜单',
  `menu_name` VARCHAR(64) NOT NULL COMMENT '菜单名称',
  `menu_type` TINYINT NOT NULL COMMENT '菜单类型',
  `path` VARCHAR(128) COMMENT '前端路由',
  `perms` VARCHAR(64) COMMENT '权限标识',
  `sort_no` SMALLINT NOT NULL DEFAULT 0 COMMENT '同级排序',
  `visible` TINYINT NOT NULL DEFAULT 1 COMMENT '是否显示',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '启用状态',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `created_by` BIGINT NOT NULL COMMENT '创建人',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后修改时间',
  `updated_by` BIGINT COMMENT '最后修改人',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标志',
  `deleted_at` DATETIME COMMENT '删除时间',
  `version` INT NOT NULL DEFAULT 0 COMMENT '版本号（乐观锁）',
  KEY `idx_sys_menu_parent` (parent_id),
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='RBAC 的权限点：目录、菜单、按钮三级（满足“菜单按角色区分”）';

DROP TABLE IF EXISTS `sys_role_menu`;
CREATE TABLE `sys_role_menu` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `role_id` BIGINT NOT NULL COMMENT '角色',
  `menu_id` BIGINT NOT NULL COMMENT '菜单/权限点',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  UNIQUE KEY `uk_role_menu` (role_id, menu_id),
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色与权限点的多对多关系，决定某角色能看到哪些菜单和按钮';

DROP TABLE IF EXISTS `sys_dict_type`;
CREATE TABLE `sys_dict_type` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `dict_code` VARCHAR(32) NOT NULL COMMENT '字典编码',
  `dict_name` VARCHAR(64) NOT NULL COMMENT '字典名称',
  `remark` VARCHAR(255) COMMENT '备注',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '启用状态',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `created_by` BIGINT NOT NULL COMMENT '创建人',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后修改时间',
  `updated_by` BIGINT COMMENT '最后修改人',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标志',
  `deleted_at` DATETIME COMMENT '删除时间',
  `version` INT NOT NULL DEFAULT 0 COMMENT '版本号（乐观锁）',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='字典的“分类”，如比赛级别、赛题方向、获奖等级';

DROP TABLE IF EXISTS `sys_dict_item`;
CREATE TABLE `sys_dict_item` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `dict_type_id` BIGINT NOT NULL COMMENT '所属字典类型',
  `item_label` VARCHAR(64) NOT NULL COMMENT '显示名称',
  `item_value` VARCHAR(32) NOT NULL COMMENT '存储值',
  `sort_no` SMALLINT NOT NULL DEFAULT 0 COMMENT '排序号',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '启用状态',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `created_by` BIGINT NOT NULL COMMENT '创建人',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后修改时间',
  `updated_by` BIGINT COMMENT '最后修改人',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标志',
  `deleted_at` DATETIME COMMENT '删除时间',
  `version` INT NOT NULL DEFAULT 0 COMMENT '版本号（乐观锁）',
  UNIQUE KEY `uk_dict_item_value` (dict_type_id, item_value),
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='字典的具体取值，如“国家级”“人工智能”“一等奖”';

DROP TABLE IF EXISTS `file`;
CREATE TABLE `file` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `original_name` VARCHAR(255) NOT NULL COMMENT '原始文件名',
  `mime_type` VARCHAR(128) COMMENT '文件类型',
  `file_size` BIGINT NOT NULL COMMENT '文件大小',
  `storage_type` TINYINT NOT NULL COMMENT '存储方式',
  `storage_path` VARCHAR(255) NOT NULL COMMENT '相对路径',
  `sha256` CHAR(64) COMMENT '文件摘要',
  `uploader_id` BIGINT NOT NULL COMMENT '上传人',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `created_by` BIGINT NOT NULL COMMENT '创建人',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后修改时间',
  `updated_by` BIGINT COMMENT '最后修改人',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标志',
  `deleted_at` DATETIME COMMENT '删除时间',
  `version` INT NOT NULL DEFAULT 0 COMMENT '版本号（乐观锁）',
  UNIQUE KEY `uk_file_sha256` (sha256),
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='所有上传文件的唯一元数据（作品附件、规则文档等共用），文件本体存磁盘或 OSS';

DROP TABLE IF EXISTS `upload_task`;
CREATE TABLE `upload_task` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `file_id` BIGINT COMMENT '目标文件',
  `file_name` VARCHAR(255) NOT NULL COMMENT '文件名',
  `total_size` BIGINT NOT NULL COMMENT '总大小',
  `chunk_size` INT NOT NULL DEFAULT 5 COMMENT '分片大小',
  `total_chunks` INT NOT NULL COMMENT '分片总数',
  `uploaded_chunks` INT NOT NULL DEFAULT 0 COMMENT '已传分片数',
  `status` TINYINT NOT NULL COMMENT '任务状态',
  `expire_at` DATETIME DEFAULT NULL COMMENT '过期时间',
  `uploader_id` BIGINT NOT NULL COMMENT '上传人',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `created_by` BIGINT NOT NULL COMMENT '创建人',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后修改时间',
  `updated_by` BIGINT COMMENT '最后修改人',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标志',
  `deleted_at` DATETIME COMMENT '删除时间',
  `version` INT NOT NULL DEFAULT 0 COMMENT '版本号（乐观锁）',
  KEY `idx_upload_task_user` (uploader_id, status),
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='一次上传的任务记录，分片上传与断点续传靠它（记录已传分片数）';

DROP TABLE IF EXISTS `file_chunk`;
CREATE TABLE `file_chunk` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `upload_id` BIGINT NOT NULL COMMENT '所属上传任务',
  `chunk_index` INT NOT NULL COMMENT '分片序号',
  `chunk_size` INT NOT NULL COMMENT '分片大小',
  `chunk_path` VARCHAR(255) NOT NULL COMMENT '分片存储路径',
  `sha256` CHAR(64) COMMENT '分片摘要',
  `status` TINYINT NOT NULL COMMENT '分片状态',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `created_by` BIGINT NOT NULL COMMENT '创建人',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后修改时间',
  `updated_by` BIGINT COMMENT '最后修改人',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标志',
  `deleted_at` DATETIME COMMENT '删除时间',
  `version` INT NOT NULL DEFAULT 0 COMMENT '版本号（乐观锁）',
  UNIQUE KEY `uk_chunk_index` (upload_id, chunk_index),
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='每个分片的落盘记录，前端凭它判断“哪些片已经传过”';

DROP TABLE IF EXISTS `ai_precheck_report`;
CREATE TABLE `ai_precheck_report` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `precheck_id` BIGINT NOT NULL COMMENT '所属预审任务',
  `report_content` MEDIUMTEXT NOT NULL COMMENT '报告正文',
  `issue_json` JSON COMMENT '问题清单结构化数据',
  `word_count` INT DEFAULT 0 COMMENT '字数',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `created_by` BIGINT NOT NULL COMMENT '创建人',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后修改时间',
  `updated_by` BIGINT COMMENT '最后修改人',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标志',
  `deleted_at` DATETIME COMMENT '删除时间',
  `version` INT NOT NULL DEFAULT 0 COMMENT '版本号（乐观锁）',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='存放预审报告正文（大文本），与任务表分离，避免主表被大字段拖慢';

DROP TABLE IF EXISTS `sys_oper_log`;
CREATE TABLE `sys_oper_log` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` BIGINT NOT NULL COMMENT '操作人',
  `module` VARCHAR(64) NOT NULL COMMENT '业务模块',
  `operation` VARCHAR(128) NOT NULL COMMENT '操作描述',
  `request_uri` VARCHAR(255) COMMENT '请求地址',
  `request_method` VARCHAR(8) COMMENT '请求方法',
  `params` TEXT COMMENT '请求参数',
  `ip` VARCHAR(45) COMMENT '来源 IP',
  `duration_ms` INT COMMENT '耗时',
  `result` TINYINT NOT NULL COMMENT '执行结果',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `created_by` BIGINT NOT NULL COMMENT '创建人',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后修改时间',
  `updated_by` BIGINT COMMENT '最后修改人',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标志',
  `deleted_at` DATETIME COMMENT '删除时间',
  `version` INT NOT NULL DEFAULT 0 COMMENT '版本号（乐观锁）',
  KEY `idx_oper_log_user_time` (user_id, created_at),
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='关键操作的审计留痕：谁、何时、做了什么、结果如何';
