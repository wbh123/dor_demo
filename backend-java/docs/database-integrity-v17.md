# V17 数据库完整性说明

> 数据库：`wust_dormitory`  
> 正式迁移最高版本：`V17`  
> 唯一结构事实来源：`backend-java/server/src/main/resources/db/migration/`

## 1. V17 修复背景

V7 已创建 `system_setting` 表并初始化 `STUDENT_WELCOME_MESSAGE`，V11 将其规范为包含 `zh-CN` 和 `en-US` 的 JSON 对象。

旧版千人测试数据清理过程会遍历并删除未列入保留清单的表。由于 `system_setting` 未被保留，导入千人测试数据后欢迎语单例被删除，管理员读取配置时返回：

```text
SYSTEM_SETTING_NOT_FOUND
学生欢迎语配置不存在
```

同时，旧清理过程关闭外键检查并删除普通管理员，但保留了部分系统配置表，可能造成以下可空创建人字段留下孤儿编号：

- `system_setting.updated_by`；
- `matching_weight_scheme.created_by`；
- `batch_rule_template.created_by`。

`service_quota_alert` 属于由当前业务规模派生的运行状态，测试数据重置后也不应保留旧告警。

## 2. V17 正式迁移

迁移文件：

```text
V17__restore_required_system_configuration.sql
```

迁移执行：

1. 缺少欢迎语时，幂等创建中英文默认欢迎语；
2. 清理系统设置、匹配方案和批次规则模板中的无效管理员引用；
3. 缺少 `SYSTEM_DEFAULT` 批次规则修订时幂等恢复；
4. 不修改已有合法配置和不可变修订。

## 3. 必需单例与目录

数据库完整性要求以下数据必须存在：

| 对象 | 要求 |
|---|---|
| `SYSTEM_ADMIN` | 恰好一个 |
| `STUDENT_WELCOME_MESSAGE` | 恰好一条，包含非空 `zh-CN` 与 `en-US` |
| `FULL_CURRENT` | 默认套餐存在 |
| `PRIMARY_SERVICE` | 单客户服务订阅存在 |
| 当前订阅修订 | `is_current=1` 恰好一条 |
| `SYSTEM_DEFAULT` | 默认批次规则修订存在且可用 |
| 核心功能目录 | 第一阶段基础权限及 `P2_BED_SELECTION_MODE` 存在 |
| 核心配额目录 | 学生、房间、床位、年度批次配额存在 |

## 4. 关键结构约束

必须保留：

- 唯一系统管理员索引 `uk_single_system_admin`；
- 系统设置键唯一索引 `uk_system_setting_key`；
- 活动学生批次互斥主键；
- 活动寝室批次互斥主键；
- 有效在住学生唯一索引 `uk_active_residency_student`；
- 有效现实床位唯一索引 `uk_active_residency_bed`；
- 当前订阅修订唯一索引 `uk_subscription_current`。

## 5. 测试数据重置规则

千人测试数据清理必须保留：

- `system_setting`；
- 功能与配额目录；
- 套餐、订阅及不可变修订；
- 问卷、匹配方案和批次规则模板；
- 唯一系统管理员。

删除普通管理员前，必须将保留配置表中指向普通管理员的可空引用设为 `NULL`。

数据重置后必须清空：

```text
service_quota_alert
```

因为告警应根据新数据规模重新计算。

## 6. Navicat 完整性检查

执行：

```text
backend-java/docs/sql/navicat/04_数据库完整性检查/
00_修复并检查数据库完整性.sql
```

检查范围包括：

- 关键表、字段、唯一索引和外键；
- Flyway V17 状态；
- 必需单例和初始化目录；
- 学生、专业、床位、布局、在住和配置孤儿引用；
- 活动锁与批次状态、模式一致性；
- 重复有效在住和重复现实床位占用。

全部通过后输出：

```text
DB_INTEGRITY_OK
```

## 7. 应用层防御

`SystemSettingService` 在管理员读取或修改欢迎语前，会通过唯一键冲突安全的原子写入确保单例存在。

数据库迁移仍是主要修复机制；应用层自动恢复用于兼容尚未执行 V17 的历史开发库，不替代正式迁移与完整性检查。
