# 单客户订阅与功能授权体系设计

> 日期：2026-08-02  
> 状态：已确认设计，等待实施计划  
> 目标分支：`dev`

## 1. 背景与目标

系统面向单一学校部署。后续根据甲方付费订阅情况开放不同功能和资源容量，但不建设多租户系统，也不在学校业务前端展示软件即服务、套餐、合同或订阅修订等平台概念。

本设计目标：

1. 新增唯一的 `SYSTEM_ADMIN` 系统管理员；
2. 建立不可变套餐修订和不可变订阅修订；
3. 支持试用、固定期限和长期订阅；
4. 支持套餐立即升级和立即降级；
5. 第一阶段基础功能按模块粗粒度授权；
6. 第二阶段已实现和待实现功能按操作细粒度授权；
7. 第三阶段权限先固化登记，功能开发时再接入；
8. 建立功能增补、功能移除和资源配额覆盖；
9. 达到配额 80% 时告警，达到 100% 时禁止新增；
10. 订阅暂停、到期、终止或降级时保护已经启动的选寝批次；
11. 所有平台操作可审计，系统管理员密码可通过本地脚本重置。

## 2. 明确排除的范围

本项目不开发：

- 多租户数据模型；
- `tenant` 表或业务表 `tenant_id`；
- 租户识别、切换、选择和隔离；
- 子域名或自定义域名识别学校；
- Redis 键和服务器发送事件通道的租户前缀；
- 一套部署同时服务多所学校；
- 在线支付、自动扣费和支付回调；
- 多个系统管理员及系统管理员账号管理；
- 第三阶段未实现功能的空接口或占位页面；
- 自动物理删除历史业务数据；
- 本轮自动备份和恢复。

系统继续采用单实例 Spring Boot、Vue、MySQL、Redis 和服务器发送事件架构。

## 3. 身份与访问边界

### 3.1 身份类型

| 身份代码 | 中文名称 | 范围 |
|---|---|---|
| `SYSTEM_ADMIN` | 系统管理员 | 套餐、订阅、功能授权、配额、平台审计和本人密码 |
| `ADMIN` | 业务管理员 | 当前有效订阅允许的学校业务管理功能 |
| `STUDENT` | 学生 | 当前有效订阅允许的学生端功能 |

### 3.2 系统管理员访问边界

系统管理员只能访问：

```text
/platform/login
/platform/**
/api/v1/platform/**
```

系统管理员不得访问学生、宿舍、批次、组队、选床、分配和学校业务导出接口，也不能通过功能授权给自身获得学校业务能力。

`ADMIN` 和 `STUDENT` 即使知道平台地址，也必须被后端拒绝。

### 3.3 唯一系统管理员

系统只允许存在一个系统管理员：

```text
用户名：system_admin
初始密码：Dormitory@2026
```

固定规则：

- 数据库迁移只保存 BCrypt 哈希；
- `password_change_required` 初始为 `1`；
- 首次登录后只能调用修改本人密码和退出登录接口；
- 修改成功后将 `password_change_required` 设为 `0`；
- 修改成功后撤销当前令牌并要求重新登录；
- 不提供新增、删除、停用或更换系统管理员的页面和接口；
- 数据库约束与启动校验共同保证 `SYSTEM_ADMIN` 不超过一条；
- 忘记密码时使用本地脚本重置；
- 重置后再次设置 `password_change_required=1`；
- 脚本不得输出或保存新密码明文。

## 4. 平台与学校业务前端分离

### 4.1 平台路由

```text
/platform/login
/platform
/platform/plans
/platform/subscription
/platform/features
/platform/quotas
/platform/audit
/platform/profile/password
```

平台控制台展示套餐修订、当前订阅、订阅历史、合同信息、功能覆盖、配额、影响预览、使用率告警和平台审计。

### 4.2 学校业务路由

```text
/login
/admin/**
/student/**
```

学校业务端不得出现：

```text
租户
多租户
软件即服务
套餐
合同
订阅修订
功能权限代码
```

业务端仅显示自然提示：

```text
该功能当前未开通，请联系系统服务方。
当前学生数量已达到服务容量上限。
当前服务已到期，历史数据仍可查看。
当前选寝活动仍可继续完成，但不能新建活动。
```

菜单、路由和按钮根据有效功能隐藏，但后端始终执行最终权限校验。

## 5. 总体执行链

```text
登录身份
  ↓
SYSTEM_ADMIN / ADMIN / STUDENT 身份守卫
  ↓
当前订阅修订
  ↓
套餐修订功能 + 有效增补 - 有效移除
  ↓
服务状态 + 操作类型 + 配额 + 批次启动快照
  ↓
执行业务并写审计
```

认证令牌不增加租户字段。

## 6. 数据模型

从当前正式迁移 V11 之后新增迁移，不修改 V1 至 V11。

### 6.1 `app_user` 扩展

- `user_type` 增加 `SYSTEM_ADMIN`；
- 增加 `password_change_required TINYINT NOT NULL DEFAULT 0`；
- 增加数据库约束或生成列唯一索引，保证最多一个 `SYSTEM_ADMIN`；
- 系统管理员不得关联 `student_id`。

### 6.2 功能目录 `feature_catalog`

| 字段 | 说明 |
|---|---|
| `feature_code` | 程序固化唯一权限代码 |
| `feature_name` | 中文名称 |
| `phase` | `PHASE1`、`PHASE2`、`PHASE3` |
| `scope` | `ADMIN`、`STUDENT`、`SHARED` |
| `granularity` | `MODULE`、`OPERATION` |
| `action_type` | `READ`、`CREATE`、`UPDATE`、`EXECUTE`、`EXPORT`、`CONFIGURE` |
| `risk_level` | `LOW`、`MEDIUM`、`HIGH` |
| `enabled_in_program` | 当前程序版本是否已有真实功能 |
| `sort_order` | 平台展示顺序 |

功能目录随程序版本固化。系统管理员只能将已有功能加入套餐或覆盖授权，不能创建、删除或修改功能代码。

### 6.3 套餐主记录 `subscription_plan`

保存稳定套餐身份：

- `plan_code`；
- `plan_name`；
- `enabled`；
- `created_at`。

套餐主记录不直接保存功能和配额。

### 6.4 不可变套餐修订 `subscription_plan_revision`

保存：

- `plan_id`；
- `revision`；
- 修订名称与说明；
- `enabled`；
- `change_reason`；
- 创建系统管理员；
- 创建时间。

规则：

- `(plan_id, revision)` 唯一；
- 创建后不得覆盖；
- 修改套餐功能或配额必须创建新修订；
- 已有订阅继续引用原修订；
- 停用修订只禁止新的订阅切换，不影响历史订阅。

### 6.5 套餐修订功能 `plan_revision_feature`

保存套餐精确修订包含的功能代码，主键或唯一键为：

```text
(plan_revision_id, feature_code)
```

### 6.6 配额目录 `quota_catalog`

程序固化配额：

```text
MAX_ADMIN_USERS
MAX_STUDENTS
MAX_CAMPUSES
MAX_BUILDINGS
MAX_ROOMS
MAX_BEDS
MAX_BATCHES_PER_YEAR
MAX_CONCURRENT_ACTIVE_BATCHES
MAX_CONCURRENT_SELECTION_USERS
MAX_IMPORT_ROWS_PER_JOB
MAX_EXPORT_ROWS_PER_JOB
MAX_NOTIFICATION_RECIPIENTS
DATA_RETENTION_DAYS
AUDIT_RETENTION_DAYS
MAX_BACKUP_RETENTION_COUNT
```

当前版本实际接入：

```text
MAX_ADMIN_USERS
MAX_STUDENTS
MAX_CAMPUSES
MAX_BUILDINGS
MAX_ROOMS
MAX_BEDS
MAX_BATCHES_PER_YEAR
MAX_CONCURRENT_ACTIVE_BATCHES
MAX_IMPORT_ROWS_PER_JOB
MAX_EXPORT_ROWS_PER_JOB
```

尚无对应业务的通知、备份等配额只登记目录，等第三阶段功能实现时接入。

### 6.7 套餐修订配额 `plan_revision_quota`

保存一个套餐精确修订的配额，唯一键为：

```text
(plan_revision_id, quota_code)
```

### 6.8 稳定订阅主记录 `service_subscription`

系统只建立一个稳定订阅主记录，用于挂载全部订阅修订、功能覆盖和配额覆盖。

字段至少包含：

- `subscription_code`；
- `created_at`。

订阅主记录不直接保存当前套餐、状态和期限。

### 6.9 不可变订阅修订 `service_subscription_revision`

每次创建、续期、升级、降级、暂停、恢复、终止或紧急停止都创建新修订。

字段至少包含：

- `subscription_id`；
- `revision`；
- `plan_revision_id`；
- `subscription_type`：`TRIAL`、`FIXED_TERM`、`LONG_TERM`；
- `service_status`：`TRIAL`、`ACTIVE`、`SUSPENDED`、`EXPIRED`、`TERMINATED`；
- `contract_number`；
- `start_at`；
- `end_at`；
- `signed_at`；
- `emergency_stopped`；
- `change_reason`；
- `remark`；
- `is_current`；
- `created_by`；
- `created_at`。

规则：

- `(subscription_id, revision)` 唯一；
- 同一订阅最多一个 `is_current=1` 修订；
- `LONG_TERM` 的 `end_at` 必须为空；
- `TRIAL` 和 `FIXED_TERM` 必须有结束时间且晚于开始时间；
- 长期订阅不自动进入 `EXPIRED`；
- 修订创建后不得覆盖；
- 状态变化必须在事务中把旧修订设为非当前，并插入新当前修订；
- 平台查询当前服务状态只读取当前修订。

功能和配额覆盖挂在稳定 `service_subscription` 主记录上，因此升级和降级后自动继续保留，无需复制覆盖数据。

### 6.10 功能覆盖 `subscription_feature_override`

覆盖类型：

```text
GRANT
REVOKE
```

字段至少包含：

- `subscription_id`；
- `feature_code`；
- `override_type`；
- `effective_from`；
- `effective_until`，可为空；
- `change_reason`；
- `created_by`；
- `created_at`。

同一时间对同一功能不得同时存在互相冲突的有效覆盖。

### 6.11 配额覆盖 `subscription_quota_override`

字段至少包含：

- `subscription_id`；
- `quota_code`；
- `quota_value`；
- `effective_from`；
- `effective_until`；
- `change_reason`；
- `created_by`；
- `created_at`。

当前有效覆盖替换套餐修订中的对应配额值。

### 6.12 配额告警状态 `service_quota_alert`

用于去重 80% 和超额告警，保存：

- `quota_code`；
- `alert_level`：`WARNING_80`、`EXCEEDED_100`；
- 当前使用量；
- 当前上限；
- 首次发生时间；
- 最近发生时间；
- 是否已恢复。

使用量降回阈值以下后标记恢复；再次达到阈值时可以生成新告警。

### 6.13 批次权限快照 `batch_entitlement_snapshot`

批次从草稿进入第一个正式运行状态前保存：

- `batch_id`；
- `subscription_revision_id`；
- `granted_features_json`；
- `quota_snapshot_json`；
- `captured_at`；
- `snapshot_version`。

规则：

- 一个批次只能有一份启动快照；
- 快照和批次状态在同一事务提交；
- 快照只能用于完成该批次；
- 不得用于创建或复制其他批次；
- 不得用于创建新规则模板或匹配方案；
- 不得开启快照中不存在的增强功能。

### 6.14 平台审计 `platform_audit_log`

与现有学校业务审计分开，记录：

```text
PLAN_CREATE
PLAN_REVISE
SUBSCRIPTION_CREATE
SUBSCRIPTION_RENEW
SUBSCRIPTION_UPGRADE
SUBSCRIPTION_DOWNGRADE
SUBSCRIPTION_SUSPEND
SUBSCRIPTION_RESUME
SUBSCRIPTION_TERMINATE
SUBSCRIPTION_EMERGENCY_STOP
SUBSCRIPTION_EMERGENCY_RESUME
FEATURE_OVERRIDE_ADD
FEATURE_OVERRIDE_REMOVE
QUOTA_OVERRIDE_UPDATE
SYSTEM_ADMIN_PASSWORD_CHANGE
SYSTEM_ADMIN_PASSWORD_RESET
```

字段至少包含操作类型、操作人、目标、原因、变更前后 JSON、请求标识、结果、稳定错误代码和发生时间。

## 7. 订阅生命周期

### 7.1 订阅类型

```text
TRIAL
FIXED_TERM
LONG_TERM
```

长期订阅固定为：

```text
subscription_type = LONG_TERM
end_at = NULL
```

长期订阅不会自动到期，只能人工暂停、恢复或终止。

### 7.2 服务状态

```text
TRIAL
ACTIVE
SUSPENDED
EXPIRED
TERMINATED
```

- `TRIAL`：试用期内正常使用已授权能力；
- `ACTIVE`：正常使用；
- `SUSPENDED`：禁止新业务，但已启动批次依据快照继续；
- `EXPIRED`：固定期限到期后禁止新业务，但已启动批次依据快照继续；
- `TERMINATED`：关闭新业务，默认仍保护已启动批次；
- `emergency_stopped=1`：停止已启动批次的继续权限。

固定期限到期由应用在读取当前订阅时实时判定，并由定时任务幂等生成 `EXPIRED` 新修订；在任务尚未执行时，接口也必须按时间直接视为到期，不能出现延迟开放窗口。

### 7.3 创建订阅

首次初始化创建一条长期订阅，包含当前已实现的第一、第二阶段能力和足以覆盖现有测试数据的配额。

后续平台允许将当前订阅切换为试用、固定期限或长期订阅，但同一时间只能有一个当前订阅修订。

### 7.4 直接升级

升级立即生效：

1. 选择目标套餐修订；
2. 生成影响预览；
3. 填写原因和合同信息；
4. 创建新的当前订阅修订；
5. 原修订变为非当前；
6. 新功能和新配额立即生效；
7. 原功能覆盖和配额覆盖继续有效；
8. 写入平台审计。

本阶段不计算差价，不调用支付网关。

### 7.5 直接降级

降级也立即生效：

1. 预览失去的功能、配额变化、当前使用量和运行中批次；
2. 填写降级原因；
3. 创建新的当前订阅修订；
4. 被取消功能立即禁止发起新操作；
5. 已启动批次依据启动快照继续；
6. 原功能覆盖和配额覆盖继续有效；
7. 存量资源超过新配额时不删除、不停用；
8. 禁止继续新增对应资源，直到使用量回到上限以内；
9. 平台端和业务管理员首页持续显示超额告警；
10. 写入平台审计。

### 7.6 续期

固定期限订阅续期通过创建新订阅修订完成，旧修订的开始和结束时间不修改。

长期订阅不生成年度自动续期记录。

### 7.7 暂停、恢复和终止

暂停、恢复和终止均创建新订阅修订，并要求填写原因。

终止不删除业务数据。未执行紧急停止时，已启动批次仍可完成；系统管理员可以单独执行紧急停止。

## 8. 有效权限计算

```text
当前套餐修订权限
+ 当前有效 GRANT 覆盖
- 当前有效 REVOKE 覆盖
= 当前有效功能
```

最终执行还叠加：

```text
功能是否已在程序实现
当前服务状态
当前操作是读取、新操作还是批次继续操作
批次是否存在有效启动快照
当前配额
紧急停止状态
```

统一后端组件：

```text
FeatureCatalog
FeatureAccessService
FeatureAccessGuard
SubscriptionService
QuotaService
EntitlementSnapshotService
PlatformAuditService
```

统一接口：

```java
featureAccessService.require("P2_BATCH_COPY");
featureAccessService.has("P2_RULE_TEMPLATE_REVISE");
featureAccessService.currentFeatures();
quotaService.requireAvailable("MAX_STUDENTS", 1);
```

控制器和业务服务不得自行拼接订阅判断。

权限守卫区分：

```text
READ_EXISTING
START_NEW
CONTINUE_EXISTING_BATCH
```

- 暂停、到期、终止和存量超额时仍允许历史读取；
- 创建、复制、发布和新执行操作使用当前权限；
- 已启动批次完成动作可以使用该批次快照；
- 快照不得扩大到批次外操作。

## 9. 权限目录

### 9.1 第一阶段模块级权限

```text
P1_IDENTITY_BASIC
P1_DORMITORY_BASIC
P1_BATCH_BASIC
P1_PREFERENCE_BASIC
P1_SELF_SELECTION
P1_TEAM_SELECTION
P1_RANDOM_RECOMMENDATION
P1_UNIFIED_ALLOCATION
P1_ASSIGNMENT_MANAGEMENT
P1_REALTIME_STATUS
P1_BASIC_EXPORT_AUDIT
```

第一阶段模块开启后，模块内部现有标准操作整体开放。

### 9.2 第二阶段操作级权限

#### 宿舍布局与床位

```text
P2_ROOM_LAYOUT_VIEW
P2_ROOM_LAYOUT_UPDATE
P2_BED_TYPE_UPDATE
P2_BED_OPERATIONAL_STATUS_UPDATE
P2_THREE_DIMENSIONAL_SELECTION
```

#### 匹配运营

```text
P2_MATCHING_SCHEME_VIEW
P2_MATCHING_SCHEME_CREATE
P2_MATCHING_SCHEME_REVISE
P2_MATCHING_SCHEME_ACTIVATE
P2_MATCHING_CONFLICT_RULE_CONFIGURE
P2_RECOMMENDATION_EXPLANATION
```

#### 学生体验与国际化

```text
P2_WELCOME_MESSAGE_VIEW
P2_WELCOME_MESSAGE_UPDATE
P2_MULTILINGUAL_INTERFACE
P2_STUDENT_CONTACT_SELF_UPDATE
P2_STUDENT_NOTIFICATION_VIEW
```

#### 组队增强

```text
P2_TEAM_FIVE_MEMBER
P2_TEAM_MEMBER_REMOVE
P2_TEAM_MEMBER_LEAVE
P2_TEAM_INVITATION_CANCEL
P2_TEAM_PERSONAL_SELECTION_SWITCH
P2_TEAM_NOTIFICATION
```

#### 批次复用与规则

```text
P2_BATCH_COPY
P2_RULE_TEMPLATE_VIEW
P2_RULE_TEMPLATE_CREATE
P2_RULE_TEMPLATE_REVISE
P2_RULE_TEMPLATE_SET_DEFAULT
P2_BATCH_USE_EXPLICIT_RULE_REVISION
```

#### 数据导入质量

```text
P2_IMPORT_PRECHECK
P2_IMPORT_COMMIT
P2_IMPORT_ERROR_VIEW
P2_IMPORT_ERROR_EXPORT
P2_IMPORT_IDEMPOTENCY
P2_IMPORT_ROLLBACK
```

#### 安全、统计与审计

```text
P2_SENSITIVE_DATA_VIEW
P2_SENSITIVE_DATA_EXPORT
P2_EXPORT_DESENSITIZATION
P2_AUDIT_ADVANCED_QUERY
P2_AUDIT_EXPORT
P2_OPERATION_STATISTICS
P2_BED_UTILIZATION_STATISTICS
P2_UNSELECTED_STUDENT_STATISTICS
P2_MANUAL_ADJUSTMENT_STATISTICS
P2_EXCEPTION_WORKBENCH
```

#### 性能与恢复

```text
P2_CONCURRENT_SELECTION_LIMIT
P2_REDIS_RECOVERY
P2_SELECTION_PRESSURE_TEST
P2_SLOW_QUERY_ANALYSIS
P2_OPERATION_HEALTH_VIEW
```

#### 分配优化与公平性

```text
P2_ALLOCATION_OPTIMIZED_PREVIEW
P2_ALLOCATION_OPTIMIZED_EXECUTE
P2_ALLOCATION_LOCAL_SWAP
P2_FAIRNESS_METRIC_VIEW
P2_FAIRNESS_COMPARISON
P2_ALLOCATION_EXPERIMENT_EXPORT
```

### 9.3 第三阶段操作级权限

第三阶段权限写入目录，但 `enabled_in_program=0`，待功能实现时接入。

#### 换寝

```text
P3_ROOM_CHANGE_REQUEST
P3_ROOM_CHANGE_REVIEW
P3_ROOM_CHANGE_APPROVE
P3_ROOM_CHANGE_REJECT
P3_ROOM_CHANGE_EXECUTE
P3_ROOM_CHANGE_HISTORY
```

#### 候补补位

```text
P3_WAITLIST_JOIN
P3_WAITLIST_EXIT
P3_WAITLIST_RULE_CONFIGURE
P3_WAITLIST_OFFER
P3_WAITLIST_ACCEPT
P3_WAITLIST_MANUAL_ASSIGN
P3_WAITLIST_HISTORY
```

#### 通知平台

```text
P3_NOTIFICATION_TEMPLATE_VIEW
P3_NOTIFICATION_TEMPLATE_MANAGE
P3_NOTIFICATION_SEND
P3_NOTIFICATION_SCHEDULE
P3_NOTIFICATION_DELIVERY_STATUS
P3_NOTIFICATION_CHANNEL_CONFIGURE
```

#### 历史分析

```text
P3_HISTORICAL_DASHBOARD
P3_CROSS_BATCH_COMPARISON
P3_TREND_ANALYSIS
P3_CUSTOM_REPORT_EXPORT
P3_DATA_RETENTION_QUERY
```

#### 移动端

```text
P3_MOBILE_STUDENT_ACCESS
P3_MOBILE_ADMIN_ACCESS
P3_MOBILE_PUSH_NOTIFICATION
```

#### 灾难恢复

```text
P3_BACKUP_VIEW
P3_BACKUP_CREATE
P3_BACKUP_POLICY_CONFIGURE
P3_RESTORE_PRECHECK
P3_RESTORE_EXECUTE
P3_DISASTER_RECOVERY_DRILL
P3_RECOVERY_REPORT
```

## 10. 配额策略

### 10.1 有效配额

```text
当前套餐修订配额
→ 当前有效配额覆盖替换对应值
= 当前有效配额
```

新增前统一检查：

```text
当前使用量 + 本次新增量 <= 当前有效配额
```

### 10.2 告警与拒绝

- 达到 80% 时生成去重告警；
- 达到或超过 100% 时禁止新增；
- 允许查询、修改非扩容字段、停用和删除；
- 重新启用会占用配额的资源时重新检查；
- 降级后存量超额允许保留，但持续告警并禁止扩容。

稳定错误代码：

```text
SERVICE_QUOTA_EXCEEDED
ADMIN_QUOTA_EXCEEDED
STUDENT_QUOTA_EXCEEDED
BATCH_QUOTA_EXCEEDED
IMPORT_ROW_QUOTA_EXCEEDED
EXPORT_ROW_QUOTA_EXCEEDED
```

错误详情至少包含：

```json
{
  "quotaCode": "MAX_STUDENTS",
  "limit": 10000,
  "used": 10000,
  "requested": 1
}
```

## 11. 批次运行保护

### 11.1 快照时机

批次进入第一个正式运行状态前，在同一事务中：

1. 校验当前服务状态；
2. 校验发布所需功能；
3. 校验活动批次配额；
4. 保存批次权限快照；
5. 更新批次状态；
6. 写业务审计。

### 11.2 订阅变化后的允许行为

暂停、到期、终止或降级后，已启动批次允许：

- 查看批次；
- 提交该批次问卷；
- 个人选床占用、释放和确认；
- 队伍邀请确认、锁定、占用、释放和确认；
- 管理员完成统一分配和必要收尾；
- 查看该批次结果和历史。

禁止：

- 新建或复制批次；
- 发布尚未启动的批次；
- 创建规则模板或新修订；
- 创建匹配方案或新修订；
- 使用快照开启启动时未授权的增强功能；
- 将快照用于其他批次。

### 11.3 紧急停止

系统管理员可显式紧急停止：

- 必须填写原因；
- 先展示运行中批次数和影响学生数；
- 创建新的订阅修订并设置 `emergency_stopped=1`；
- 写入平台审计；
- 不删除 Redis 或数据库事实；
- 解除紧急停止同样创建新修订并审计。

## 12. OpenAPI 与后端边界

所有新增接口必须 OpenAPI 优先，再生成 Java 接口、数据传输对象和 TypeScript 类型。控制器只实现生成接口，不手写对外路由和请求模型。

平台接口建议拆分：

```text
backend-java/model/src/main/resources/platform/openapi-platform-auth.yaml
backend-java/model/src/main/resources/platform/openapi-platform-plan.yaml
backend-java/model/src/main/resources/platform/openapi-platform-subscription.yaml
backend-java/model/src/main/resources/platform/openapi-platform-entitlement.yaml
backend-java/model/src/main/resources/platform/openapi-platform-audit.yaml
```

平台接口包括：

- 系统管理员登录和退出；
- 修改本人密码；
- 查询套餐与修订；
- 创建套餐和套餐新修订；
- 查询当前订阅和历史；
- 创建订阅；
- 续期；
- 直接升级；
- 直接降级；
- 暂停、恢复和终止；
- 紧急停止和解除；
- 订阅影响预览；
- 功能覆盖管理；
- 配额覆盖管理；
- 配额使用率；
- 平台审计查询。

`CurrentUser` 支持：

```text
SYSTEM_ADMIN
ADMIN
STUDENT
passwordChangeRequired
```

平台登录和业务登录使用独立接口，并执行双向身份隔离。

## 13. 本地密码重置脚本

新增：

```text
scripts/admin/reset_system_admin_password.py
```

要求：

- 从命令行参数或交互式隐藏输入读取新密码；
- 校验密码复杂度；
- 使用与后端一致的 BCrypt 参数；
- 只更新唯一系统管理员；
- 设置 `password_change_required=1`；
- 清理现有系统管理员登录令牌；
- 写平台密码重置审计；
- 数据库和 Redis 连接来自环境变量；
- 不打印新密码。

## 14. 前端授权数据

业务认证信息接口返回：

- 当前用户身份；
- 当前有效功能代码；
- 服务是否允许新操作；
- 业务友好提示；
- 配额告警摘要。

不得向业务前端返回合同编号、套餐主键、套餐修订、平台审计和系统管理员信息。

前端统一提供：

```text
useFeatureAccess()
FeatureGate
路由元数据 requiredFeature
按钮级 requiredFeature
```

## 15. 稳定错误代码

```text
PLATFORM_ADMIN_REQUIRED
BUSINESS_USER_REQUIRED
SYSTEM_ADMIN_PASSWORD_CHANGE_REQUIRED
FEATURE_NOT_ENABLED
SERVICE_SUSPENDED
SERVICE_EXPIRED
SERVICE_TERMINATED
SERVICE_EMERGENCY_STOPPED
SUBSCRIPTION_NOT_FOUND
SUBSCRIPTION_VERSION_CONFLICT
PLAN_REVISION_DISABLED
PLAN_REVISION_IMMUTABLE
FEATURE_OVERRIDE_CONFLICT
QUOTA_OVERRIDE_INVALID
SERVICE_QUOTA_EXCEEDED
BATCH_ENTITLEMENT_SNAPSHOT_MISSING
```

业务前端将平台型错误代码映射为自然中文提示。

## 16. 初始化迁移

迁移完成后现有业务必须继续可用。

初始化内容：

1. 创建唯一 `system_admin`；
2. 设置初始密码 BCrypt 哈希和首次强制改密；
3. 创建第一、第二阶段完整功能目录；
4. 创建第三阶段目录并标记 `enabled_in_program=0`；
5. 创建系统默认套餐和修订；
6. 默认套餐包含当前已实现的第一、第二阶段功能；
7. 创建稳定订阅主记录；
8. 创建一条长期有效的当前订阅修订；
9. 配置足以覆盖现有测试数据的默认配额；
10. 不修改现有学生、宿舍、批次和分配数据。

## 17. 测试与验收

### 17.1 身份与密码

- 系统管理员无法访问业务接口；
- 管理员和学生无法访问平台接口；
- 首次密码未修改时平台操作全部拒绝；
- 修改密码后旧令牌失效；
- 数据库不能创建第二个系统管理员；
- 本地重置脚本只更新唯一系统管理员。

### 17.2 套餐与订阅

- 套餐修订不可覆盖；
- 停用修订不能用于新切换；
- 订阅状态变化产生新修订；
- 同一时间只有一个当前订阅修订；
- 长期订阅无结束时间且不会自动到期；
- 固定期限到期无延迟开放窗口；
- 升级立即开放新功能；
- 降级立即关闭新操作；
- 升降级保留功能和配额覆盖；
- 降级后存量超额但不能新增；
- 暂停、恢复、终止和紧急停止均有平台审计。

### 17.3 功能接入

- 第一阶段模块关闭后对应接口组拒绝；
- 第二阶段每项已实现操作可独立开关；
- 第三阶段未实现权限不能产生可用功能；
- 前端菜单、路由和按钮与权限一致；
- 直接调用隐藏接口仍被后端拒绝；
- 当前所有管理端和学生端接口都有明确权限归属。

### 17.4 配额

- 80% 告警去重；
- 100% 禁止新增；
- 非扩容修改、停用和删除允许；
- 重新启用时重新检查；
- 年度批次、活动批次、导入和导出计量准确；
- 降级超额持续告警；
- 使用量恢复后告警状态正确恢复。

### 17.5 批次快照

- 批次启动生成唯一快照；
- 快照和状态同一事务提交；
- 暂停、到期、终止和降级后已启动批次可完成；
- 未启动批次不能发布；
- 快照不能用于其他批次；
- 紧急停止阻止继续操作；
- 解除紧急停止后按原快照恢复。

### 17.6 工程门禁

本地实际执行：

- 数据库静态测试；
- Flyway 空库迁移和 V11 升级迁移；
- 固化 `schema.sql` 生成和漂移检查；
- OpenAPI 引用、操作编号和生成测试；
- Maven 全模块 `clean verify`；
- TypeScript、Vue 和 Vite 生产构建；
- MySQL、Redis、Spring Boot 真实启动；
- 第一阶段完整回归；
- 第二阶段当前真实接口回归；
- 平台订阅、授权、配额和批次快照真实接口验收。

GitHub Actions 不可用时，不得依据源码审查声称运行验证通过。

## 18. 本轮实施范围

本轮实现：

1. 唯一系统管理员和独立平台登录；
2. 首次强制改密和本地重置脚本；
3. 固化功能与配额目录；
4. 不可变套餐修订；
5. 不可变订阅修订；
6. 试用、固定期限和长期订阅；
7. 直接升级和直接降级；
8. 功能增补、移除和配额覆盖；
9. 第一阶段模块级权限接入；
10. 第二阶段当前功能操作级权限接入；
11. 第三阶段权限目录登记；
12. 配额告警和新增拒绝；
13. 批次权限快照；
14. 独立平台控制台；
15. 平台审计和业务端自然提示。

## 19. 决策摘要

| 决策 | 结果 |
|---|---|
| 部署客户 | 单一学校、单一客户 |
| 多租户 | 不开发 |
| 系统管理员 | 唯一一个 `SYSTEM_ADMIN` |
| 初始凭据 | `system_admin` / `Dormitory@2026`，首次强制改密 |
| 支付 | 线下合同，平台手工维护 |
| 套餐 | 稳定主记录 + 不可变修订 |
| 订阅 | 稳定主记录 + 不可变修订 |
| 长期订阅 | `end_at=NULL`，仅人工暂停或终止 |
| 升级 | 立即生效 |
| 降级 | 立即生效 |
| 覆盖项 | 升降级后继续保留 |
| 降级超额 | 保留存量、禁止新增、持续告警 |
| 第一阶段权限 | 模块级 |
| 第二阶段权限 | 操作级 |
| 第三阶段权限 | 仅目录登记，开发时接入 |
| 运行中批次 | 使用启动快照继续完成 |
| 紧急停止 | 系统管理员显式操作 |
| 业务端展示 | 不出现套餐、订阅和软件即服务概念 |
