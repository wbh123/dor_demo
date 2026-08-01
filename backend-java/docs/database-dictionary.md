# 数据库字典

> 数据库：MySQL 8.4  
> 结构来源：`server/src/main/resources/db/migration/`  
> 当前版本：V2  
> 状态：第一阶段开发中

## 1. 表清单

| 数据域 | 表 | 说明 |
|---|---|---|
| 用户组织 | `organization` | 学校、学院、专业和班级层级 |
| 用户组织 | `app_user` | 学生和管理员账户 |
| 用户组织 | `student` | 学生业务档案 |
| 用户组织 | `import_job` | 数据导入任务 |
| 用户组织 | `import_error` | 数据导入错误 |
| 宿舍资源 | `campus` | 校区 |
| 宿舍资源 | `dormitory_building` | 宿舍楼 |
| 宿舍资源 | `dormitory_floor` | 楼层 |
| 宿舍资源 | `room` | 房型、容量、固定性别和运行状态 |
| 宿舍资源 | `bed_frame` | 双层床共享床架 |
| 宿舍资源 | `bed` | 独立床位 |
| 问卷匹配 | `questionnaire_version` | 问卷版本 |
| 问卷匹配 | `questionnaire_question` | 问卷题目 |
| 问卷匹配 | `questionnaire_option` | 问卷选项 |
| 问卷匹配 | `questionnaire_answer` | 学生原始答案 |
| 问卷匹配 | `student_feature` | 标准化特征 |
| 问卷匹配 | `matching_weight_scheme` | 匹配权重方案 |
| 批次资格 | `selection_batch` | 选寝批次 |
| 批次资格 | `batch_student_eligibility` | 学生资格 |
| 批次资格 | `batch_building_scope` | 可选楼栋范围 |
| 批次资格 | `batch_room_scope` | 可选房间范围 |
| 批次资格 | `batch_bed_scope` | 可选床位范围 |
| 组队 | `selection_team` | 选寝队伍 |
| 组队 | `selection_team_member` | 队伍成员 |
| 组队 | `team_invitation` | 队伍邀请 |
| 分配 | `bed_assignment` | 当前有效床位分配 |
| 分配 | `assignment_history` | 分配历史 |
| 分配 | `allocation_run` | 随机分配执行 |
| 分配 | `allocation_run_result` | 随机分配结果 |
| 审计 | `audit_log` | 关键操作审计 |

## 2. 宿舍资源核心字段

### 2.1 `dormitory_building`

| 字段 | 说明 |
|---|---|
| `campus_id` | 所属校区 |
| `building_code` | 楼栋编码 |
| `building_name` | 楼栋名称 |
| `gender_restriction` | `M`、`F` 或 `ANY` |
| `enabled` | 是否启用 |

楼栋允许使用 `ANY`，表示未来可以在同一楼栋中按房间分别配置男寝和女寝。它不表示房间可以男女混住。

### 2.2 `room`

| 字段 | 说明 |
|---|---|
| `floor_id` | 所属楼层 |
| `room_number` | 房间号 |
| `room_type` | 房型 |
| `capacity` | 规划容量 |
| `gender_restriction` | 固定房间性别，只允许 `M` 或 `F` |
| `operational_status` | 启用、禁用或维护 |
| `state_version` | 房间实时状态版本 |
| `version` | 乐观锁版本 |

当前支持的房型值：

```text
FOUR_PERSON
FIVE_PERSON
SIX_PERSON
OTHER
```

房型不与性别绑定。后续可以存在：

- 男生四人间；
- 男生五人间；
- 女生四人间；
- 女生五人间。

但每个具体房间必须固定为男寝或女寝。

### 2.3 `bed_frame`

用于表达共享床架，例如上下铺。当前五人间中，上铺和下铺通过同一个 `bed_frame_id` 关联。

### 2.4 `bed`

| 字段 | 说明 |
|---|---|
| `room_id` | 所属房间 |
| `bed_frame_id` | 共享床架，可为空 |
| `bed_code` | 房内床位编码 |
| `bed_type` | 床位类型 |
| `position_index` | 房内排序位置 |
| `operational_status` | 启用、禁用或维护 |

床位类型：

```text
LOFT_BED_DESK
BUNK_UPPER
BUNK_LOWER
OTHER
```

同一房间中，床位编码和位置均唯一。

## 3. 当前测试数据房型

当前第一阶段合成数据按学校现阶段要求生成：

| 性别 | 房型 | 房间数 | 每间床位 | 床位数 |
|---|---|---:|---:|---:|
| 男生 | 五人间 | 64 | 5 | 320 |
| 女生 | 四人间 | 80 | 4 | 320 |
| 合计 | — | 144 | — | 640 |

男生五人间布局：

```text
A、B、C：LOFT_BED_DESK
D-U：BUNK_UPPER
D-L：BUNK_LOWER
```

女生四人间布局：

```text
A、B、C、D：LOFT_BED_DESK
```

该组合只是测试数据，不是数据库永久限制。

## 4. 批次可选范围

管理员通过以下表控制宿舍开放范围：

| 表 | 说明 |
|---|---|
| `batch_building_scope` | 按楼栋开放 |
| `batch_room_scope` | 按具体房间开放 |
| `batch_bed_scope` | 按具体床位开放或排除 |

房型混合时，应优先使用 `batch_room_scope` 精确选择当前批次允许的四人间和五人间。

候选查询必须校验：

- 学生具有批次资格；
- 学生性别与房间性别一致；
- 房间和床位属于批次范围；
- 房间和床位处于启用状态；
- 床位没有最终分配和有效临时占用。

## 5. 核心唯一约束

| 约束 | 说明 |
|---|---|
| `uk_student_number` | 学号全局唯一 |
| `uk_room_floor_number` | 同一楼层房间号唯一 |
| `uk_bed_room_code` | 同一房间床位编码唯一 |
| `uk_bed_room_position` | 同一房间床位位置唯一 |
| `uk_batch_student_eligibility` | 同一批次每名学生只有一条资格 |
| `uk_batch_room_scope` | 同一房间在同一批次只配置一次 |
| `uk_active_team_member` | 同一学生在同一批次最多属于一个有效队伍 |
| `uk_assignment_batch_student` | 同一学生在同一批次最多一个当前床位 |
| `uk_assignment_batch_bed` | 同一床位在同一批次最多分配给一个学生 |
| `uk_allocation_idempotency` | 同一批次随机分配请求幂等 |

## 6. 关键状态

### 6.1 批次状态

```text
DRAFT
PUBLISHED
OPEN
PAUSED
CLOSED
ALLOCATING
FINISHED
CANCELLED
```

### 6.2 队伍状态

```text
FORMING
LOCKED
SELECTING
COMPLETED
DISSOLVED
```

### 6.3 分配方式

```text
SELF_SELECT
TEAM_SELECT
STUDENT_RANDOM
ADMIN_RANDOM
MANUAL_ADJUSTMENT
```

## 7. Flyway迁移

当前正式迁移：

```text
V1__create_phase1_schema.sql
V2__enforce_fixed_room_gender.sql
```

V2将房间性别约束从 `M/F/ANY` 收紧为：

```text
M/F
```

开发期间：

1. 新增Flyway版本迁移；
2. 执行空库迁移测试；
3. 执行已有数据库升级测试；
4. 更新本文档；
5. 不修改已经执行的版本迁移。

第一阶段全部功能开发并验收完成后，运行：

```bash
python scripts/db/build_frozen_baseline.py
```

将正式迁移整理为独立结构SQL。开发测试数据不进入固化脚本。
