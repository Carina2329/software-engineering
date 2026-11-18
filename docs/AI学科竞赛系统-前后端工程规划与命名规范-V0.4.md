# AI 大学生学科竞赛全流程智能服务平台 —— 前后端工程规划与命名规范

| 项目 | 内容 |
| --- | --- |
| 文档版本 | V0.4 |
| 团队规模 | 6 人 |
| 关联文档 | 《功能需求说明书》V0.5 |
| 默认技术栈 | 后端 Java 17 + Spring Boot 3 + MyBatis-Plus + MySQL 8 + Redis；前端 Vue 3 + TypeScript + Vite + Element Plus |

## 版本变更记录

| 版本 | 日期 | 变更内容 | 历史版本 |
| --- | --- | --- | --- |
| V0.1 | 初版 | 工程目录、模块划分、命名规范、分支与交付约定 | `AI学科竞赛系统-前后端工程规划与命名规范-V0.1.md` |
| V0.2 | 2026-11-12 | 关联需求升级到 V0.3；明确赛题模式四选一、自拟项目、教师申请和选题更换；修正开发主线与命名示例 | 原件保留为《AI学科竞赛系统-前后端工程规划与命名规范-V0.2.md》 |
| V0.3 | 2026-11-15 | 关联需求升级到 V0.4；增加固定/范围队伍人数和届次教师指导上限；删除固定业务代号负责人，改为按依赖开放、组员认领、组长确认 | 原件保留为《AI学科竞赛系统-前后端工程规划与命名规范-V0.3.md》 |
| V0.4 | 2026-11-18 | 升级为多学校、多主办方赛事托管模型；增加组织、学校、省份、范围角色和比赛归属；冻结同校学号唯一与关键业务强制事务审计 | 当前版本 |

### V0.2 变更说明

- 每届赛题模式只能是 `PRESET_UNLIMITED`、`PRESET_LIMITED`、`SELF_PROPOSED`、`NO_TOPIC` 之一；
- 不支持混合模式；
- 教师接受指导关系时校验最大指导队伍数，不作先到先得；
- 队伍成员通过邀请、申请和确认加入；
- 选题或项目申报默认最多更换一次；
- 本轮只更新规划文档，不生成业务代码或 SQL。

### V0.3 变更说明

- 当前需求和业务公共契约以 V0.4 需求文档为准；
- 新增 `TeamSizeModeEnum`，取值为 `FIXED`、`RANGE`；
- 教师指导上限归属于届次规则，不在教师个人档案中维护；
- A、B、C、D、E 仅作为临时任务代号，不是真实业务域长期负责人；
- 业务按依赖顺序开放，由真实组员认领并经组长确认后再生成任务卡；
- 个人档案第一阶段只维护学生姓名、学号、学院、专业、年级，以及教师姓名、学院、职称。

### V0.4 变更说明

- 平台采用组织主档，学校是 `SCHOOL` 类型组织。
- 学生和教师绑定所属学校，省份通过学校主档推导。
- 学号唯一约束为 `(school_id, student_no)`，用户名和邮箱仍全平台唯一。
- 角色授权增加 `GLOBAL`、`ORGANIZATION`、`PROVINCE`、`EVENT` 范围。
- 竞赛主档表示竞赛系列；具体比赛实例承载级别、主办组织、学校/省份范围和可见范围。
- 关键业务成功写操作与审计日志必须处于同一数据库事务。

---

## 一、工程规划

### 1.1 仓库结构

```text
contest-agent-platform/
├─ backend/                     # Spring Boot 后端
│  ├─ pom.xml
│  └─ src/main/
│     ├─ java/com/campus/contest/
│     └─ resources/
│        ├─ application.yml
│        ├─ application-dev.yml
│        └─ mapper/
├─ frontend/                    # Vue 3 前端
│  ├─ package.json
│  ├─ vite.config.ts
│  └─ src/
├─ docs/                        # 需求、设计、测试等课程文档
├─ scripts/                     # 种子数据/历届数据/AI 测试集生成脚本
├─ docker/                      # Docker Compose、MySQL 初始化 SQL
└─ README.md
```

> 说明：本目录树为初始约定，后续可按需新增目录（如 `tests/`、`mock/`），新增目录须遵守本文档命名规则、组内知会并更新本节，文档版本号随之升级。

### 1.2 后端模块划分与负责人

后端基础包统一为 `com.campus.contest`，按领域分包：

```text
com.campus.contest
├─ ContestApplication.java
├─ common/                  # 统一返回、异常、常量、枚举、工具
├─ config/                  # Redis、MyBatis-Plus、CORS 等配置
├─ security/                # JWT、登录用户、权限注解
├─ storage/                 # 文件上传存储
├─ ai/                      # LLM 客户端、Prompt、RAG、相似度计算、异步任务编排
└─ module/
   ├─ system/               # 用户、角色、权限
   ├─ contest/              # 竞赛主档、届次、赛程节点、赛题模式与统一赛题
   ├─ team/                 # 队伍、成员邀请、教师申请、报名审核、赛题或项目申报
   ├─ work/                 # 作品、文件、版本、AI 预审、原创性初检
   ├─ review/               # 评分维度、评审任务、打分、成绩公示、异议处理
   ├─ knowledge/            # 规则知识库、问答
   ├─ rehearsal/            # 答辩预演
   ├─ notification/         # 站内通知、风险预警
   ├─ showcase/             # 获奖展厅、个人竞赛名片
   └─ statistics/           # 数据看板
```

Redis 用途约定：登录令牌/会话缓存、热点数据缓存（如竞赛列表）、AI 异步任务状态暂存。禁止将业务数据只存 Redis 不落库。

负责人采用业务域认领制，最终以真实组员认领和组长确认为准：

- 业务按“赛前基础 → 报名组织 → 选题项目 → 作品提交 → 评审公示 → 增强能力”的依赖顺序开放。
- 认领人负责其模块的持续维护、契约草稿、实现和验收证据，但不对代码或文档享有独占权。
- 一人当前只能作为一个未完成开放项的主要维护人，不能预占多个未开放模块。
- 公共契约、公共文件、公共 SQL、权限编码、分支、合并和集成由段欣懿统一维护。
- 真实组员认领后才生成阶段任务卡；任务卡负责明确本次文件所有权。

> 协作边界约定：`work` 模块的 AI 预审/原创性初检、`knowledge` 的问答、`rehearsal` 的出题评分，均由 C/B 提供 REST 接口与持久化，F 通过内部 Service 接口提供 AI 能力；接口签名在第 3 周设计定稿时冻结，禁止联调期临时改签名。

每个模块内部统一分层：

```text
module/contest/
├─ controller/   ContestController.java / EditionController.java
├─ service/      ContestService.java + impl/ContestServiceImpl.java
├─ mapper/       ContestMapper.java / EditionMapper.java
├─ entity/       ContestDO.java / EditionDO.java
├─ dto/          ContestCreateDTO.java / EditionQuery.java
└─ vo/           EditionDetailVO.java
```

跨模块只允许调用 Service 接口，禁止跨模块直接使用 Mapper。

### 1.3 前端目录与负责人

```text
frontend/src/
├─ api/                # 按业务模块拆分：auth、contest、team、work、review、knowledge、rehearsal、notification、showcase、statistics
├─ assets/
├─ components/         # 通用组件
├─ composables/        # useXxx 组合式函数
├─ directives/
├─ layouts/            # 学生/教师/管理员等不同布局
├─ router/
├─ stores/             # Pinia
├─ styles/
├─ types/
├─ utils/
└─ views/
   ├─ common/          # 登录、首页、竞赛列表等公共页面
   ├─ student/         # 学生端：档案、组队、选题、作品、预审、问答
   ├─ showcase/        # 获奖展厅、个人竞赛名片
   ├─ teacher/         # 教师端：指导申请、队伍进度、作品查看、结果查看
   ├─ reviewer/        # 评审任务、打分
   └─ admin/           # 管理端：竞赛/届次/赛题/评委/公示/知识库/看板配置
```

约定：

- 页面所有权由当前阶段任务卡明确；业务域认领人不独占前端或后端文件；
- 页面必须按角色目录放，禁止四种角色页面混在一个目录；
- 前后端接口联调前，前端统一使用 `src/api/` 下的 mock 数据或本地假接口，不许等后端。

### 1.4 业务开放顺序

| 顺序 | 业务域 | 开放条件 |
| --- | --- | --- |
| 0 | 业务公共契约、SQL、权限和测试数据 | 公共契约冻结，初始化与增量脚本可执行 |
| 1 | 赛前基础：个人档案、竞赛与届次 | 两项可并行，均基于正式认证/RBAC 基线 |
| 2 | 报名组织 | 档案、届次、报名时间和队伍身份规则稳定 |
| 3 | 选题项目 | 报名终审通过状态和届次赛题模式稳定 |
| 4 | 作品提交 | 报名和选题门槛可用，文件版本契约冻结 |
| 5 | 评审公示 | 已有锁定作品版本、评分规则和专家分配规则 |
| 6 | AI 与增强能力 | 作品或知识库数据可用，按独立闭环拆分认领 |

任何后续业务必须在前置条件满足后开放，不提前制作静态空壳页面。

### 1.5 Git 协作规范

分支模型：

| 分支 | 用途 |
| --- | --- |
| `main` | 可演示的稳定版本 |
| `develop` | 日常集成分支 |
| `feature/contest-create` | 新功能，从 `develop` 拉出 |
| `fix/login-expired` | 缺陷修复 |
| `refactor/review-service` | 重构 |

提交信息使用 Conventional Commits：

```text
feat(contest): 竞赛届次创建与发布
feat(work): 对接 AI 预审接口
fix(team): 修复成员退出后队长状态错误
docs(db): 补充届次表索引
```

流程要求：

- 业务功能从确认的正式业务基线拉分支，完成后发起合并，由段欣懿和至少一名组员 Review；
- 禁止直接提交 `main`；
- 密钥、真实数据、`.env` 禁止进仓库；
- 每周至少合一次 `develop`，避免大冲突。

### 1.6 Definition of Done

一个功能"完成"必须同时满足：

- 后端接口通过自测，返回结构与接口文档一致；
- 前端页面可操作，错误状态有提示；
- 代码通过 ESLint/格式化检查，命名符合本规范；
- 涉及数据库的改动已同步到初始化 SQL；
- 涉及 AI 的功能有至少一条可演示的输入输出记录，且 AI 服务断开时页面有降级提示。

---

## 二、统一业务术语

全项目禁止同义混用：

| 中文 | 统一用词 | 说明 |
| --- | --- | --- |
| 竞赛 | `contest` | 赛事主档，可举办多届 |
| 届次 | `edition` | 竞赛的一次举办实例 |
| 赛程节点 | `milestone` | 届次下的时间节点 |
| 赛题 | `topic` | 统一赛题模式下届次下设的预设题目 |
| 自拟项目 | `teamTopicProject` | `SELF_PROPOSED` 模式下由队伍申报的项目内容 |
| 赛题模式 | `TopicModeEnum` | `PRESET_UNLIMITED`、`PRESET_LIMITED`、`SELF_PROPOSED`、`NO_TOPIC` |
| 队伍 | `team` | 参赛队伍 |
| 队员 | `teamMember` | 队伍成员关系 |
| 指导教师 | `teacher` | 通过角色区分 |
| 评审专家 | `reviewer` | 通过角色区分 |
| 作品提交记录 | `workSubmission` | 一次作品版本 |
| 附件 | `file` | 作品文件 |
| AI 预审 | `aiPrecheck` | 对照评审细则的提交前检查 |
| 原创性初检 | `originalityCheck` | 与本届及往届作品的相似度比对 |
| 适配度分析 | `fitAnalysis` | 智能选题的适配度与能力缺口分析 |
| 评分维度 | `rubric` | 维度与权重 |
| 评审任务 | `reviewAssignment` | 专家被分配的任务 |
| 成绩公示 | `resultPublication` | 成绩公示与获奖名单 |
| 异议申诉 | `appeal` | 公示期内的成绩异议 |
| 答辩预演 | `rehearsalSession` | 一次预演会话 |
| 规则文档 | `knowledgeDoc` | 知识库文档 |
| 问答消息 | `qaMessage` | 带引用来源的消息 |
| 通知 | `notification` | 站内消息 |
| 风险预警 | `riskWarning` | 标红预警记录 |
| 获奖展厅 | `showcase` | 获奖作品展示 |
| 竞赛名片 | `portfolio` | 个人竞赛履历 |

状态值统一成枚举，禁止散落 0/1：

| 枚举 | 取值示例 |
| --- | --- |
| `EditionStatusEnum` | `DRAFT`、`REGISTERING`、`SUBMITTING`、`REVIEWING`、`PUBLISHING`、`ARCHIVED` |
| `TeamStatusEnum` | `PENDING_TEACHER`、`PENDING_ADMIN`、`APPROVED`、`REJECTED` |
| `TeamSizeModeEnum` | `FIXED`、`RANGE` |
| `TopicModeEnum` | `PRESET_UNLIMITED`、`PRESET_LIMITED`、`SELF_PROPOSED`、`NO_TOPIC` |
| `OrganizationTypeEnum` | `SCHOOL`、`PROVINCE_ORGANIZER`、`NATIONAL_ORGANIZER`、`PLATFORM` |
| `RoleScopeTypeEnum` | `GLOBAL`、`ORGANIZATION`、`PROVINCE`、`EVENT` |
| `ContestEventLevelEnum` | `SCHOOL`、`PROVINCE`、`NATIONAL` |
| `ContestVisibilityEnum` | `DRAFT`、`UNLISTED`、`SCHOOL`、`PROVINCE`、`PUBLIC` |
| `ContestEntryModeEnum` | `OPEN`、`INVITED`、`QUALIFIED` |
| `SchoolVerificationStatusEnum` | `PENDING`、`VERIFIED`、`REJECTED` |
| `TopicAllocationModeEnum` | `REVIEW`、`FIRST_COME` |
| `TopicApplicationStatusEnum` | `DRAFT`、`PENDING`、`APPROVED`、`REJECTED`、`WITHDRAWN` |
| `TeacherApplicationStatusEnum` | `PENDING`、`ACCEPTED`、`REJECTED`、`WITHDRAWN` |
| `TeamInvitationStatusEnum` | `PENDING`、`ACCEPTED`、`REJECTED`、`CANCELLED`、`EXPIRED` |
| `SubmissionStatusEnum` | `DRAFT`、`SUBMITTED`、`LOCKED` |
| `AiPrecheckStatusEnum` | `PENDING`、`RUNNING`、`SUCCESS`、`FAILED` |
| `ReviewStatusEnum` | `PENDING`、`IN_PROGRESS`、`SUBMITTED` |
| `AppealStatusEnum` | `PENDING`、`UPHELD`、`ADJUSTED` |
| `WarningTypeEnum` | `DEADLINE_APPROACHING`、`INACTIVE`、`PRECHECK_FAILED` |

---

## 三、命名规范

### 3.1 数据库命名

- 表名全小写 snake_case、单数；系统表加 `sys_` 前缀；
- 主键 `id`，时间字段 `created_at`/`updated_at`，逻辑删除 `deleted`；
- 普通索引 `idx_表名_字段`，唯一索引 `uk_表名_字段`；
- 业务表统一带届次外键 `edition_id`（队伍、作品、评审、公示等均挂在届次下）；
- 表名示例：`sys_user`、`contest`、`contest_edition`、`contest_topic`、`team`、`team_member`、`team_invitation`、`team_teacher_application`、`team_topic_application`、`work_submission`、`review_assignment`、`appeal`、`risk_warning`、`showcase_item`。

### 3.2 Java 命名

| 对象 | 规范 | 正确示例 | 错误示例 |
| --- | --- | --- | --- |
| 包名 | 全小写 | `com.campus.contest.module.team` | `com.campus.Team` |
| 类名 | UpperCamelCase | `EditionService` | `editionService` |
| 方法 | lowerCamelCase，动词开头 | `createEdition()` | `edition_create()` |
| 变量 | lowerCamelCase | `teamList` | `team_list`、`list1` |
| 常量 | UPPER_SNAKE_CASE | `DEFAULT_PAGE_SIZE` | `defaultPageSize` |
| 枚举 | 业务 + `Enum` | `EditionStatusEnum` | `Status` |
| 枚举值 | UPPER_SNAKE_CASE | `PENDING_TEACHER` | `PendingTeacher` |
| Controller | `XxxController` | `EditionController` | `Ctl` |
| Service | `XxxService` | `TeamService` | `TeamSvc` |
| Service 实现 | `XxxServiceImpl` | `TeamServiceImpl` | `TeamServiceImp` |
| Mapper | `XxxMapper` | `WorkSubmissionMapper` | `WorkSubmissionDao` |
| 实体 | `XxxDO` | `EditionDO` | `EditionEntity` |
| 新增/修改入参 | `XxxCreateDTO` / `XxxUpdateDTO` | `EditionCreateDTO` | `EditionDTO` |
| 查询参数 | `XxxQuery` | `EditionQuery` | `EditionSearchDTO` |
| 返回对象 | `XxxVO` | `EditionDetailVO` | `EditionDTO` |

### 3.3 前端命名

| 对象 | 规范 | 正确示例 | 错误示例 |
| --- | --- | --- | --- |
| Vue 文件 | PascalCase，多单词 | `EditionCreateDialog.vue` | `edition_create.vue`、`Dialog.vue` |
| 页面目录 | 按角色小写 | `views/admin/` | `views/ADMIN/` |
| TS 变量/函数 | camelCase | `fetchTeamList()` | `fetch_team_list()` |
| 常量 | UPPER_SNAKE_CASE | `ROLE_ADMIN` | `roleAdmin` |
| 类型/接口 | PascalCase，不加 I | `interface EditionDetail` | `IEditionDetail` |
| API 函数 | `fetch/create/update/delete` 开头 | `createTeam()`、`fetchEditionList()` | `save()` |
| 路由 path | 小写连字符 | `/admin/editions` | `/Admin/editions` |
| 路由 name | PascalCase | `name: 'AdminEditionList'` | `name: 'admin-edition-list'` |
| Pinia | `use` + PascalCase | `useAuthStore()` | `authStore()` |
| 组件事件 | kebab-case | `@submit-success` | `@submitSuccess` |
| CSS 类 | kebab-case/BEM | `.contest-card__header` | `.contestCardHeader` |

组件规范：

- 组件必须是多单词：`ContestCard.vue` 可以，`Card.vue` 不行；
- 使用 `<script setup lang="ts">`，业务代码禁止 `any`；
- Props 用 `defineProps<T>()`，事件用 `defineEmits<...>()`；
- 页面负责请求数据和跳转，通用组件只负责展示和通知，不直接调接口；
- 请求统一走 `src/api/request.ts`，禁止页面内新建 Axios 实例；
- 组件样式默认 `scoped`。

### 3.4 API 命名

- 统一前缀 `/api/v1`，资源用复数、多词连字符：`/api/v1/contests`、`/api/v1/review-assignments`；
- 只有非 CRUD 动作才用动词：`/works/{id}/ai-precheck`、`/teams/{id}/topic-selection`；
- 前端函数名与后端动作一一对应：`fetchEditionList` ↔ `GET /editions`，`submitReviewScore` ↔ `POST /review-assignments/{id}/submit-score`。

典型接口命名：

```text
POST   /api/v1/auth/login
GET    /api/v1/contests
POST   /api/v1/contests/{id}/editions
POST   /api/v1/editions/{id}/publish
POST   /api/v1/editions/{editionId}/teams
POST   /api/v1/teams/{id}/topic-selection
POST   /api/v1/teams/{id}/invitations
GET    /api/v1/users/me/team-invitations
GET    /api/v1/topics/{id}/fit-analysis
POST   /api/v1/works/{id}/ai-precheck
GET    /api/v1/works/{id}/precheck-report
POST   /api/v1/works/{id}/originality-check
POST   /api/v1/review-assignments/{id}/submit-score
POST   /api/v1/editions/{id}/appeals
POST   /api/v1/rehearsals
GET    /api/v1/warnings
GET    /api/v1/showcase/works
GET    /api/v1/statistics/overview
```

---

## 四、交付检查清单

- [ ] 后端 `mvn compile`、前端 `pnpm build` 全部通过；
- [ ] 代码格式化、命名符合本规范；
- [ ] 四个角色登录后菜单和数据权限正确；
- [ ] "发布竞赛届次 → 组队与教师确认 → 报名审核 → 赛题选择/自拟项目/无赛题 → 作品 → AI 预审 → 盲评 → 成绩公示"主链路可演示；
- [ ] 规则问答回答带来源引用，超库问题明确拒答；
- [ ] AI 服务断开时主链路可走通、页面有降级提示；
- [ ] 数据库可从零初始化，Docker 一键启动；
- [ ] 种子数据（含 2–3 个历史届次）和 AI 测试集在 `scripts/` 下可复现；
- [ ] 课程文档齐全，README 含启动步骤和演示账号。

> 重要提醒：本规范在设计定稿周必须冻结接口与命名，后续只允许小范围修订；频繁改契约会让 6 个人的工作同时返工。确需修订时，组内确认后升级文档版本号并全员同步。
