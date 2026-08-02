# V16 选寝模式、在住事实与转学生数据字典扩展

> 正式迁移：`V15__add_batch_selection_modes.sql`、`V16__add_residency_student_category_and_transfer_support.sql`  
> 最新结构入口：`backend-java/docs/sql/schema.sql`

## 1. `student` 新增字段

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| `student_category` | `VARCHAR(24)` | `DOMESTIC` / `INTERNATIONAL` | 明确业务学生类别，不只依赖国籍代码推断 |
| `enrollment_source` | `VARCHAR(32)` | `INITIAL_IMPORT` / `TRANSFER_MANUAL` / `ADMIN_MANUAL` / `BATCH_IMPORT` | 学生资料进入系统的来源 |

历史迁移默认：

```text
nationality_code = CN -> DOMESTIC
其他国籍代码 -> INTERNATIONAL
```

管理员可以人工修正特殊学籍。

## 2. `room` 新增字段

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| `resident_scope` | `VARCHAR(32)` | `DOMESTIC_ONLY` / `INTERNATIONAL_ONLY` / `MIXED` | 国内生专用、国际生专用或混住宿舍 |

房间属性始终有效，批次隔离开关不能覆盖专用宿舍限制。

## 3. `selection_batch` 新增字段

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| `selection_mode` | `VARCHAR(16)` | `ROOM` / `BED` | 只选寝室或选择具体床位 |
| `separate_student_categories` | `TINYINT` | `0` / `1` | 是否强制国内生和国际生分开选寝 |

规则：

- `BED` 模式需要 `P2_BED_SELECTION_MODE` 权限；
- 批次进入活动状态后不允许修改模式；
- `ROOM` 模式不产生具体床位；
- `BED` 模式发布前要求所有在住学生的实际床位可识别。

## 4. 活动范围互斥

### `active_batch_room_lock`

| 字段 | 说明 |
|---|---|
| `room_id` | 主键，同一寝室同时只能属于一个活动批次 |
| `batch_id` | 活动批次 |
| `selection_mode` | 锁定时的 `ROOM` 或 `BED` 模式 |
| `locked_at` | 锁定时间 |

批次状态进入 `PUBLISHED`、`OPEN` 或 `PAUSED` 时获取锁；进入 `CLOSED`、`FINISHED` 或 `CANCELLED` 时释放锁。

### `active_batch_student_lock`

| 字段 | 说明 |
|---|---|
| `student_id` | 主键，同一学生同时只能参加一个活动批次 |
| `batch_id` | 当前活动批次 |
| `locked_at` | 锁定时间 |

## 5. `batch_student_eligibility` 新增字段

| 字段 | 类型 | 说明 |
|---|---|---|
| `source_type` | `VARCHAR(32)` | `INITIAL`、`TRANSFER_MANUAL`、`ADMIN_MANUAL`、`IMPORT` |
| `added_by` | `BIGINT` | 人工加入时的管理员 |
| `added_at` | `DATETIME(3)` | 人工加入时间 |

转学生加入已存在批次前，必须通过容量、性别、学生类别、房间范围和活动冲突预检。

## 6. `room_assignment` 升级为跨批次在住事实

`room_assignment` 不再只是某个批次的选寝结果，而是当前现实入住状态的唯一事实来源。

| 字段 | 类型 | 说明 |
|---|---|---|
| `batch_id` | `BIGINT NULL` | 来源批次；管理员直接入住时为空 |
| `student_id` | `BIGINT` | 学生 |
| `room_id` | `BIGINT` | 当前寝室 |
| `bed_id` | `BIGINT NULL` | 实际床位；ROOM模式可为空 |
| `team_id` | `BIGINT NULL` | 来源队伍 |
| `source_selection_mode` | `VARCHAR(16)` | `ROOM`、`BED` 或 `DIRECT` |
| `assignment_method` | `VARCHAR(32)` | 个人选寝、队伍选寝、个人选床、队伍选床、直接安排等 |
| `assignment_status` | `VARCHAR(16)` | `ACTIVE` 或 `ENDED` |
| `bed_confirmed_at` | `DATETIME(3) NULL` | 实际床位确认时间 |
| `ended_at` | `DATETIME(3) NULL` | 退宿或换寝结束时间 |
| `end_reason` | `VARCHAR(500) NULL` | 结束原因 |

关键约束：

```text
同一学生最多一条 ACTIVE 在住记录
同一床位最多一条 ACTIVE 且已确认的在住记录
```

房间剩余容量统一计算：

```text
房间容量 - ACTIVE 在住人数
```

具体可选床位统一计算：

```text
批次开放范围内的启用床位
- ACTIVE 且 bed_id 已确认的在住床位
```

如果寝室存在 `ACTIVE AND bed_id IS NULL` 的学生，则该寝室不能加入 BED 模式活动批次。

## 7. `room_assignment_history`

记录寝室和床位事实变化：

```text
ROOM_ASSIGNED
BED_ASSIGNED
BED_CONFIRMED
BED_CHANGED
RESIDENCY_ENDED
```

保存操作人、原因、变更前后数据和发生时间，禁止物理覆盖历史事实。

## 8. 最新数据脚本

```text
backend-java/docs/sql/schema.sql
backend-java/docs/sql/test-data/1000_students_clean.sql
backend-java/docs/sql/test-data/1000_students_realistic_mixed_state.sql
```

独立单文件生成：

```bash
python scripts/db/generate_1000_student_sql.py --scenario all
```
