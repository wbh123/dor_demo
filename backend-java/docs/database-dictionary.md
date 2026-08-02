# 数据库数据字典

> 数据库：MySQL 8.4及以上版本  
> 字符集：`utf8mb4`  
> 结构来源：`backend-java/server/src/main/resources/db/migration/`  
> 当前正式迁移版本：V9  
> 固化结构：`backend-java/docs/sql/schema.sql`

## 维护规则

本文件是项目长期维护的数据字典，而不是一次性设计说明。

1. 每次新增或修改Flyway迁移时，必须同步更新本数据字典；已经在环境中执行的旧迁移文件禁止直接修改，只能新增更高版本迁移。
2. 每次数据库结构变化后，必须执行 `python scripts/db/build_frozen_baseline.py`，同步更新固化结构 `backend-java/docs/sql/schema.sql`。
3. 新增表时必须补充表用途、主键、重要外键、状态字段、唯一约束和删除策略。
4. 新增字段时必须写明字段类型、是否允许为空、业务含义和枚举值。
5. 测试数据脚本 `backend-java/docs/sql/reset_and_seed_test_data.sql` 必须与最新迁移和本数据字典保持一致。

## 表清单

| 数据域 | 表名 | 主要用途 |
|---|---|---|
| 账户与学生 | `major` | 专业基础目录 |
| 账户与学生 | `app_user` | 管理员和学生登录账号 |
| 账户与学生 | `student` | 学生档案、国籍与手机号码 |
| 账户与学生 | `student_notification` | 面向学生的系统通知 |
| 账户与学生 | `import_job` | 批量导入任务 |
| 账户与学生 | `import_error` | 批量导入错误明细 |
| 宿舍资源 | `campus` | 校区目录 |
| 宿舍资源 | `dormitory_building` | 宿舍楼 |
| 宿舍资源 | `dormitory_floor` | 宿舍楼层 |
| 宿舍资源 | `room` | 宿舍房间、容量、性别与运行状态 |
| 宿舍资源 | `bed_frame` | 上下铺共享床架 |
| 宿舍资源 | `bed` | 可独立分配的床位 |
| 宿舍资源 | `room_bed_layout` | 床位平面坐标与旋转角度 |
| 个人偏好 | `questionnaire_version` | 个人偏好版本 |
| 个人偏好 | `questionnaire_question` | 个人偏好题目 |
| 个人偏好 | `questionnaire_option` | 单选题选项与匹配值 |
| 个人偏好 | `questionnaire_answer` | 学生原始答案 |
| 个人偏好 | `student_feature` | 标准化匹配特征与画像标签 |
| 匹配规则 | `matching_weight_scheme` | 可版本化的匹配权重方案 |
| 选寝批次 | `selection_batch` | 选寝活动批次 |
| 选寝批次 | `batch_student_eligibility` | 批次学生资格 |
| 选寝批次 | `active_batch_student_lock` | 学生活动批次唯一锁 |
| 选寝批次 | `batch_building_scope` | 批次开放楼栋范围 |
| 选寝批次 | `batch_room_scope` | 批次开放房间范围 |
| 选寝批次 | `batch_bed_scope` | 批次开放床位范围 |
| 组队 | `selection_team` | 选寝队伍 |
| 组队 | `selection_team_member` | 队伍成员及邀请状态 |
| 组队 | `team_invitation` | 组队邀请令牌与处理状态 |
| 分配 | `bed_assignment` | 当前有效住宿分配 |
| 分配 | `assignment_history` | 分配创建、调整和取消历史 |
| 分配 | `allocation_run` | 管理员统一分配执行记录 |
| 分配 | `allocation_run_result` | 每名学生的统一分配结果或失败原因 |
| 系统配置 | `system_setting` | 可配置欢迎语等系统设置 |
| 审计 | `audit_log` | 管理员、学生和系统关键操作审计 |

## 账户与学生

### `major`

| 字段 | 类型 | 含义 |
|---|---|---|
| `id` | `BIGINT` | 专业主键 |
| `major_code` | `VARCHAR(32)` | 专业编号，全局唯一 |
| `major_name` | `VARCHAR(128)` | 专业名称，全局唯一 |
| `enabled` | `TINYINT` | 是否允许新学生关联，`1`为启用 |
| `created_at` | `DATETIME(3)` | 创建时间 |
| `updated_at` | `DATETIME(3)` | 最后更新时间 |

### `app_user`

| 字段 | 类型 | 含义 |
|---|---|---|
| `id` | `BIGINT` | 用户主键 |
| `student_id` | `BIGINT NULL` | 学生账号对应的学生主键；管理员为空；一个学生最多一个账号 |
| `username` | `VARCHAR(64)` | 登录名；学生账号等于12位学号 |
| `password_hash` | `VARCHAR(255) NULL` | BCrypt等密码哈希；待激活或密码重置后为空 |
| `user_type` | `VARCHAR(32)` | `STUDENT`或`ADMIN` |
| `account_status` | `VARCHAR(32)` | `PENDING`、`ACTIVE`、`LOCKED`或`DISABLED` |
| `display_name` | `VARCHAR(128)` | 页面显示名称 |
| `last_login_at` | `DATETIME(3) NULL` | 最后登录时间 |
| `welcome_acknowledged_at` | `DATETIME(3) NULL` | 学生确认首次欢迎浮窗的时间 |
| `version` | `INT` | 乐观锁版本 |
| `created_at` | `DATETIME(3)` | 创建时间 |
| `updated_at` | `DATETIME(3)` | 更新时间 |

管理员重置学生密码时，`password_hash`、`last_login_at`和`welcome_acknowledged_at`清空，`account_status`恢复为`PENDING`，但选寝业务数据保留。完全重置会额外清除学生的批次资格、个人偏好、组队关系、通知和分配数据。

### `student`

| 字段 | 类型 | 含义 |
|---|---|---|
| `id` | `BIGINT` | 学生主键 |
| `student_number` | `CHAR(12)` | 12位数字学号，全局唯一 |
| `student_name` | `VARCHAR(128)` | 学生姓名 |
| `gender` | `CHAR(1)` | `M`为男生，`F`为女生 |
| `major_id` | `BIGINT` | 专业外键，关联`major.id` |
| `nationality_code` | `CHAR(2)` | ISO 3166-1 alpha-2国籍代码，例如`CN`、`US`、`JP` |
| `phone_number` | `VARCHAR(32) NULL` | 学生手机号码，学生本人可修改 |
| `created_at` | `DATETIME(3)` | 创建时间 |
| `updated_at` | `DATETIME(3)` | 更新时间 |

学生是否参加某次选寝由`batch_student_eligibility`决定，不由账号是否激活决定。统一分配必须覆盖批次内全部`ELIGIBLE`学生。

### `student_notification`

| 字段 | 类型 | 含义 |
|---|---|---|
| `id` | `BIGINT` | 通知主键 |
| `student_id` | `BIGINT` | 接收学生，学生删除时级联删除通知 |
| `notification_type` | `VARCHAR(64)` | `TEAM_MEMBER_REMOVED`、`TEAM_DISSOLVED`或`TEAM_INVITATION_CANCELLED` |
| `title_key` | `VARCHAR(128)` | 前端国际化标题键 |
| `message_key` | `VARCHAR(128)` | 前端国际化正文键 |
| `parameters_json` | `JSON` | 姓名、队伍等插值参数 |
| `read_at` | `DATETIME(3) NULL` | 阅读时间；为空表示未读 |
| `created_at` | `DATETIME(3)` | 创建时间 |

### `import_job`

| 字段 | 含义 |
|---|---|
| `id` | 导入任务主键 |
| `import_type` | 导入类型：`STUDENT`或`DORMITORY` |
| `file_name` | 原始文件名 |
| `file_sha256` | 文件摘要 |
| `operation_mode` | `VALIDATE`、`INSERT`或`UPSERT` |
| `job_status` | 创建、运行、成功、部分成功或失败状态 |
| `total_rows`、`success_rows`、`failed_rows` | 行数统计 |
| `operator_user_id` | 操作管理员 |
| `started_at`、`finished_at`、`created_at` | 执行时间 |

### `import_error`

| 字段 | 含义 |
|---|---|
| `id` | 错误主键 |
| `import_job_id` | 所属导入任务，任务删除时级联删除 |
| `row_number` | 原文件行号 |
| `field_name` | 出错字段 |
| `error_code` | 稳定错误编码 |
| `error_message` | 可读错误说明 |
| `raw_data` | 原始行JSON |
| `created_at` | 记录时间 |

## 宿舍资源

### `campus`

| 字段 | 含义 |
|---|---|
| `id` | 校区主键 |
| `campus_code` | 校区编码，全局唯一 |
| `campus_name` | 校区名称 |
| `address` | 地址 |
| `enabled` | 是否启用 |
| `version` | 乐观锁版本 |

### `dormitory_building`

| 字段 | 含义 |
|---|---|
| `id` | 楼栋主键 |
| `campus_id` | 所属校区 |
| `building_code` | 校区内楼栋编码 |
| `building_name` | 楼栋名称 |
| `gender_restriction` | `M`、`F`或`ANY`；`ANY`仅表示房间可分别配置性别，不表示房间混住 |
| `enabled` | 是否启用 |
| `version` | 乐观锁版本 |

### `dormitory_floor`

| 字段 | 含义 |
|---|---|
| `id` | 楼层主键 |
| `building_id` | 所属楼栋 |
| `floor_number` | 楼层数字，同一楼栋唯一 |
| `floor_name` | 展示名称 |
| `enabled` | 是否启用 |
| `version` | 乐观锁版本 |

### `room`

| 字段 | 类型 | 含义 |
|---|---|---|
| `id` | `BIGINT` | 房间主键 |
| `floor_id` | `BIGINT` | 所属楼层 |
| `room_number` | `VARCHAR(32)` | 房间号，同一楼层唯一 |
| `room_type` | `VARCHAR(32)` | `FOUR_PERSON`、`FIVE_PERSON`、`SIX_PERSON`或`OTHER` |
| `capacity` | `SMALLINT` | 可独立分配床位数量，当前业务最大8人 |
| `gender_restriction` | `VARCHAR(8)` | 固定房间性别，`M`或`F` |
| `operational_status` | `VARCHAR(32)` | `ENABLED`、`DISABLED`或`MAINTENANCE` |
| `state_version` | `BIGINT` | 实时床位状态版本 |
| `remark` | `VARCHAR(500) NULL` | 管理备注 |
| `version` | `INT` | 房间属性与布局乐观锁版本 |

`room_type`和`capacity`由床位可视化布局编辑器按独立床位数量自动同步，普通房间属性编辑器不得单独修改房型。

### `bed_frame`

| 字段 | 含义 |
|---|---|
| `id` | 床架主键 |
| `room_id` | 所属房间 |
| `frame_code` | 房间内床架编码 |
| `frame_type` | 当前主要为`BUNK_FRAME` |
| `enabled` | 是否启用 |
| `version` | 乐观锁版本 |

### `bed`

| 字段 | 类型 | 含义 |
|---|---|---|
| `id` | `BIGINT` | 独立可分配床位主键 |
| `room_id` | `BIGINT` | 所属房间 |
| `bed_frame_id` | `BIGINT NULL` | 上下铺共享床架；上床下桌为空 |
| `bed_code` | `VARCHAR(32)` | 房间内床位编码 |
| `bed_type` | `VARCHAR(32)` | `LOFT_BED_DESK`、`BUNK_UPPER`、`BUNK_LOWER`或`OTHER` |
| `position_index` | `SMALLINT` | 房间内唯一排序位置 |
| `operational_status` | `VARCHAR(32)` | 启用、禁用或维护 |
| `version` | `INT` | 乐观锁版本 |

空的上床下桌可在布局编辑器中转换为上下铺。转换时新增一个独立下铺床位、共享`bed_frame`并使房间容量增加1。已有住宿分配的床具不允许改型。

### `room_bed_layout`

| 字段 | 类型 | 含义 |
|---|---|---|
| `bed_id` | `BIGINT` | 床位主键，同时为布局主键 |
| `layout_x` | `DECIMAL(6,3)` | 房间横向坐标，范围`[-5.2,5.2]` |
| `layout_z` | `DECIMAL(6,3)` | 房间纵向坐标，范围`[-3.5,3.5]` |
| `rotation_degrees` | `SMALLINT` | 顺时针角度，只允许`0`、`90`、`180`、`270` |
| `updated_by` | `BIGINT` | 最后修改管理员 |
| `version` | `INT` | 布局记录版本 |
| `created_at`、`updated_at` | `DATETIME(3)` | 创建和更新时间 |

同一上下铺床架的上下层必须使用相同坐标和角度。管理端只允许拖动和顺时针旋转90度，不提供手工坐标输入。

## 个人偏好与匹配

### `questionnaire_version`

| 字段 | 含义 |
|---|---|
| `id` | 版本主键 |
| `version_code` | 版本编码，全局唯一 |
| `questionnaire_name` | 页面名称，统一使用“个人偏好” |
| `version_status` | 草稿、发布或归档状态 |
| `description` | 说明 |
| `published_at` | 发布时间 |

### `questionnaire_question`

| 字段 | 含义 |
|---|---|
| `id` | 题目主键 |
| `questionnaire_version_id` | 所属版本 |
| `question_code` | 稳定业务编码 |
| `question_text` | 页面问题文本 |
| `question_type` | 时间、整数或单选等类型 |
| `feature_key` | 生成匹配特征时使用的键 |
| `required_flag` | 是否必填 |
| `sort_order` | 展示顺序 |
| `enabled` | 是否启用 |

### `questionnaire_option`

| 字段 | 含义 |
|---|---|
| `id` | 选项主键 |
| `question_id` | 所属题目 |
| `option_code` | 同一题目内唯一选项编码 |
| `option_text` | 页面文本 |
| `feature_value` | 匹配计算值；可为空 |
| `sort_order` | 展示顺序 |
| `enabled` | 是否启用 |

界面选择必须使用唯一选项编码，不能仅使用可能重复的`feature_value`判断单选状态。

### `questionnaire_answer`

| 字段 | 含义 |
|---|---|
| `id` | 答案主键 |
| `batch_id` | 选寝批次 |
| `questionnaire_version_id` | 个人偏好版本 |
| `student_id` | 学生 |
| `question_id` | 题目 |
| `answer_json` | 原始答案JSON |
| `submitted_at` | 提交时间 |
| `version` | 乐观锁版本 |

同一批次、学生和题目仅允许一条答案。

### `student_feature`

| 字段 | 含义 |
|---|---|
| `id` | 特征主键 |
| `batch_id` | 批次 |
| `student_id` | 学生 |
| `algorithm_version` | 特征转换版本 |
| `feature_vector_json` | 标准化匹配向量 |
| `explanation_tags_json` | 用户画像和可解释标签 |
| `calculated_at` | 计算时间 |
| `source_answer_version` | 来源答案版本 |

### `matching_weight_scheme`

| 字段 | 含义 |
|---|---|
| `id` | 修订主键 |
| `scheme_code` | 方案族编码 |
| `scheme_name` | 方案名称 |
| `revision` | 修订号 |
| `algorithm_version` | 匹配算法版本 |
| `weights_json` | 各个人偏好维度权重 |
| `conflict_rules_json` | 冲突提示阈值和扣分规则 |
| `enabled` | 是否作为新批次默认修订 |
| `version` | 乐观锁版本 |
| `created_by` | 创建管理员 |
| `change_reason` | 修订原因 |
| `published_at` | 启用时间 |

## 选寝批次与组队

### `selection_batch`

| 字段 | 含义 |
|---|---|
| `id` | 批次主键 |
| `batch_code`、`batch_name` | 批次编码和名称 |
| `batch_status` | `DRAFT`、`PUBLISHED`、`OPEN`、`PAUSED`、`CLOSED`、`ALLOCATING`、`FINISHED`或`CANCELLED` |
| `questionnaire_version_id` | 使用的个人偏好版本 |
| `matching_weight_scheme_id` | 使用的匹配方案修订 |
| `start_at`、`end_at` | 开始和结束时间 |
| `hold_duration_seconds` | 临时保留秒数 |
| `hold_renewal_limit` | 保留续期次数 |
| `allow_team` | 是否允许组队 |
| `team_min_size`、`team_max_size` | 队伍人数限制；当前最大5人 |
| `allow_student_random` | 是否允许学生随机推荐 |
| `unselected_strategy` | 未自选学生处理策略 |
| `rule_version` | 批次规则版本 |
| `created_by`、`published_at`、`version` | 创建、发布和版本信息 |

### `batch_student_eligibility`

| 字段 | 含义 |
|---|---|
| `id` | 资格主键 |
| `batch_id` | 批次 |
| `student_id` | 学生 |
| `eligibility_status` | `ELIGIBLE`、`INELIGIBLE`等资格状态 |
| `reason` | 不符合资格的原因 |
| `created_at`、`updated_at` | 创建和更新时间 |

统一分配以该表中的`ELIGIBLE`记录为学生范围，不要求`app_user.account_status='ACTIVE'`。

### `active_batch_student_lock`

| 字段 | 含义 |
|---|---|
| `student_id` | 学生主键，同时为主键 |
| `batch_id` | 当前活动批次 |
| `created_at` | 加锁时间 |

保证同一学生同一时刻只属于一个发布中、开放中或暂停中的批次。

### `batch_building_scope`

| 字段 | 含义 |
|---|---|
| `id` | 范围主键 |
| `batch_id` | 批次 |
| `building_id` | 允许楼栋 |
| `created_at` | 创建时间 |

### `batch_room_scope`

| 字段 | 含义 |
|---|---|
| `id` | 范围主键 |
| `batch_id` | 批次 |
| `room_id` | 允许房间 |
| `created_at` | 创建时间 |

### `batch_bed_scope`

| 字段 | 含义 |
|---|---|
| `id` | 范围主键 |
| `batch_id` | 批次 |
| `bed_id` | 允许床位 |
| `created_at` | 创建时间 |

### `selection_team`

| 字段 | 含义 |
|---|---|
| `id` | 队伍主键 |
| `batch_id` | 所属批次 |
| `team_code`、`team_name` | 内部编码和名称 |
| `leader_student_id` | 队长 |
| `team_status` | `FORMING`、`LOCKED`、`SELECTING`、`COMPLETED`或`DISSOLVED` |
| `locked_at` | 锁定时间 |
| `version` | 乐观锁版本 |

### `selection_team_member`

| 字段 | 含义 |
|---|---|
| `id` | 成员主键 |
| `team_id` | 队伍 |
| `batch_id` | 冗余批次，用于有效成员唯一约束 |
| `student_id` | 学生 |
| `member_role` | `LEADER`或`MEMBER` |
| `member_status` | `INVITED`、`JOINED`、`LOCKED`、`LEFT`、`REMOVED`或`REJECTED` |
| `active_marker` | 由状态生成的有效成员唯一标记 |
| `joined_at`、`left_at` | 加入和离开时间 |

### `team_invitation`

| 字段 | 含义 |
|---|---|
| `id` | 邀请主键 |
| `team_id` | 队伍 |
| `inviter_student_id` | 邀请人 |
| `invitee_student_id` | 被邀请人 |
| `invitation_status` | `PENDING`、`ACCEPTED`、`REJECTED`、`EXPIRED`或`CANCELLED` |
| `invitation_token` | 邀请处理令牌，全局唯一 |
| `expires_at` | 过期时间 |
| `responded_at` | 处理时间 |

## 分配与审计

### `bed_assignment`

| 字段 | 类型 | 含义 |
|---|---|---|
| `id` | `BIGINT` | 当前分配主键 |
| `batch_id` | `BIGINT` | 批次 |
| `student_id` | `BIGINT` | 学生；同一批次唯一 |
| `bed_id` | `BIGINT` | 床位；同一批次唯一 |
| `team_id` | `BIGINT NULL` | 组队分配时关联队伍 |
| `assignment_method` | `VARCHAR(32)` | `SELF_SELECT`、`TEAM_SELECT`、`STUDENT_RANDOM`、`ADMIN_RANDOM`或`MANUAL_ADJUSTMENT` |
| `assignment_status` | `VARCHAR(32)` | 当前只允许`ACTIVE` |
| `allocation_run_id` | `BIGINT NULL` | 管理员统一分配执行 |
| `assigned_by` | `BIGINT NULL` | 管理员或操作账号；学生自选可为空 |
| `assigned_at` | `DATETIME(3)` | 分配时间 |
| `version` | `INT` | 乐观锁版本 |

### `assignment_history`

| 字段 | 含义 |
|---|---|
| `id` | 历史主键 |
| `assignment_id` | 当前分配主键；当前分配删除后可为空 |
| `batch_id`、`student_id`、`bed_id` | 事件对应批次、学生和床位 |
| `event_type` | `CREATED`、`ADJUSTED`、`CANCELLED`或`RESTORED` |
| `assignment_method` | 分配方式 |
| `operator_user_id` | 操作账号 |
| `reason` | 操作原因 |
| `previous_data`、`current_data` | 调整前后JSON快照 |
| `occurred_at` | 事件时间 |

### `allocation_run`

| 字段 | 含义 |
|---|---|
| `id` | 统一分配执行主键 |
| `batch_id` | 批次 |
| `execution_code` | 可读执行编号 |
| `idempotency_key` | 同一批次幂等键 |
| `run_mode` | `PREVIEW`或`COMMIT` |
| `run_status` | 创建、运行、成功、部分成功或失败 |
| `algorithm_version` | 分配算法版本 |
| `rule_version` | 批次规则版本 |
| `random_seed` | 可复现随机种子 |
| `student_snapshot_json` | 本次应分配学生快照，包含未激活学生 |
| `bed_snapshot_json` | 可用床位快照 |
| `summary_json` | 成功与失败统计 |
| `operator_user_id` | 执行管理员 |
| `started_at`、`finished_at`、`created_at` | 执行时间 |

### `allocation_run_result`

| 字段 | 类型 | 含义 |
|---|---|---|
| `id` | `BIGINT` | 结果主键 |
| `allocation_run_id` | `BIGINT` | 所属统一分配执行 |
| `student_id` | `BIGINT` | 学生；同一执行中唯一 |
| `bed_id` | `BIGINT NULL` | 成功分配的床位，失败时为空 |
| `result_status` | `VARCHAR(32)` | `ASSIGNED`、`UNASSIGNED`、`SKIPPED`或`FAILED` |
| `score` | `DECIMAL(10,4) NULL` | 分配或匹配分数 |
| `failure_code` | `VARCHAR(64) NULL` | 稳定失败代码，例如`NO_AVAILABLE_BED` |
| `explanation_json` | `JSON NULL` | 学生姓名、学号、失败原因或算法解释 |
| `created_at` | `DATETIME(3)` | 创建时间 |

管理员页面必须展示`UNASSIGNED`学生的姓名、学号、失败代码和失败原因。正常配置应使全部学生成功分配；性别容量或批次床位范围不足时才允许出现失败清单。

### `system_setting`

| 字段 | 含义 |
|---|---|
| `id` | 设置主键 |
| `setting_key` | 设置键，全局唯一 |
| `setting_value` | 设置值；学生欢迎语保存中英文JSON |
| `version` | 乐观锁版本 |
| `updated_by` | 最后修改管理员 |
| `created_at`、`updated_at` | 创建和更新时间 |

### `audit_log`

| 字段 | 含义 |
|---|---|
| `id` | 审计主键 |
| `request_id` | 请求追踪号 |
| `operator_user_id` | 操作账号，可为空 |
| `operator_type` | `STUDENT`、`ADMIN`或`SYSTEM` |
| `action_type` | 稳定动作编码，例如`ROOM_LAYOUT_UPDATE`、`STUDENT_PASSWORD_RESET`、`STUDENT_STATE_RESET` |
| `resource_type`、`resource_id` | 资源类型与标识 |
| `result_status` | `SUCCESS`、`FAILED`或`REJECTED` |
| `reason` | 操作原因 |
| `before_data`、`after_data` | 前后JSON快照 |
| `ip_address` | 请求地址 |
| `occurred_at` | 操作时间 |

## 核心唯一约束

| 约束 | 含义 |
|---|---|
| `uk_major_code`、`uk_major_name` | 专业编号和名称唯一 |
| `uk_student_number` | 学号全局唯一 |
| `uk_app_user_student` | 一个学生最多一个登录账号 |
| `uk_room_floor_number` | 同一楼层房间号唯一 |
| `uk_bed_room_code` | 同一房间床位编码唯一 |
| `uk_bed_room_position` | 同一房间位置序号唯一 |
| `PRIMARY KEY room_bed_layout(bed_id)` | 一个床位最多一条自定义布局 |
| `uk_answer_batch_student_question` | 同一批次学生题目答案唯一 |
| `uk_feature_batch_student` | 同一批次学生特征唯一 |
| `PRIMARY KEY active_batch_student_lock(student_id)` | 同一学生只能参加一个活动批次 |
| `uk_active_team_member` | 同一学生在一个批次最多属于一个有效队伍或待处理邀请 |
| `uk_assignment_batch_student` | 同一批次学生最多一个当前床位 |
| `uk_assignment_batch_bed` | 同一批次床位最多分配一名学生 |
| `uk_allocation_idempotency` | 同一批次统一分配请求幂等 |
| `uk_allocation_result_student` | 同一分配执行每名学生只有一条结果 |

## Flyway迁移历史

| 版本 | 文件 | 主要内容 |
|---|---|---|
| V1 | `V1__create_phase1_schema.sql` | 第一阶段基础表、选寝、组队、分配和审计结构 |
| V2 | `V2__enforce_fixed_room_gender.sql` | 固定房间性别约束 |
| V3 | `V3__normalize_major_and_minimize_student.sql` | 专业独立表、最小化学生档案 |
| V4 | `V4__refine_questionnaire_and_active_batch_rules.sql` | 三态吸烟偏好和活动批次唯一锁 |
| V5 | `V5__add_room_bed_layout.sql` | 床位可视化坐标与角度 |
| V6 | `V6__version_matching_weight_schemes.sql` | 匹配规则不可变修订 |
| V7 | `V7__add_student_welcome_settings.sql` | 首次欢迎确认与系统设置 |
| V8 | `V8__expand_personal_preferences.sql` | 夏冬空调、熄灯后活动、闹钟和气味偏好 |
| V9 | `V9__add_student_contact_and_notifications.sql` | 国籍、手机号、多语言欢迎语和学生通知 |

管理员学生密码/状态重置及全部学生统一分配不新增数据库表，复用现有账户、批次、个人偏好、组队、分配、通知和审计表。
