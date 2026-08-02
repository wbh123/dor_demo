# 单客户订阅与功能授权体系设计

> 日期：2026-08-02  
> 状态：已确认设计，等待实施计划  
> 目标分支：`dev`

## 1. 背景与目标

当前系统面向单一学校提供宿舍选择服务。后续需要根据甲方付费订阅情况开放不同功能和资源容量，但不建设多租户系统，也不在业务前端展示软件即服务、租户、套餐或合同等平台概念。

本设计目标：

1. 新增独立的单一系统管理员身份；
2. 建立不可变套餐修订、长期订阅、固定期限订阅、直接升级和直接降级；
3. 将第一阶段基础能力按模块授权；
4. 将第二、第三阶段增强能力按具体操作授权；
5. 建立可覆盖的功能授权和资源配额；
6. 订阅暂停、到期或降级时保护已经启动的选寝批次；
7. 在不引入多租户字段、不改造全部业务查询的前提下完成统一授权；
8. 所有平台操作可追溯、可审计、可本地恢复系统管理员密码。

## 2. 明确取消的范围

以下能力不开发：

- 多租户数据模型；
- `tenant` 表和所有业务表的 `tenant_id`；
- 租户识别、租户选择和租户切换；
- 子域名或自定义域名识别学校；
- 跨租户隔离和跨租户越权测试；
- Redis 键、服务器发送事件通道增加租户编号；
- 一套部署同时服务多所学校；
- 在线支付、微信支付、支付宝和自动支付回调；
- 多个系统管理员及系统管理员账号管理；
- 第三阶段未实现功能的空接口和占位页面。

系统保持现有单实例 Spring Boot、Vue、MySQL、Redis 和服务器发送事件架构。

## 3. 身份与访问边界

### 3.1 身份类型

系统保留三种身份：

| 身份代码 | 中文名称 | 业务范围 |
|---|---|---|
| `SYSTEM_ADMIN` | 系统管理员 | 套餐、订阅、功能授权、配额、平台审计和本人密码 |
| `ADMIN` | 业务管理员 | 当前订阅授权范围内的学校业务管理 |
| `STUDENT` | 学生 | 当前订阅授权范围内的学生端业务 |

### 3.2 系统管理员边界

系统管理员只能访问：

```text
/platform/login
/platform/**
/api/v1/platform/**
```

系统管理员不得：

- 查询学生档案；
- 查询或修改宿舍资源；
- 查询或修改选寝批次；
- 参加选寝或组队；
- 执行统一分配；
- 导出学校业务数据；
- 通过授权给自身获得学校业务能力。

`ADMIN` 和 `STUDENT` 即使知道平台地址，也必须被后端拒绝。

### 3.3 单一系统管理员

系统只允许存在一个 `SYSTEM_ADMIN`：

```text
用户名：system_admin
初始密码：Dormitory@2026
```

固定规则：

- 由数据库迁移写入 BCrypt 密码哈希，不保存明文密码；
- `password_change_required` 初始为 `1`；
- 首次登录后只能访问本人密码修改接口；
- 修改密码成功后设置为 `0`；
- 修改成功后撤销当前令牌并要求重新登录；
- 不提供新增、删除、停用或更换系统管理员的页面和接口；
- 数据库约束和启动校验保证 `SYSTEM_ADMIN` 数量不超过一条；
- 忘记密码时通过本地脚本重置密码哈希；
- 重置脚本接收新密码参数，不在仓库中保存新密码明文。

## 4. 平台与业务前端分离

### 4.1 平台入口

平台前端路由：

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

平台控制台展示：

- 套餐及修订；
- 当前订阅；
- 合同编号和服务期限；
- 功能增补和移除；
- 配额和使用率；
- 订阅影响预览；
- 平台审计；
- 系统管理员密码修改。

### 4.2 业务前端

业务前端继续使用：

```text
/login
/admin/**
/student/**
```

业务前端不得出现：

```text
租户
多租户
软件即服务
套餐
合同
权限代码
订阅修订
```

业务端只显示自然语言提示：

```text
该功能当前未开通，请联系系统服务方。
当前学生数量已达到服务容量上限。
当前服务已到期，历史数据仍可查看。
当前选寝活动仍可继续完成，但不能新建活动。
```

菜单、路由和按钮根据有效权限隐藏，但隐藏只用于改善体验，后端始终是最终权限裁决者。

## 5. 数据模型

建议从当前正式迁移 V11 之后新增迁移，预计至少包含 V12 和 V13，不修改 V1 至 V11。

### 5.1 功能目录

#### `feature_catalog`

| 字段 | 说明 |
|---|---|
| `feature_code` | 程序固化的唯一权限代码 |
| `feature_name` | 中文名称 |
| `phase` | `PHASE1`、`PHASE2`、`PHASE3` |
| `scope` | `ADMIN`、`STUDENT`、`SHARED` |
| `granularity` | `MODULE`、`OPERATION` |
| `action_type` | `READ`、`CREATE`、`UPDATE`、`EXECUTE`、`EXPORT`、`CONFIGURE` |
| `risk_level` | `LOW`、`MEDIUM`、`HIGH` |
| `enabled_in_program` | 当前版本是否已有实际功能 |
| `sort_order` | 平台展示顺序 |

权限目录随程序版本固化。系统管理员只能选择已有权限，不能新增、删除或修改权限代码。

### 5.2 套餐与不可变修订

#### `subscription_plan`

保存套餐稳定身份：

- 套餐编码；
- 套餐显示名称；
- 是否允许新订阅选择；
- 创建时间。

#### `subscription_plan_revision`

保存不可变修订：

- `plan_id`；
- `revision`；
- 修订名称和说明；
- `enabled`；
- `change_reason`；
- 创建时间。

固定规则：

- 同一套餐修订号唯一；
- 修订创建后不得覆盖；
- 修改套餐功能或配额必须创建新修订；
- 已有订阅继续引用原修订；
- 停用修订只禁止新的订阅切换，不影响历史订阅。

#### `plan_revision_feature`

保存一个套餐修订包含的功能权限。

#### `plan_revision_quota`

保存一个套餐修订的资源配额。

### 5.3 服务订阅

#### `service_subscription`

| 字段 | 说明 |
|---|---|
| `subscription_code` | 订阅编码 |
| `plan_revision_id` | 当前套餐精确修订 |
| `subscription_type` | `TRIAL`、`FIXED_TERM`、`LONG_TERM` |
| `service_status` | `TRIAL`、`ACTIVE`、`SUSPENDED`、`EXPIRED`、`TERMINATED` |
| `contract_number` | 合同编号，可为空 |
| `start_at` | 服务开始时间 |
| `end_at` | 固定期限结束时间；长期订阅为空 |
| `signed_at` | 合同签署时间，可为空 |
| `change_reason` | 创建或切换原因 |
| `remark` | 备注 |
| `version` | 乐观锁版本 |
| `created_at` | 创建时间 |

固定规则：

- 系统同一时间最多只有一条当前生效订阅；
- `LONG_TERM` 的 `end_at` 必须为空；
- `FIXED_TERM` 和 `TRIAL` 必须有合法结束时间；
- 长期订阅不会自动进入 `EXPIRED`；
- 订阅历史不能物理覆盖；
- 升级、降级、续期和状态变化必须保留历史事件与审计。

建议使用订阅主记录加事件历史表，或每次切换创建新订阅修订并关闭旧修订。实施计划必须选择一种方式并确保当前生效记录唯一。

### 5.4 功能覆盖

#### `subscription_feature_override`

支持两种类型：

```text
GRANT
REVOKE
```

字段至少包含：

- 权限代码；
- 覆盖类型；
- 生效时间；
- 失效时间，可为空；
- 调整原因；
- 创建时间；
- 创建系统管理员。

升级和降级时，原有仍在有效期内的功能覆盖全部保留。

### 5.5 配额目录与覆盖

#### `quota_catalog`

程序固化的首批配额：

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

其中当前版本必须实际接入：

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

尚无对应业务的通知、备份等配额只登记目录，待第三阶段功能实现时接入。

#### `subscription_quota_override`

允许系统管理员对当前服务单独提高或降低配额。字段至少包含：

- 配额代码；
- 覆盖值；
- 生效时间；
- 失效时间；
- 原因；
- 创建时间。

### 5.6 批次权限快照

#### `batch_entitlement_snapshot`

在选寝批次从草稿进入正式运行状态前保存：

- `batch_id`；
- `subscription_id` 或订阅修订标识；
- 生效功能代码 JSON；
- 关键配额 JSON；
- 捕获时间；
- 快照版本。

一个批次只能存在一份正式启动快照。快照只能用于完成该批次，不得用于创建新批次、复制批次、创建新模板或新匹配方案。

### 5.7 平台审计

#### `platform_audit_log`

与现有学校业务审计分开，记录：

```text
SUBSCRIPTION_CREATE
SUBSCRIPTION_RENEW
SUBSCRIPTION_UPGRADE
SUBSCRIPTION_DOWNGRADE
SUBSCRIPTION_SUSPEND
SUBSCRIPTION_RESUME
SUBSCRIPTION_TERMINATE
SUBSCRIPTION_EMERGENCY_STOP
FEATURE_OVERRIDE_ADD
FEATURE_OVERRIDE_REMOVE
QUOTA_OVERRIDE_UPDATE
PLAN_CREATE
PLAN_REVISE
SYSTEM_ADMIN_PASSWORD_CHANGE
SYSTEM_ADMIN_PASSWORD_RESET
```

平台审计至少保存：

- 操作类型；
- 操作人；
- 目标类型和目标主键；
- 操作原因；
- 变更前 JSON；
- 变更后 JSON；
- 请求标识；
- 操作时间；
- 是否成功；
- 稳定错误代码。

## 6. 订阅生命周期

### 6.1 订阅类型

```text
TRIAL
FIXED_TERM
LONG_TERM
```

长期订阅：

```text
subscription_type = LONG_TERM
end_at = NULL
```

长期订阅不会自动到期，只能由系统管理员暂停、恢复或终止。

### 6.2 服务状态

```text
TRIAL
ACTIVE
SUSPENDED
EXPIRED
TERMINATED
```

规则：

- `TRIAL`：试用期内按订阅权限工作；
- `ACTIVE`：正常工作；
- `SUSPENDED`：禁止发起新业务，但保护已启动批次；
- `EXPIRED`：固定期限自动到期，禁止发起新业务，但保护已启动批次；
- `TERMINATED`：关闭新业务；默认仍允许已启动批次依据快照完成；
- 系统管理员显式执行紧急停止时，才停止已启动批次的继续权限。

### 6.3 直接升级

升级立即生效：

1. 选择目标套餐修订；
2. 生成影响预览；
3. 系统管理员填写原因和合同信息；
4. 切换当前套餐精确修订；
5. 新功能和新配额立即生效；
6. 原有功能覆盖和配额覆盖继续保留；
7. 写入平台审计。

本阶段不计算差价，不调用支付网关，费用由线下合同处理。

### 6.4 直接降级

降级也立即生效：

1. 生成将失去的功能、配额变化、当前使用量和运行中批次影响；
2. 系统管理员填写降级原因；
3. 切换到目标套餐修订；
4. 被取消的功能立即禁止发起新操作；
5. 已启动批次依据启动快照继续完成；
6. 原有覆盖继续保留；
7. 存量资源超过新配额时不删除、不停用；
8. 禁止继续新增对应资源，直到使用量回到配额以内；
9. 业务端和平台端持续显示超额告警；
10. 写入平台审计。

### 6.5 续期

固定期限订阅支持人工续期。续期不得覆盖历史结束时间，应记录新的服务期限和审计事件。

长期订阅不生成年度自动续期记录。

## 7. 有效权限计算

当前有效权限：

```text
当前套餐精确修订权限
+ 当前有效 GRANT 覆盖
- 当前有效 REVOKE 覆盖
= 当前有效权限
```

最终执行还需要判断：

```text
服务状态
权限是否已在程序中实现
是否属于读取或新操作
是否存在对应批次启动快照
资源配额
紧急停止状态
```

后端新增统一组件：

```text
FeatureCatalog
FeatureAccessService
FeatureAccessGuard
SubscriptionService
QuotaService
EntitlementSnapshotService
PlatformAuditService
```

统一接口示例：

```java
featureAccessService.require("P2_BATCH_COPY");
featureAccessService.has("P2_RULE_TEMPLATE_REVISE");
featureAccessService.currentFeatures();
quotaService.requireAvailable("MAX_STUDENTS", 1);
```

控制器和业务服务不得自行拼接订阅判断。

### 7.1 读取与新操作

权限守卫需要区分：

```text
READ_EXISTING
START_NEW
CONTINUE_EXISTING_BATCH
```

- 历史数据读取在暂停、到期和存量超额时仍可使用；
- 创建、复制、发布和新执行操作必须使用当前权限；
- 已启动批次完成动作可以使用该批次权限快照；
- 快照不得扩大到批次外操作。

## 8. 权限目录

### 8.1 第一阶段模块级权限

第一阶段基础闭环采用粗粒度模块授权：

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

模块开通后，模块内部现有标准操作整体开放。

### 8.2 第二阶段操作级权限

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

### 8.3 第三阶段操作级权限

第三阶段权限写入固化目录，但 `enabled_in_program=0`，待对应功能开发时接入。

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

## 9. 配额策略

### 9.1 有效配额

```text
当前套餐精确修订配额
→ 被当前有效订阅配额覆盖替换
= 当前有效配额
```

每次新增资源前统一执行：

```text
当前使用量 + 本次新增量 <= 当前有效配额
```

### 9.2 预警和拒绝

- 使用率达到 80% 时生成告警；
- 平台控制台和业务管理员首页展示告警；
- 告警去重，避免每次请求重复创建；
- 达到或超过 100% 时禁止新增；
- 允许查询、修改非扩容字段、停用和删除；
- 重新启用会占用配额的资源时重新校验；
- 配额拒绝和重要告警写入审计或运行记录。

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

## 10. 批次运行保护

### 10.1 快照时机

批次从草稿进入第一个正式运行状态前，在同一数据库事务中：

1. 校验当前服务状态；
2. 校验发布所需当前权限；
3. 校验活动批次配额；
4. 保存权限和关键配额快照；
5. 更新批次状态；
6. 写业务审计。

### 10.2 到期、暂停和降级后的行为

允许继续：

- 学生查看正在运行批次；
- 提交该批次问卷；
- 个人选寝临时占用、释放和确认；
- 队伍邀请确认、锁定、临时占用和确认；
- 管理员完成该批次统一分配和必要收尾；
- 查看该批次结果和历史。

禁止：

- 新建批次；
- 复制批次；
- 发布尚未启动的批次；
- 创建新规则模板或新修订；
- 创建新匹配方案或新修订；
- 使用快照开启该批次启动时未包含的增强功能；
- 将快照用于其他批次。

### 10.3 紧急停止

系统管理员可以显式执行紧急停止：

- 必须填写原因；
- 必须显示正在运行批次数量和影响学生数量；
- 写入 `SUBSCRIPTION_EMERGENCY_STOP` 平台审计；
- 业务接口返回稳定的服务停止错误；
- 不删除 Redis 或数据库事实，由业务恢复流程处理。

## 11. 接口与后端边界

### 11.1 OpenAPI 优先

所有新增接口先修改 OpenAPI，再生成 Java 接口、数据传输对象和 TypeScript 类型。控制器只实现生成接口，不手写对外路由或请求模型。

### 11.2 平台接口

建议分片：

```text
backend-java/model/src/main/resources/platform/openapi-platform-auth.yaml
backend-java/model/src/main/resources/platform/openapi-platform-plan.yaml
backend-java/model/src/main/resources/platform/openapi-platform-subscription.yaml
backend-java/model/src/main/resources/platform/openapi-platform-entitlement.yaml
backend-java/model/src/main/resources/platform/openapi-platform-audit.yaml
```

平台接口至少包括：

- 系统管理员登录；
- 修改本人密码；
- 查询套餐和修订；
- 创建套餐；
- 创建套餐新修订；
- 查询当前订阅和历史；
- 创建订阅；
- 续期；
- 直接升级；
- 直接降级；
- 暂停；
- 恢复；
- 终止；
- 紧急停止和解除；
- 查询订阅影响预览；
- 功能覆盖管理；
- 配额覆盖管理；
- 配额使用率；
- 平台审计查询。

### 11.3 认证上下文

`CurrentUser` 增加或支持：

```text
SYSTEM_ADMIN
ADMIN
STUDENT
passwordChangeRequired
```

认证令牌中不增加租户字段。

平台和业务认证可以复用令牌机制，但必须使用不同登录接口和严格身份守卫。

## 12. 本地密码重置脚本

新增本地脚本，例如：

```text
scripts/admin/reset_system_admin_password.py
```

要求：

- 通过命令行参数或交互式隐藏输入读取新密码；
- 校验密码长度和复杂度；
- 使用与后端一致的 BCrypt 成本参数生成哈希；
- 只更新唯一 `SYSTEM_ADMIN`；
- 设置 `password_change_required=1`；
- 清理该账号现有 Redis 登录令牌；
- 写入平台密码重置审计；
- 不在日志打印新密码；
- 数据库连接信息来自环境变量。

## 13. 安全与错误处理

稳定错误代码至少包括：

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

所有拒绝响应不得泄漏内部数据库结构或密码信息。

## 14. 前端授权数据

业务认证信息接口返回：

- 用户身份；
- 有效功能代码；
- 服务可操作状态；
- 业务友好提示；
- 配额告警摘要。

不得向业务前端返回：

- 合同编号；
- 套餐内部主键；
- 套餐修订细节；
- 平台审计；
- 系统管理员信息。

前端需要统一：

```text
useFeatureAccess()
FeatureGate
路由元数据 requiredFeature
按钮级 requiredFeature
```

后端拒绝后，前端显示自然业务提示，而不是权限代码。

## 15. 迁移与默认授权

迁移完成后，现有业务必须继续可用，不能因为引入订阅而突然关闭。

初始化内容：

1. 创建唯一系统管理员；
2. 创建第一阶段和第二阶段完整功能目录；
3. 创建第三阶段未实现功能目录，标记 `enabled_in_program=0`；
4. 创建系统默认套餐和不可变修订；
5. 默认套餐包含当前已实现的第一、第二阶段功能；
6. 创建一条长期有效的当前订阅；
7. 配置足以覆盖现有测试数据的默认配额；
8. 设置系统管理员首次登录强制改密；
9. 不修改现有学生、宿舍、批次和分配数据归属。

## 16. 测试与验收

### 16.1 身份隔离

- 系统管理员无法访问业务接口；
- 业务管理员和学生无法访问平台接口；
- 初始密码未修改时，只允许修改本人密码；
- 修改密码后旧令牌失效；
- 数据库无法创建第二个系统管理员；
- 本地重置脚本只重置唯一系统管理员。

### 16.2 套餐与订阅

- 套餐修订不可覆盖；
- 停用修订不能用于新切换；
- 长期订阅结束时间为空且不会自动到期；
- 固定期限订阅按时间到期；
- 升级立即开放新功能；
- 降级立即禁止新操作；
- 升降级保留功能和配额覆盖；
- 降级后存量超额但不能新增；
- 暂停、恢复、终止和紧急停止有完整审计；
- 当前有效订阅最多一条。

### 16.3 权限接入

- 第一阶段模块关闭后，对应接口组全部拒绝；
- 第二阶段每项已实现操作可以独立开关；
- 第三阶段未实现权限不能授权出可用功能；
- 前端菜单、路由和按钮与权限一致；
- 直接调用隐藏接口仍被后端拒绝；
- 所有当前已存在的管理和学生接口都有明确权限归属。

### 16.4 配额

- 80% 产生一次告警；
- 100% 禁止新增；
- 非扩容修改、停用和删除仍允许；
- 重新启用时重新检查；
- 年度批次、活动批次、导入和导出配额准确；
- 降级后的超额状态持续告警。

### 16.5 批次快照

- 批次启动时生成唯一快照；
- 快照与批次状态在同一事务提交；
- 到期、暂停和降级后已启动批次可完成；
- 未启动批次不能发布；
- 快照不能用于其他批次；
- 紧急停止可以阻止继续操作；
- 解除紧急停止后按原快照恢复。

### 16.6 工程门禁

本地必须实际执行：

- 数据库静态测试；
- Flyway 空库迁移和 V11 升级迁移；
- 固化 `schema.sql` 生成和漂移检查；
- OpenAPI 引用、操作编号和生成测试；
- Maven 全模块 `clean verify`；
- TypeScript、Vue 和 Vite 生产构建；
- MySQL、Redis、Spring Boot 真实启动；
- 第一阶段完整回归；
- 第二阶段当前全部真实接口回归；
- 平台订阅、授权、配额和批次快照真实接口验收。

GitHub Actions 不可用时，不得根据源码审查宣称运行验证通过。

## 17. 实施范围与阶段

本轮实际实现：

1. 单一系统管理员和平台登录；
2. 首次强制修改密码和本地重置脚本；
3. 固化权限与配额目录；
4. 不可变套餐修订；
5. 试用、固定期限和长期订阅；
6. 直接升级和直接降级；
7. 功能增补、移除和配额覆盖；
8. 第一阶段模块级权限接入；
9. 第二阶段现有功能操作级权限接入；
10. 第三阶段权限目录登记；
11. 80% 配额告警和 100% 新增拒绝；
12. 批次权限快照；
13. 独立平台控制台；
14. 平台审计和业务端自然提示。

本轮不实现：

- 第三阶段业务功能；
- 在线支付；
- 多客户和多租户；
- 多系统管理员；
- 自动数据物理清理；
- 自动备份和恢复。

## 18. 架构决策摘要

| 决策 | 结果 |
|---|---|
| 客户数量 | 单一客户、单一学校部署 |
| 系统管理员 | 唯一一个 `SYSTEM_ADMIN` |
| 初始凭据 | `system_admin` / `Dormitory@2026`，首次强制改密 |
| 支付 | 线下合同，平台手工维护 |
| 套餐 | 不可变修订 |
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
