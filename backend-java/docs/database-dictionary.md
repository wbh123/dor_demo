# 数据库字典

> 数据库：MySQL 8.4  
> 结构来源：`server/src/main/resources/db/migration/`  
> 当前版本：V4  
> 状态：第一阶段冻结后的规则优化

## 1. 表清单

| 数据域 | 表 | 说明 |
|---|---|---|
| 用户学生 | `major` | 专业编号和专业名称 |
| 用户学生 | `app_user` | 学生和管理员账户；学生账号通过 `student_id` 关联学生 |
| 用户学生 | `student` | 最小化学生档案 |
| 用户学生 | `import_job` | 数据导入任务 |
| 用户学生 | `import_error` | 数据导入错误 |
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
| 批次资格 | `batch_student_eligibility` | 学生在指定批次中的资格 |
| 批次资格 | `active_batch_student_lock` | 同一学生活动批次唯一锁 |
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

V3删除了通用 `organization` 组织树。当前需求不保存学院和班级信息，学生只关联专业，因此继续维护通用组织层级会增加无实际用途的表、外键和重复字段。

## 2. 学生与专业

### 2.1 `major`

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | `BIGINT` | 专业主键 |
| `major_code` | `VARCHAR(32)` | 学校专业编号，全局唯一 |
| `major_name` | `VARCHAR(128)` | 专业名称，全局唯一 |
| `enabled` | `TINYINT` | 是否启用 |
| `created_at` | `DATETIME(3)` | 创建时间 |
| `updated_at` | `DATETIME(3)` | 更新时间 |

学生表不重复保存专业名称。查询学生信息时通过 `student.major_id` 关联专业表获得专业编号和名称。

### 2.2 `student`

V3后的业务字段只有：

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | `BIGINT` | 技术主键 |
| `student_number` | `CHAR(12)` | 12位数字学号，全局唯一 |
| `student_name` | `VARCHAR(128)` | 姓名 |
| `gender` | `CHAR(1)` | `M`或`F` |
| `major_id` | `BIGINT` | 专业外键 |
| `created_at` | `DATETIME(3)` | 创建时间 |
| `updated_at` | `DATETIME(3)` | 更新时间 |

学生表不保存用户外键、组织、班级、校区、年级、专业名称快照、全局住宿资格、资料状态和数据来源。账号关系属于用户域，住宿资格属于具体选寝批次。

### 2.3 `app_user`

`app_user.student_id`：

- 学生账号关联一个学生；
- 管理员账号为空；
- 建立唯一索引，避免一个学生绑定多个账号；
- 学生档案本身不保存账号信息。

## 3. 宿舍资源核心字段

### 3.1 `dormitory_building`

| 字段 | 说明 |
|---|---|
| `campus_id` | 所属校区 |
| `building_code` | 楼栋编码 |
| `building_name` | 楼栋名称 |
| `gender_restriction` | `M`、`F`或`ANY` |
| `enabled` | 是否启用 |

楼栋允许使用 `ANY`，表示同一楼栋可以按房间分别配置男寝和女寝。房间本身仍不得男女混住。

### 3.2 `room`

| 字段 | 说明 |
|---|---|
| `floor_id` | 所属楼层 |
| `room_number` | 房间号 |
| `room_type` | 房型 |
| `capacity` | 规划容量 |
| `gender_restriction` | 固定房间性别，只允许`M`或`F` |
| `operational_status` | 启用、禁用或维护 |
| `state_version` | 后端内部房间状态版本，不展示给学生 |
| `version` | 乐观锁版本 |

当前房型值：

```text
FOUR_PERSON
FIVE_PERSON
SIX_PERSON
OTHER
```

房型不与性别绑定。可以存在男生四人间、男生五人间、女生四人间和女生五人间，但每个具体房间必须固定为男寝或女寝。

### 3.3 `bed_frame`与`bed`

`bed_frame`表达共享床架，例如上下铺。`bed`保存每个可分配床位。

床位类型：

```text
LOFT_BED_DESK
BUNK_UPPER
BUNK_LOWER
OTHER
```

同一房间中，床位编码和位置均唯一。当前五人间中，位置4和位置5为靠窗右侧同一床架上的上铺和下铺。

## 4. 问卷三态吸烟偏好

V4将 `SMOKING_ACCEPTANCE` 从布尔题改为单选题：

| 选项编码 | 页面文本 | 匹配含义 |
|---|---|---|
| `ACCEPT` | 接受 | 可以接受室友吸烟 |
| `REJECT` | 不接受 | 不能接受室友吸烟 |
| `ANY` | 均可 | 对此没有明确限制 |

历史布尔答案迁移规则：

```text
true  → ACCEPT
false → REJECT
其他  → ANY
```

只有 `ACCEPT` 与 `REJECT` 直接匹配时产生明显冲突，`ANY` 不产生吸烟偏好冲突。

## 5. 当前测试数据

| 数据 | 数量 |
|---|---:|
| 专业 | 5 |
| 学生 | 520 |
| 男生 | 260 |
| 女生 | 260 |
| 男生五人间 | 64 |
| 女生四人间 | 80 |
| 房间合计 | 144 |
| 男生床位 | 320 |
| 女生床位 | 320 |
| 床位合计 | 640 |

测试专业编号为 `M001` 至 `M005`。全部学号为12位数字，范围为 `202600000001` 至 `202600000520`。

开发测试数据先执行 `R__development_test_data.sql`，再执行 `R__zz_refine_development_questionnaire.sql`，确保最终问卷和学生特征均为三态吸烟偏好。

## 6. 批次可选范围与活动唯一性

管理员通过以下表控制宿舍开放范围：

| 表 | 说明 |
|---|---|
| `batch_building_scope` | 按楼栋开放 |
| `batch_room_scope` | 按具体房间开放 |
| `batch_bed_scope` | 按具体床位开放或排除 |

候选查询必须同时校验批次资格、学生性别、房间性别、范围配置、房间状态、床位状态、最终分配和临时占用。

活动批次定义为：

```text
PUBLISHED
OPEN
PAUSED
```

`active_batch_student_lock` 以 `student_id` 为主键。批次进入活动状态时，`BatchLifecycleService`在同一数据库事务中写入全部有资格学生；批次关闭、完成或取消时，在同一事务中释放对应锁。若学生已属于另一个活动批次，数据库主键冲突会阻止状态切换并回滚整个事务。

该方案不依赖数据库触发器或超级管理员权限，普通业务数据库账号即可完成迁移和运行。

## 7. 核心唯一约束

| 约束 | 说明 |
|---|---|
| `uk_major_code` | 专业编号唯一 |
| `uk_major_name` | 专业名称唯一 |
| `uk_student_number` | 学号全局唯一 |
| `uk_app_user_student` | 一个学生最多关联一个账号 |
| `PRIMARY KEY active_batch_student_lock(student_id)` | 同一学生同一时刻只能属于一个活动批次 |
| `uk_room_floor_number` | 同一楼层房间号唯一 |
| `uk_bed_room_code` | 同一房间床位编码唯一 |
| `uk_bed_room_position` | 同一房间床位位置唯一 |
| `uk_batch_student_eligibility` | 同一批次每名学生只有一条资格 |
| `uk_active_team_member` | 同一学生在同一批次最多属于一个有效队伍 |
| `uk_assignment_batch_student` | 同一学生在同一批次最多一个当前床位 |
| `uk_assignment_batch_bed` | 同一床位在同一批次最多分配给一个学生 |
| `uk_allocation_idempotency` | 同一批次随机分配请求幂等 |

## 8. Flyway迁移

当前正式迁移：

```text
V1__create_phase1_schema.sql
V2__enforce_fixed_room_gender.sql
V3__normalize_major_and_minimize_student.sql
V4__refine_questionnaire_and_active_batch_rules.sql
```

V4执行以下变化：

1. 吸烟接受问题改为三态单选；
2. 历史问卷答案和特征向量转换为三态；
3. 新建活动批次学生唯一锁表；
4. 由Spring事务服务配合数据库唯一约束维护活动批次锁，不创建高权限触发器。

开发期间只能新增迁移，不能修改已经执行的版本迁移。固化结构脚本通过以下命令生成：

```bash
python scripts/db/build_frozen_baseline.py
```

开发测试数据不进入固化结构脚本。
