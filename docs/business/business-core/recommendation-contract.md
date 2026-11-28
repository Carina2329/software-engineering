# 基于真实竞赛分类的相似竞赛推荐共享契约

| 项目 | 内容 |
| --- | --- |
| 契约版本 | 1.0.1 |
| 冻结日期 | 2026-11-28 |
| 页面阶段 | 竞赛中心推荐区 |
| 推荐对象 | 学生 |
| 入口 | 竞赛中心顶部“为你推荐”区域 |
| 权限 | 登录用户 |
| 数据范围 | SELF |

本契约只使用已经完成并合并的业务数据。当前不实现协同过滤、推荐模型或复杂召回排序，只做基于真实竞赛学科分类的相似竞赛推荐。

## 一、分类设计

竞赛分类归属于 `contest` 竞赛系列主档，不属于 `contest_edition` 届次。

- `contest.category` 存储字典项 `item_value`，可空；空值表示未分类。
- 一个竞赛系列第一版只选择一个主分类，不做多分类。
- 创建竞赛系列时必须选择分类；创建届次时自动继承所属竞赛系列的分类。
- 分类由平台统一维护，创建竞赛的管理员只能从启用字典中选择，不能自由输入。
- 历史竞赛由平台管理员补录分类；未分类不影响原有业务，但不参与相似竞赛推荐。
- EP-012 创建请求必填 `category`，必须是启用的 `CONTEST_CATEGORY` 字典值。
- EP-014 修改请求可选 `category`，用于历史补录或修改；传值时必须是启用字典值，不允许通过 `null` 清空。
- EP-011 列表和 EP-013 详情返回可空 `category` 与 `category_name`；历史未分类返回 `null`。

初始分类字典 `CONTEST_CATEGORY`：

| item_value | item_label |
| --- | --- |
| MATH_MODELING | 数学建模类 |
| PROGRAMMING | 程序设计类 |
| ELECTRONIC_DESIGN | 电子设计类 |
| INNOVATION | 创新创业类 |

不设综合兜底分类。分类下拉复用 `EP-009 GET /dicts/{dict_code}`。

## 二、行为事件

只采集一种行为：`contest_detail_viewed`。

前端上报字段：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| event_id | string | 前端为一次真实浏览生成的 UUID；同一次浏览网络重试复用，新的详情页加载生成新值 |
| contest_id | string | 浏览详情页对应的竞赛系列 ID |
| client_ts | string | 前端发生时间，仅作参考 |

服务端补全字段：

| 字段 | 说明 |
| --- | --- |
| user_id | 从登录态获取，不信任前端 |
| event_type | 固定 `contest_detail_viewed` |
| category | 后端按 contest_id 查 contest 主档得到，不信任前端 |
| occurred_at | 服务端接收时间，作为事件时间 |

## 三、上报与去重

投递语义为 best-effort：极端故障下允许极少量丢失或重复，通过 event_id 和下游去重降低重复，不保证 exactly-once。

去重键完全独立：

| Redis 键 | 作用 | TTL |
| --- | --- | --- |
| rec:ingest:event:{event_id} | 上报层 pending/sent 状态 | 7 天 |
| rec:flink:dedup:{event_id} | Flink 消费侧兜底去重 | 8 天 |
| rec:user:{uid}:last_contest | 该用户最近一次有效浏览的 contest_id | 7 天 |

上报层 pending/sent 状态机：先写 pending，等待 Kafka broker ACK 成功后才改为 sent 并返回 HTTP 202；Kafka 发送失败保留 pending，当前请求返回 HTTP 503 + `50004`，前端带同一 event_id 重试。sent 状态直接幂等返回成功。

202 只表示行为已受理并投递，不表示 Flink 已生成偏好。行为上报校验通过并成功投递后，写入 `rec:user:{uid}:last_contest`，用于 EP-101 排除学生刚浏览的竞赛系列。

推荐链路依赖不可用时使用 `50004`，含义为“推荐行为事件流依赖暂不可用”，HTTP 503，可重试；该错误码不改动 `50003` 的认证语义。

## 四、实时特征

Kafka topic `contest-behavior`，分区 key `user_id`，保留 7 天。

Flink 窗口口径：

| 项目 | 口径 |
| --- | --- |
| 事件时间 | occurred_at |
| 窗口类型 | 滚动窗口（Tumbling event-time window） |
| 窗口长度 | 5 分钟 |
| 水位线 | bound 1 分钟 |
| 迟到口径 | 超过水位线进 side output，丢弃，不进入偏好 |
| 最大预期延迟 | 约 6 分钟 |
| 特征窗口 | 最近 7 天 |

Redis 偏好计数桶 `rec:user:{uid}:cat:{category}`，field 为窗口起点的 Unix epoch 毫秒字符串，value 为浏览次数，key TTL 8 天兜底。读取时只统计 `[当前时间 - 7 天, 当前时间]` 内的桶，忽略未来桶；写入时清理 7 天前字段。TTL 只兜底清理，不承担窗口计算。组员 A 写入和组员 B 读取必须遵循同一 field 格式。

## 五、推荐接口

`EP-101 GET /recommendations`，返回 `preferred_category`、`recommendations`、`generated_at`。每个推荐项包含 `edition_id`、`edition_name`、`contest_id`、`contest_name`、`series_code`、`level`、`category`、`category_name`、`register_deadline`、`registration_open`、`preliminarily_eligible`、`reason`。

推荐候选必须是“偏好分类下、且不是学生刚浏览的那个竞赛系列”的初步符合推荐条件的届次。推荐结果关联具体 `edition_id`，同系列下的其他届次不冒充相似推荐。

学生刚浏览的竞赛系列从 `rec:user:{uid}:last_contest` 读取；该键不存在时不额外排除，仍按偏好分类正常推荐。

有效偏好与排序口径：

- 只有启用且未删除的分类、且最近 7 天总浏览次数大于 0，才算有效偏好。
- 偏好分类按浏览次数降序；次数相同按 `category` 字典值升序，保证结果稳定。
- 已停用或已删除的分类即使 Redis 中仍有计数，也不生成偏好。
- 无有效偏好时返回 `preferred_category=null`、`recommendations=[]`。
- 推荐结果按 `register_deadline` 升序，再按 `edition_id` 升序，最多 3 条。

初步资格筛选只判断当前阶段能确定的条件：

- 学生对届次可见；
- 届次正在报名；
- 学生学校范围符合要求；
- 个人或学校具有必要的邀请、晋级资格；
- 学生没有已存在的有效报名。

推荐接口只做初步判断，不返回 `can_register`，不承诺学生最终一定能报名。最终报名资格在正式提交报名时由现有服务校验。

## 六、页面展示

推荐入口在竞赛中心顶部、“全部竞赛”列表之前，不新增独立推荐页面或路由。

- 有有效偏好时，最多展示 3 个推荐结果；不足 3 个按实际数量，不为凑数放宽初步资格、分类或其他筛选条件。
- 每个结果包含竞赛名称、具体届次、当前开放报名状态、报名截止时间和推荐理由，点击进入对应届次详情页。
- 无有效偏好时不显示推荐区，只保留普通竞赛列表；不伪造偏好，也不用报名提醒顶替该区域。
- 学生刚产生浏览行为、但 Flink 尚未产出偏好时，按无有效偏好处理。
- 学生主动搜索或筛选时收起推荐区，恢复默认列表后再按有效偏好显示。
- 页面只表达“当前开放报名”，不表达“你一定能报名”。

## 七、公共边界

- Kafka 只用于学生行为事件流，采用 best-effort，不保证 exactly-once。
- RabbitMQ 仍只用于认证邮件 OTP。
- 学生首页待办与报名提醒是独立业务能力，不在本推荐任务实现。
