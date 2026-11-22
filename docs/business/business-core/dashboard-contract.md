# 数字大屏一期共享契约

| 项目 | 内容 |
| --- | --- |
| 契约版本 | 1.0.1 |
| 冻结日期 | 2026-11-21 |
| 页面阶段 | 数字大屏一期 |
| 观察对象 | 学科竞赛平台运行状态 |
| 目标用户 | 平台管理员 |
| 权限 | `dashboard:view` |
| 数据范围 | `GLOBAL` |

本契约只使用已经完成并合并的业务数据。当前不展示报名队伍、作品、评审、公示或获奖数据。

## 一、页面范围

一期页面包含：

- 4 个核心指标；
- 省份学校分布；
- 用户角色构成；
- 竞赛系列与届次摘要；
- 最近 7 天报名截止列表；
- WebSocket 实时连接状态；
- 页面底部数据更新时间和数据来源。

页面不再增加其他业务模块。底部只展示：

```text
数据更新时间：<generated_at>
数据来源：平台组织、用户及竞赛届次业务库
```

## 二、数据口径

所有指标使用 `Asia/Shanghai` 业务时间。计数统一返回非负整数，不使用字符串表示数字。

| 指标 | 口径 |
| --- | --- |
| `effective_school_count` | `organization.org_type=SCHOOL`、`status=1`、`deleted=0`，且 `province_code` 为启用状态的 `CN_PROVINCE` 字典项 |
| `active_user_count` | `sys_user.status=1`、`deleted=0` 的用户数 |
| `registering_edition_count` | 状态为 `REGISTERING`，且 `register_deadline >= 当前时间` 的届次数 |
| `pending_affiliation_count` | 启用用户的 `school_verification_status=PENDING` 的申请数 |
| `contest_series_count` | `contest.deleted=0` 的竞赛系列数 |
| `published_edition_count` | `contest_edition.deleted=0`，状态不等于 `DRAFT` 的届次数 |
| `level_distribution` | 未删除届次按 `SCHOOL`、`PROVINCE`、`NATIONAL` 分组计数 |
| `province_schools` | 有效学校按省份分组，返回省份编码、启用字典名称和学校数，按学校数降序 |
| `user_roles` | 有效用户按有效角色授权分组，统计不同用户数 |
| `upcoming_deadlines` | `REGISTERING` 且报名截止位于 `[当前时间, 当前时间 + 7 天)` 的届次，按截止时间升序，最多 5 条 |

角色统计要求：

- `sys_user.status=1` 且 `deleted=0`；
- `sys_user_role.status=1` 且 `deleted=0`；
- 当前时间位于 `valid_from` 和 `valid_to` 的有效区间内；
- 返回未删除角色；`sys_role` 当前没有独立启用状态字段，角色删除状态即可用状态；
- 同一用户在同一角色下有多条范围授权时，只计一次。

## 三、REST 快照接口

### GET `/api/v1/dashboard/overview`

权限：

- Bearer Access Token；
- `dashboard:view`；
- 当前用户必须是 `GLOBAL` 平台管理员。

成功响应：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "generated_at": "2026-11-21T17:30:00+08:00",
    "data_source": "平台组织、用户及竞赛届次业务库",
    "summary": {
      "effective_school_count": 4,
      "active_user_count": 9,
      "registering_edition_count": 6,
      "pending_affiliation_count": 0
    },
    "contest_operations": {
      "contest_series_count": 2,
      "published_edition_count": 6,
      "level_distribution": [
        { "level": "SCHOOL", "count": 3 },
        { "level": "PROVINCE", "count": 2 },
        { "level": "NATIONAL", "count": 1 }
      ]
    },
    "province_schools": [
      {
        "province_code": "510000",
        "province_name": "四川省",
        "school_count": 3
      }
    ],
    "user_roles": [
      {
        "role_code": "STUDENT",
        "role_name": "学生",
        "user_count": 3
      }
    ],
    "upcoming_deadlines": [
      {
        "edition_id": "1",
        "edition_name": "2026年A大学统计建模校赛",
        "contest_name": "全国大学生统计建模大赛",
        "level": "SCHOOL",
        "register_deadline": "2026-12-01T23:59:59+08:00"
      }
    ]
  },
  "trace_id": "运行过程中生成"
}
```

字段规则：

- 所有 ID 使用字符串；
- 所有时间使用带 `+08:00` 的 RFC 3339 时间；
- 数组按契约规定的顺序返回；
- 没有数据时返回空数组或 `0`，不得使用假数据占位；
- REST 和 WebSocket 推送共用同一个 `data` 结构。

## 四、WebSocket 票据接口

### POST `/api/v1/dashboard/ws-ticket`

权限与 REST 快照相同，请求体为空。

成功响应：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "ticket": "一次性随机票据",
    "expires_at": "2026-11-21T17:30:30+08:00"
  },
  "trace_id": "运行过程中生成"
}
```

票据要求：

- 有效期 30 秒；
- 仅能使用一次；
- 由 Redis 保存，不保存 Access Token；
- 不得把 JWT、Refresh Token 或长期凭据放入 WebSocket URL。

## 五、WebSocket 通道

连接地址：

```text
/ws/dashboard?ticket=<一次性票据>
```

握手时校验票据、`dashboard:view` 和 GLOBAL 数据范围。校验失败返回对应关闭码：

| 场景 | 关闭码 |
| --- | --- |
| 票据无效、过期或重复使用 | `4401` |
| 权限或数据范围失效 | `4403` |
| 服务暂不可用 | `1013` |

消息统一结构：

```json
{
  "type": "DASHBOARD_OVERVIEW",
  "version": 1,
  "generated_at": "2026-11-21T17:30:30+08:00",
  "data": {}
}
```

连接成功后先发送：

```json
{
  "type": "CONNECTED",
  "version": 1,
  "generated_at": "2026-11-21T17:30:00+08:00",
  "data": null
}
```

更新规则：

- 后端每 30 秒聚合一次快照并广播；
- 数据没有实质变化时不要求重复发送，但发送相同快照也属于合法实现；
- WebSocket 认证失效或权限被撤销时主动断开；
- 前端把任意有效消息视为连接存活信号；连续 75 秒没有收到消息时主动断开并重连；
- 断线期间保留最后一次有效快照；
- 重连成功后重新请求 REST 快照，再继续接收更新。

## 六、前端行为

1. 页面首次打开先请求 REST 快照。
2. 获取快照后请求 WebSocket ticket 并建立连接。
3. WebSocket 收到新快照时更新页面，不重新加载整个路由。
4. 连接断开时显示“实时连接中断”，保留最后一次数据并自动重连。
5. 重连采用指数退避，最大间隔不超过 30 秒。
6. 页面至少适配 `1920×1080`、`2560×1440` 和普通笔记本。
7. 不使用轮询替代 WebSocket；REST 只用于首次快照和重连恢复。
8. 底部只显示数据更新时间和数据来源。

## 七、非范围

- 不新增队伍、作品、评审、公示或获奖指标；
- 不新增数据库表；
- 不引入 RabbitMQ；
- 不使用 Socket.IO 或 STOMP；
- 不创建第二套 Axios 实例、Auth Store 或路由守卫；
- 不在 URL、日志或错误响应中记录 WebSocket ticket；
- 不修改其他业务模块的数据口径。
