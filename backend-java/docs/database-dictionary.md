# 数据库字典

> 数据库：MySQL 8.4  
> 结构来源：`server/src/main/resources/db/migration/`  
> 当前版本：V11  
> 状态：第二阶段批次规则模板与已合并功能加固

## 1. 表清单

| 数据域 | 表 | 说明 |
|---|---|---|
| 用户学生 | `major` | 专业编号和专业名称 |
| 用户学生 | `app_user` | 学生和管理员账户；学生账号通过`student_id`关联学生 |
| 用户学生 | `student` | 学生身份、国籍代码和本人手机号 |
| 用户学生 | `import_job` | 数据导入任务 |
| 用户学生 | `import_error` | 数据导入错误 |
| 用户学生 | `student_notification` | 学生站内系统通知 |
| 系统配置 | `system_setting` | 新生欢迎语等可审计系统配置 |
| 宿舍资源 | `campus` | 校区 |
| 宿舍资源 | `dormitory_building` | 宿舍楼 |
| 宿舍资源 | `dormitory_floor` | 楼层 |
| 宿舍资源 | `room` | 房型、物理容量、固定性别和运行状态 |
| 宿舍资源 | `bed_frame` | 共享床架 |
| 宿舍资源 | `bed` | 独立可分配床位 |
| 宿舍资源 | `room_bed_layout` | 逐床位房间平面坐标与朝向 |
| 问卷匹配 | `questionnaire_version` | 个人偏好版本 |
| 问卷匹配 | `questionnaire_question` | 个人偏好题目 |
| 问卷匹配 | `questionnaire_option` | 题目选项 |
| 问卷匹配 | `questionnaire_answer` | 学生原始答案 |
| 问卷匹配 | `student_feature` | 标准化特征 |
| 问卷匹配 | `matching_weight_scheme` | 匹配权重方案不可变修订 |
| 批次资格 | `batch_rule_template` | 批次运行规则不可变修订 |
| 批次资格 | `selection_batch` | 选寝批次和执行规则快照 |
| 批次资格 | `batch_student_eligibility` | 学生在指定批次中的资格 |
| 批次资格 | `active_batch_student_lock` | 同一学生活动批次唯一锁 |
| 批次资格 | `batch_building_scope` | 可选楼栋范围 |
| 批次资格 | `batch_room_scope` | 可选房间范围 |
| 批次资格 | `batch_bed_scope` | 可选床位范围 |
| 组队 | `selection_team` | 选寝小组内部事实 |
| 组队 | `selection_team_member` | 小组成员 |
| 组队 | `team_invitation` | 小组邀请 |
| 分配 | `bed_assignment` | 当前有效床位分配 |
| 分配 | `assignment_history` | 分配历史 |
| 分配 | `allocation_run` | 随机分配执行 |
| 分配 | `allocation_run_result` | 随机分配结果 |
| 审计 | `audit_log` | 关键操作审计 |

V3删除了通用`organization`组织树。学生只关联专业，不保存学院、班级、年级和专业名称快照。

## 2. 学生、账户与系统配置

### 2.1 `major`

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | `BIGINT` | 专业主键 |
| `major_code` | `VARCHAR(32)` | 学校专业编号，全局唯一 |
| `major_name` | `VARCHAR(128)` | 专业名称，全局唯一 |
| `enabled` | `TINYINT` | 是否启用 |
| `created_at` | `DATETIME(3)` | 创建时间 |
| `updated_at` | `DATETIME(3)` | 更新时间 |

### 2.2 `student`

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | `BIGINT` | 技术主键 |
| `student_number` | `CHAR(12)` | 12位数字学号，全局唯一 |
| `student_name` | `VARCHAR(128)` | 姓名 |
| `gender` | `CHAR(1)` | `M`或`F` |
| `major_id` | `BIGINT` | 专业外键 |
| `nationality_code` | `CHAR(2)` | ISO 3166-1两位国籍代码，默认`CN` |
| `phone_number` | `VARCHAR(32)` | 学生本人可维护的手机号，可为空 |
| `created_at` | `DATETIME(3)` | 创建时间 |
| `updated_at` | `DATETIME(3)` | 更新时间 |

学生表不保存账号、班级、年级、专业名称快照、全局住宿资格和数据来源。国籍与手机号为学生体验所需的最小扩展资料。

### 2.3 `app_user`

`app_user.student_id`用于学生账号关联：

- 学生账号关联一个学生；
- 管理员账号为空；
- 唯一索引避免一个学生绑定多个账号；
- `welcome_acknowledged_at`记录学生是否确认过首次欢迎信息。

### 2.4 `system_setting`

| 字段 | 说明 |
|---|---|
| `setting_key` | 配置唯一编码 |
| `setting_value` | 配置值；新生欢迎语保存为`zh-CN`和`en-US`组成的JSON对象 |
| `updated_by` | 最后修改管理员 |
| `version` | 乐观锁版本 |

V11会将V9之前可能遗留的JSON标量、`null`、数组或缺少语言键的对象规范化为完整中英文对象。

### 2.5 `student_notification`

当前通知类型：

```text
TEAM_MEMBER_REMOVED
TEAM_DISSOLVED
TEAM_INVITATION_CANCELLED
```

通知正文保存国际化键和JSON插值参数，不直接固化单一语言文本。

## 3. 宿舍资源

### 3.1 `dormitory_building`

| 字段 | 说明 |
|---|---|
| `campus_id` | 所属校区 |
| `building_code` | 楼栋编码 |
| `building_name` | 楼栋名称 |
| `gender_restriction` | `M`、`F`或`ANY` |
| `enabled` | 是否启用 |

楼栋的`ANY`只表示可以按房间分别配置男寝和女寝，不表示同一房间允许混住。

### 3.2 `room`

| 字段 | 说明 |
|---|---|
| `floor_id` | 所属楼层 |
| `room_number` | 房间号 |
| `room_type` | 房型，由物理床位数量同步 |
| `capacity` | 物理床位总数，不受维护或停用状态影响 |
| `gender_restriction` | 固定房间性别，只允许`M`或`F` |
| `operational_status` | 启用、禁用或维护 |
| `state_version` | 内部房间状态版本，不展示给学生 |
| `version` | 房间属性与布局的乐观锁版本 |

房型值：

```text
FOUR_PERSON
FIVE_PERSON
SIX_PERSON
OTHER
```

### 3.3 `bed_frame`与`bed`

`bed_frame`表达共享床架；`bed`保存每个可分配床位。

床位类型：

```text
LOFT_BED_DESK
BUNK_UPPER
BUNK_LOWER
OTHER
```

只有同一床架下恰好包含一张`BUNK_UPPER`和一张`BUNK_LOWER`时，布局编辑器才将其合并为上下铺床具单元。其他历史床架保持逐床位编辑。

### 3.4 `room_bed_layout`

V5新增逐床位可视化布局事实。没有记录时使用后端和前端一致的默认布局。

| 字段 | 类型 | 说明 |
|---|---|---|
| `bed_id` | `BIGINT` | 床位主键，同时作为布局主键 |
| `layout_x` | `DECIMAL(6,3)` | 房间局部横向坐标，范围`[-5.2, 5.2]` |
| `layout_z` | `DECIMAL(6,3)` | 房间局部纵向坐标，范围`[-3.5, 3.5]` |
| `rotation_degrees` | `SMALLINT` | 平面朝向，只允许`0/90/180/270` |
| `updated_by` | `BIGINT` | 最后修改管理员账号 |
| `version` | `INT` | 单条布局记录版本 |
| `created_at` | `DATETIME(3)` | 创建时间 |
| `updated_at` | `DATETIME(3)` | 更新时间 |

业务约束：

- 一个床位最多一条布局记录；
- 一次保存必须提交房间内全部床具单元；
- 真正的上下铺上下层共享平面坐标和朝向；
- 上下层高度由`bed_type`决定；
- 管理端以`room.version`执行乐观锁；
- 保存后递增`room.version`和`room.state_version`；
- 修改原因和前后布局写入`audit_log`。

## 4. 个人偏好与匹配

V4将`SMOKING_ACCEPTANCE`改为三态单选：

| 编码 | 页面文本 | 匹配含义 |
|---|---|---|
| `ACCEPT` | 接受 | 可以接受室友吸烟 |
| `REJECT` | 不接受 | 不能接受室友吸烟 |
| `ANY` | 均可 | 没有明确限制 |

只有`ACCEPT`与`REJECT`直接组合时产生明显冲突。V6为`matching_weight_scheme`增加：

- `revision`不可变修订号；
- `created_by`和`change_reason`；
- `published_at`；
- `(scheme_code, revision)`唯一约束。

批次始终引用精确匹配方案修订，后续新修订不改变历史批次评分。

## 5. 批次规则模板

### 5.1 `batch_rule_template`

V10新增批次规则模板不可变修订。

| 字段 | 说明 |
|---|---|
| `rule_code` | 规则模板编码 |
| `rule_name` | 模板名称 |
| `revision` | 同一编码下递增的不可变修订号 |
| `hold_duration_seconds` | 临时占用时长 |
| `hold_renewal_limit` | 最大续期次数 |
| `allow_team` | 是否允许组队 |
| `team_min_size` | 队伍最小人数 |
| `team_max_size` | 队伍最大人数；新启用模板最多5人 |
| `allow_student_random` | 是否允许学生随机推荐 |
| `unselected_strategy` | 未选学生处理策略 |
| `rule_version` | 规则执行版本 |
| `enabled` | 是否可供新批次选择 |
| `is_default` | 是否为默认修订，全库最多一个 |
| `created_by` | 创建管理员 |
| `change_reason` | 新建或修订原因 |
| `version` | 乐观锁版本 |

规则：

- 修订创建后不覆盖原记录；
- 新启用模板组队上限为5人；
- V10迁移生成的停用历史模板允许保留旧上限，但不能用于新批次；
- 默认模板必须启用；
- 默认切换与新修订创建在同一事务中完成并写审计。

### 5.2 `selection_batch`

`selection_batch.rule_template_id`引用精确模板修订。同时继续保存以下执行快照：

```text
hold_duration_seconds
hold_renewal_limit
allow_team
team_min_size
team_max_size
allow_student_random
unselected_strategy
rule_version
```

运行中的选寝只读取批次快照，不动态读取模板。创建模板新修订不会改变历史批次；复制批次同时复制`rule_template_id`和全部规则快照。

## 6. 批次可选范围与活动唯一性

管理员通过以下表控制开放范围：

| 表 | 说明 |
|---|---|
| `batch_building_scope` | 按楼栋开放 |
| `batch_room_scope` | 按房间开放 |
| `batch_bed_scope` | 按床位开放或排除 |

活动状态为`PUBLISHED`、`OPEN`和`PAUSED`。`active_batch_student_lock`以`student_id`为主键，保证同一学生同一时刻只参加一个活动批次。

队伍存在Redis临时床位占用时，成员移除、成员退出和切换个人选寝会返回`TEAM_HOLD_ACTIVE`，必须先释放队伍床位占用。

## 7. 当前开发数据

| 数据 | 数量 |
|---|---:|
| 专业 | 5 |
| 学生 | 520 |
| 男生 | 260 |
| 女生 | 260 |
| 房间 | 144 |
| 男生床位 | 320 |
| 女生床位 | 320 |
| 床位 | 640 |

测试学号范围为`202600000001`至`202600000520`。开发数据由测试迁移和重建脚本生成，不进入正式Flyway版本迁移。

## 8. 核心唯一约束

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
| `PRIMARY KEY room_bed_layout(bed_id)` | 一个床位最多一条自定义布局 |
| `uk_weight_scheme_revision` | 匹配方案编码和修订号唯一 |
| `uk_batch_rule_template_revision` | 批次规则模板编码和修订号唯一 |
| `uk_batch_rule_template_default` | 全库最多一个默认模板修订 |
| `uk_batch_student_eligibility` | 同一批次每名学生只有一条资格 |
| `uk_active_team_member` | 同一学生在同一批次最多属于一个有效小组 |
| `uk_assignment_batch_student` | 同一学生在同一批次最多一个当前床位 |
| `uk_assignment_batch_bed` | 同一床位在同一批次最多分配给一个学生 |
| `uk_allocation_idempotency` | 同一批次随机分配请求幂等 |

## 9. Flyway迁移

当前正式迁移：

```text
V1__create_phase1_schema.sql
V2__enforce_fixed_room_gender.sql
V3__normalize_major_and_minimize_student.sql
V4__refine_questionnaire_and_active_batch_rules.sql
V5__add_room_bed_layout.sql
V6__version_matching_weight_schemes.sql
V7__add_student_welcome_settings.sql
V8__expand_personal_preferences.sql
V9__add_student_contact_and_notifications.sql
V10__add_batch_rule_templates.sql
V11__harden_welcome_message_json.sql
```

开发期间只能新增迁移，不能修改已执行版本。固化结构生成命令：

```bash
python scripts/db/build_frozen_baseline.py
```

生成后必须执行固化结构漂移测试。开发测试数据不进入固化结构。
