# 数据库数据字典

> 数据库：MySQL 8.4及以上版本  
> 字符集：`utf8mb4`  
> 结构唯一事实来源：`backend-java/server/src/main/resources/db/migration/`  
> 当前正式迁移版本：**V16**  
> 架构安装入口：`backend-java/docs/sql/schema.sql`

## 维护规则

本文件是项目长期维护的数据字典，不是一次性设计说明。

1. 每次新增Flyway迁移时，必须同步更新本字典；已执行迁移禁止修改，只能新增更高版本迁移。
2. 数据库结构变化后必须执行`python scripts/db/build_frozen_baseline.py`，更新`schema.sql`。
3. 新增表必须记录用途、字段、主外键、状态值、唯一约束和删除策略。
4. 测试数据入口`reset_and_seed_test_data.sql`必须与最新迁移、本字典和实际业务规则一致。
5. `created_at`、`updated_at`、`version`分别表示创建时间、更新时间和乐观锁版本；下文未重复说明时仍遵循该含义。

## 一、表清单

| 数据域 | 表名 | 用途 |
|---|---|---|
| 账户与学生 | `major` | 专业目录 |
| 账户与学生 | `app_user` | 学生、管理员和系统管理员账号 |
| 账户与学生 | `student` | 学生基础档案、国籍、类别和来源 |
| 账户与学生 | `student_notification` | 学生系统通知 |
| 导入 | `import_job` | 批量导入任务 |
| 导入 | `import_error` | 批量导入错误明细 |
| 宿舍 | `campus` | 校区 |
| 宿舍 | `dormitory_building` | 宿舍楼 |
| 宿舍 | `dormitory_floor` | 楼层 |
| 宿舍 | `room` | 房间、容量、性别和学生类别范围 |
| 宿舍 | `bed_frame` | 上下铺共享床架 |
| 宿舍 | `bed` | 独立可分配床位 |
| 宿舍 | `room_bed_layout` | 床位图形化位置和角度 |
| 个人偏好 | `questionnaire_version` | 个人偏好版本 |
| 个人偏好 | `questionnaire_question` | 偏好题目 |
| 个人偏好 | `questionnaire_option` | 题目选项 |
| 个人偏好 | `questionnaire_answer` | 学生原始答案 |
| 个人偏好 | `student_feature` | 匹配特征和画像标签 |
| 匹配 | `matching_weight_scheme` | 匹配权重不可变修订 |
| 批次规则 | `batch_rule_template` | 批次规则模板不可变修订 |
| 选寝批次 | `selection_batch` | 选寝活动及ROOM/BED模式 |
| 选寝批次 | `batch_student_eligibility` | 学生批次资格及加入来源 |
| 选寝批次 | `active_batch_student_lock` | 学生活动批次互斥锁 |
| 选寝批次 | `active_batch_room_lock` | 房间活动批次互斥锁 |
| 选寝批次 | `batch_building_scope` | 开放楼栋范围 |
| 选寝批次 | `batch_room_scope` | 开放房间范围 |
| 选寝批次 | `batch_bed_scope` | 开放床位范围 |
| 组队 | `selection_team` | 选寝队伍 |
| 组队 | `selection_team_member` | 队伍成员状态 |
| 组队 | `team_invitation` | 邀请令牌和处理状态 |
| 分配 | `bed_assignment` | BED模式批次具体床位结果 |
| 分配 | `assignment_history` | 床位结果调整历史 |
| 分配 | `allocation_run` | 管理员统一分配执行 |
| 分配 | `allocation_run_result` | 统一分配逐学生结果 |
| 在住 | `room_assignment` | 跨批次当前在住事实和实际床位 |
| 在住 | `room_assignment_history` | 在住、换寝、确认床位历史 |
| 系统配置 | `system_setting` | 欢迎语等系统设置 |
| 业务审计 | `audit_log` | 普通管理员和学生操作审计 |
| 平台权限 | `feature_catalog` | 程序功能目录 |
| 平台权限 | `quota_catalog` | 资源配额目录 |
| 平台权限 | `subscription_plan` | 套餐稳定主记录 |
| 平台权限 | `subscription_plan_revision` | 套餐不可变修订 |
| 平台权限 | `plan_revision_feature` | 套餐修订功能集合 |
| 平台权限 | `plan_revision_quota` | 套餐修订配额集合 |
| 平台权限 | `service_subscription` | 单客户订阅主记录 |
| 平台权限 | `service_subscription_revision` | 订阅不可变修订 |
| 平台权限 | `subscription_feature_override` | 临时功能增补或撤销 |
| 平台权限 | `subscription_quota_override` | 临时配额覆盖 |
| 平台权限 | `service_quota_alert` | 配额告警 |
| 平台权限 | `batch_entitlement_snapshot` | 批次启动时权限快照 |
| 平台审计 | `platform_audit_log` | 系统管理员平台操作审计 |

## 二、账户与学生

### `major`

| 字段 | 含义 |
|---|---|
| `id` | 专业主键 |
| `major_code` | 专业编号，全局唯一 |
| `major_name` | 专业名称，全局唯一 |
| `enabled` | 是否允许新学生关联 |

### `app_user`

| 字段 | 含义 |
|---|---|
| `id` | 用户主键 |
| `student_id` | 学生账号关联`student.id`；管理员为空 |
| `username` | 登录名；学生账号使用12位学号 |
| `password_hash` | Spring委托密码编码值；待激活账号可为空 |
| `user_type` | `STUDENT`、`ADMIN`或`SYSTEM_ADMIN` |
| `account_status` | `PENDING`、`ACTIVE`、`LOCKED`或`DISABLED` |
| `display_name` | 页面显示名称 |
| `last_login_at` | 最后登录时间 |
| `welcome_acknowledged_at` | 学生确认首次欢迎的时间 |
| `password_change_required` | 是否必须修改初始或重置后的密码 |
| `system_admin_marker` | 生成列；保证系统中最多一个`SYSTEM_ADMIN` |

关键约束：一个学生最多一个账号；系统管理员不能关联学生；系统管理员全局唯一。

### `student`

| 字段 | 含义 |
|---|---|
| `id` | 学生主键 |
| `student_number` | 12位数字学号，全局唯一 |
| `student_name` | 姓名 |
| `gender` | `M`或`F` |
| `major_id` | 专业外键 |
| `nationality_code` | ISO 3166-1二位国籍代码 |
| `student_category` | `DOMESTIC`国内生或`INTERNATIONAL`国际生 |
| `enrollment_source` | `INITIAL_IMPORT`、`TRANSFER_MANUAL`、`ADMIN_MANUAL`或`BATCH_IMPORT` |
| `phone_number` | 手机号码，允许学生本人修改 |

学生类别是住宿隔离判断的业务字段；国籍用于展示和默认语言选择。两者不能混用。

### `student_notification`

| 字段 | 含义 |
|---|---|
| `id` | 通知主键 |
| `student_id` | 接收学生，学生删除时级联删除 |
| `notification_type` | `TEAM_MEMBER_REMOVED`、`TEAM_DISSOLVED`或`TEAM_INVITATION_CANCELLED` |
| `title_key`、`message_key` | 前端国际化键 |
| `parameters_json` | 姓名、队伍等插值参数 |
| `read_at` | 阅读时间，为空表示未读 |

### `import_job`与`import_error`

| 表 | 关键字段与含义 |
|---|---|
| `import_job` | `import_type`导入类型；`file_name`文件名；`file_sha256`摘要；`operation_mode`校验/新增/更新；`job_status`执行状态；`total_rows`、`success_rows`、`failed_rows`统计；`operator_user_id`操作人 |
| `import_error` | `import_job_id`所属任务；`row_number`原行号；`field_name`字段；`error_code`稳定代码；`error_message`错误说明；`raw_data`原始JSON |

## 三、宿舍资源

### `campus`、`dormitory_building`、`dormitory_floor`

| 表 | 关键字段与含义 |
|---|---|
| `campus` | `campus_code`校区编码；`campus_name`名称；`address`地址；`enabled`是否启用 |
| `dormitory_building` | `campus_id`校区；`building_code`楼栋编码；`building_name`名称；`gender_restriction`楼栋性别范围；`enabled`是否启用 |
| `dormitory_floor` | `building_id`楼栋；`floor_number`楼层数字；`floor_name`展示名称；`enabled`是否启用 |

### `room`

| 字段 | 含义 |
|---|---|
| `id` | 房间主键 |
| `floor_id` | 楼层外键 |
| `room_number` | 同楼层唯一房间号 |
| `room_type` | `FOUR_PERSON`、`FIVE_PERSON`、`SIX_PERSON`或`OTHER` |
| `capacity` | 独立可分配床位数，业务最大8人 |
| `gender_restriction` | 固定房间性别`M`或`F` |
| `resident_scope` | `DOMESTIC_ONLY`、`INTERNATIONAL_ONLY`或`MIXED` |
| `operational_status` | `ENABLED`、`DISABLED`或`MAINTENANCE` |
| `state_version` | 实时状态版本 |
| `remark` | 管理备注 |

房型和容量由图形化床位布局保存时自动同步，普通房间编辑不得单独修改房型。

### `bed_frame`、`bed`、`room_bed_layout`

| 表 | 关键字段与含义 |
|---|---|
| `bed_frame` | `room_id`房间；`frame_code`床架编码；`frame_type`通常为`BUNK_FRAME`；`enabled`是否启用 |
| `bed` | `room_id`房间；`bed_frame_id`上下铺共享床架；`bed_code`床位编码；`bed_type`为`LOFT_BED_DESK`、`BUNK_UPPER`、`BUNK_LOWER`或`OTHER`；`position_index`排序；`operational_status`运行状态 |
| `room_bed_layout` | `bed_id`同时为主键；`layout_x`、`layout_z`画布坐标；`rotation_degrees`仅0/90/180/270；`updated_by`修改管理员 |

同一上下铺上下层共享坐标和角度。已有在住或床位分配的床具不允许改型。

## 四、个人偏好与匹配

| 表 | 关键字段与含义 |
|---|---|
| `questionnaire_version` | `version_code`版本编码；`questionnaire_name`名称；`version_status`草稿/发布/归档；`published_at`发布时间 |
| `questionnaire_question` | `question_code`稳定编码；`question_text`文本；`question_type`类型；`feature_key`匹配特征键；`required_flag`必填标志；`sort_order`顺序 |
| `questionnaire_option` | `question_id`题目；`option_code`唯一选项编码；`option_text`文本；`feature_value`匹配值；`sort_order`顺序 |
| `questionnaire_answer` | `batch_id`批次；`student_id`学生；`question_id`题目；`answer_json`原始答案；`submitted_at`提交时间 |
| `student_feature` | `batch_id`批次；`student_id`学生；`algorithm_version`算法版本；`feature_vector_json`向量；`explanation_tags_json`画像标签；`source_answer_version`答案版本 |
| `matching_weight_scheme` | `scheme_code`方案族；`revision`修订；`algorithm_version`算法；`weights_json`权重；`conflict_rules_json`冲突规则；`enabled`默认状态；`change_reason`修订原因 |

同一批次、学生、题目只允许一条答案；同一批次、学生只允许一条特征记录。

## 五、批次规则、选寝和组队

### `batch_rule_template`

| 字段 | 含义 |
|---|---|
| `rule_code`、`rule_name` | 模板族编码和名称 |
| `revision` | 不可变修订号 |
| `hold_duration_seconds` | 临时保留秒数 |
| `hold_renewal_limit` | 最大续期次数 |
| `allow_team` | 是否允许组队 |
| `team_min_size`、`team_max_size` | 队伍人数限制，启用模板最大5人 |
| `allow_student_random` | 是否允许随机推荐 |
| `unselected_strategy` | `NONE`或`ADMIN_ALLOCATION` |
| `rule_version` | 规则执行版本 |
| `enabled`、`is_default` | 可用与默认标志 |
| `change_reason` | 创建或修订原因 |

### `selection_batch`

| 字段 | 含义 |
|---|---|
| `id` | 批次主键 |
| `batch_code`、`batch_name` | 编码和名称 |
| `batch_status` | `DRAFT`、`PUBLISHED`、`OPEN`、`PAUSED`、`CLOSED`、`ALLOCATING`、`FINISHED`或`CANCELLED` |
| `selection_mode` | `ROOM`选择寝室或`BED`选择具体床位 |
| `separate_student_categories` | 是否强制国内生、国际生使用对应专用寝室 |
| `questionnaire_version_id` | 偏好版本 |
| `matching_weight_scheme_id` | 匹配方案修订 |
| `rule_template_id` | 批次规则模板修订 |
| `start_at`、`end_at` | 开始和结束时间 |
| `hold_duration_seconds`、`hold_renewal_limit` | 临时保留规则快照 |
| `allow_team`、`team_min_size`、`team_max_size` | 组队规则快照 |
| `allow_student_random` | 是否允许随机推荐 |
| `unselected_strategy` | 未自选学生处理策略 |
| `rule_version` | 规则版本快照 |

### `batch_student_eligibility`

| 字段 | 含义 |
|---|---|
| `batch_id`、`student_id` | 批次和学生 |
| `eligibility_status` | 资格状态 |
| `reason_code` | 不符合资格的稳定原因代码 |
| `source_type` | `INITIAL`、`IMPORT`、`ADMIN_MANUAL`或`TRANSFER_MANUAL` |
| `added_by`、`added_at` | 人工加入管理员和时间 |

统一分配和自选资格以本表的`ELIGIBLE`记录为准，不依赖账号是否激活。

### 批次范围与互斥锁

| 表 | 关键字段与含义 |
|---|---|
| `active_batch_student_lock` | `student_id`主键；`batch_id`活动批次；保证学生同时只参加一个活动批次 |
| `active_batch_room_lock` | `room_id`主键；`batch_id`占用批次；`selection_mode`模式快照；保证房间同时只属于一个活动批次 |
| `batch_building_scope` | `batch_id`、`building_id`开放楼栋 |
| `batch_room_scope` | `batch_id`、`room_id`开放房间 |
| `batch_bed_scope` | `batch_id`、`bed_id`开放床位 |

### 组队

| 表 | 关键字段与含义 |
|---|---|
| `selection_team` | `batch_id`批次；`team_code`内部编码；`leader_student_id`队长；`team_status`为`FORMING`、`LOCKED`、`SELECTING`、`COMPLETED`或`DISSOLVED`；`locked_at`锁定时间 |
| `selection_team_member` | `team_id`队伍；`student_id`学生；`member_role`队长/成员；`member_status`邀请、加入、锁定、离开、移除或拒绝；`active_marker`用于有效成员唯一约束 |
| `team_invitation` | `inviter_student_id`邀请人；`invitee_student_id`被邀请人；`invitation_status`待处理/接受/拒绝/过期/取消；`invitation_token`唯一令牌；`expires_at`过期时间 |

## 六、分配与跨批次在住

### `bed_assignment`

| 字段 | 含义 |
|---|---|
| `batch_id`、`student_id`、`bed_id` | 批次、学生和具体床位 |
| `team_id` | 组队分配时的队伍 |
| `assignment_method` | `SELF_SELECT`、`TEAM_SELECT`、`STUDENT_RANDOM`、`ADMIN_RANDOM`或`MANUAL_ADJUSTMENT` |
| `assignment_status` | 当前为`ACTIVE` |
| `allocation_run_id` | 统一分配执行 |
| `assigned_by`、`assigned_at` | 操作账号和时间 |

`bed_assignment`是批次BED模式结果；V16以后跨批次真实在住状态以`room_assignment`为准。

### `assignment_history`

记录床位结果的`CREATED`、`ADJUSTED`、`CANCELLED`、`RESTORED`事件，包含`previous_data`和`current_data`JSON快照。

### `allocation_run`与`allocation_run_result`

| 表 | 关键字段与含义 |
|---|---|
| `allocation_run` | `batch_id`批次；`idempotency_key`幂等键；`run_mode`预演/提交；`run_status`状态；`algorithm_version`算法版本；`student_snapshot_json`学生快照；`bed_snapshot_json`床位快照；`summary_json`汇总 |
| `allocation_run_result` | `allocation_run_id`执行；`student_id`学生且同次执行唯一；`bed_id`成功床位；`result_status`分配/未分配/跳过/失败；`failure_code`稳定失败码；`explanation_json`姓名、学号和原因 |

### `room_assignment`

| 字段 | 含义 |
|---|---|
| `id` | 在住主键 |
| `batch_id` | 来源批次，可为空，管理员直接分配时为空 |
| `student_id` | 学生 |
| `room_id` | 当前在住房间 |
| `bed_id` | 实际床位；ROOM模式初始可为空 |
| `team_id` | 来源队伍 |
| `source_selection_mode` | `ROOM`、`BED`或`DIRECT` |
| `assignment_method` | `ROOM_SELECT`、`TEAM_ROOM_SELECT`、`BED_SELECT`、`TEAM_BED_SELECT`、`DIRECT_ROOM`、`DIRECT_BED`、`IMPORT_MIGRATION`或`MANUAL_ADJUSTMENT` |
| `assignment_status` | `ACTIVE`或`ENDED` |
| `assigned_by`、`assigned_at` | 分配账号和时间 |
| `bed_confirmed_at` | 实际床位确认时间 |
| `ended_at`、`end_reason` | 在住结束时间和原因 |
| `active_student_marker` | 生成列，保证学生最多一条活动在住 |
| `active_bed_marker` | 生成列，保证实际床位最多一名活动住户 |

### `room_assignment_history`

| 字段 | 含义 |
|---|---|
| `room_assignment_id` | 在住记录，可因历史保留而为空 |
| `student_id`、`room_id`、`bed_id` | 学生、房间和实际床位 |
| `event_type` | `ROOM_ASSIGNED`、`BED_ASSIGNED`、`BED_CONFIRMED`、`BED_CHANGED`、`ROOM_CHANGED`、`RESIDENCY_ENDED`或`MIGRATED` |
| `operator_user_id` | 操作管理员或学生 |
| `reason` | 变更原因 |
| `previous_data`、`current_data` | 前后JSON快照 |
| `occurred_at` | 发生时间 |

## 七、系统配置与审计

| 表 | 关键字段与含义 |
|---|---|
| `system_setting` | `setting_key`唯一键；`setting_value`设置值，欢迎语为中英文JSON；`updated_by`修改管理员 |
| `audit_log` | `request_id`追踪号；`operator_user_id`操作账号；`operator_type`学生/管理员/系统；`action_type`动作编码；`resource_type`、`resource_id`资源；`result_status`结果；`reason`原因；`before_data`、`after_data`快照；`ip_address`地址 |

## 八、订阅、功能与配额

### 功能和配额目录

| 表 | 关键字段与含义 |
|---|---|
| `feature_catalog` | `feature_code`主键；`feature_name`名称；`phase`阶段；`scope`管理员/学生/共享；`granularity`模块/操作；`action_type`动作；`risk_level`风险；`enabled_in_program`程序是否支持 |
| `quota_catalog` | `quota_code`主键；`quota_name`名称；`unit_name`单位；`enabled_in_program`程序是否支持 |

### 套餐与订阅

| 表 | 关键字段与含义 |
|---|---|
| `subscription_plan` | `plan_code`稳定编码；`plan_name`名称；`enabled`是否启用；`created_by`创建者 |
| `subscription_plan_revision` | `plan_id`套餐；`revision`不可变修订号；`revision_name`名称；`description`说明；`enabled`状态；`change_reason`原因 |
| `plan_revision_feature` | 联合主键`plan_revision_id`、`feature_code`，定义修订拥有的功能 |
| `plan_revision_quota` | 联合主键`plan_revision_id`、`quota_code`；`quota_value`配额值 |
| `service_subscription` | `subscription_code`稳定编码；`singleton_key=1`保证单客户唯一订阅 |
| `service_subscription_revision` | `subscription_id`订阅；`revision`不可变修订；`plan_revision_id`套餐修订；`subscription_type`试用/定期/长期；`service_status`服务状态；`start_at`、`end_at`期限；`emergency_stopped`紧急停用；`is_current`当前修订标志 |

### 覆盖、告警和快照

| 表 | 关键字段与含义 |
|---|---|
| `subscription_feature_override` | `subscription_id`订阅；`feature_code`功能；`override_type`授权/撤销；`effective_from`、`effective_until`有效期；`change_reason`原因 |
| `subscription_quota_override` | `subscription_id`订阅；`quota_code`配额；`quota_value`覆盖值；有效期和原因 |
| `service_quota_alert` | `quota_code`配额；`alert_level`80%告警或100%超额；`used_value`、`limit_value`；`recovered_at`恢复时间；`active_marker`保证同级活动告警唯一 |
| `batch_entitlement_snapshot` | `batch_id`批次唯一；`subscription_revision_id`订阅修订；`granted_features_json`功能快照；`quota_snapshot_json`配额快照；`snapshot_version`快照版本 |
| `platform_audit_log` | `operation_type`平台动作；`operator_user_id`系统管理员；`target_type`、`target_id`目标；`change_reason`原因；`before_json`、`after_json`快照；`success`结果；`error_code`错误码 |

## 九、核心唯一约束

| 约束 | 含义 |
|---|---|
| `uk_student_number` | 学号全局唯一 |
| `uk_app_user_student` | 一个学生最多一个登录账号 |
| `uk_single_system_admin` | 全局最多一个系统管理员 |
| `uk_room_floor_number` | 同一楼层房间号唯一 |
| `uk_bed_room_code`、`uk_bed_room_position` | 同一房间床位编码和位置唯一 |
| `PRIMARY KEY room_bed_layout(bed_id)` | 每个床位最多一条布局 |
| `uk_answer_batch_student_question` | 同一批次学生题目答案唯一 |
| `uk_feature_batch_student` | 同一批次学生特征唯一 |
| `uk_weight_scheme_revision` | 匹配方案族修订唯一 |
| `uk_batch_rule_template_revision` | 批次规则模板修订唯一 |
| `PRIMARY KEY active_batch_student_lock(student_id)` | 学生同时只参加一个活动批次 |
| `PRIMARY KEY active_batch_room_lock(room_id)` | 房间同时只属于一个活动批次 |
| `uk_active_team_member` | 学生在同一批次最多一个有效队伍关系 |
| `uk_assignment_batch_student`、`uk_assignment_batch_bed` | BED模式批次内学生和床位唯一 |
| `uk_allocation_idempotency` | 统一分配幂等 |
| `uk_allocation_result_student` | 同一执行每名学生仅一条结果 |
| `uk_active_residency_student` | 学生最多一条活动在住记录 |
| `uk_active_residency_bed` | 实际床位最多一名活动住户 |
| `uk_subscription_plan_code` | 套餐编码唯一 |
| `uk_plan_revision` | 套餐修订唯一 |
| `uk_single_service_subscription` | 单客户订阅主记录唯一 |
| `uk_subscription_current` | 每个订阅最多一个当前修订 |
| `uk_batch_entitlement_snapshot` | 每批次一份权限快照 |

## 十、Flyway迁移历史

| 版本 | 文件 | 主要内容 |
|---|---|---|
| V1 | `V1__create_phase1_schema.sql` | 第一阶段基础表、选寝、组队、分配和审计 |
| V2 | `V2__enforce_fixed_room_gender.sql` | 固定房间性别约束 |
| V3 | `V3__normalize_major_and_minimize_student.sql` | 专业独立表和最小学生档案 |
| V4 | `V4__refine_questionnaire_and_active_batch_rules.sql` | 三态偏好和学生活动批次锁 |
| V5 | `V5__add_room_bed_layout.sql` | 床位坐标与角度 |
| V6 | `V6__version_matching_weight_schemes.sql` | 匹配规则不可变修订 |
| V7 | `V7__add_student_welcome_settings.sql` | 首次欢迎和系统设置 |
| V8 | `V8__expand_personal_preferences.sql` | 空调、熄灯、闹钟和气味偏好 |
| V9 | `V9__add_student_contact_and_notifications.sql` | 国籍、手机号、多语言欢迎语和通知 |
| V10 | `V10__add_batch_rule_templates.sql` | 批次规则模板不可变修订 |
| V11 | `V11__harden_welcome_message_json.sql` | 欢迎语JSON数据加固 |
| V12 | `V12__add_single_client_subscription_entitlements.sql` | 系统管理员、订阅、功能和配额基础结构 |
| V13 | `V13__seed_single_client_subscription_catalog.sql` | 功能目录、配额目录、默认套餐和系统管理员种子 |
| V14 | `V14__fix_system_admin_password_encoding.sql` | 修复系统管理员密码编码前缀 |
| V15 | `V15__add_batch_selection_modes.sql` | ROOM/BED双模式、房间锁和寝室结果 |
| V16 | `V16__add_residency_student_category_and_transfer_support.sql` | 学生类别、分类寝室、转学生和跨批次在住事实 |

## 十一、脚本职责

- `scripts/dev/reset-local-environment.sh`：只清空并重启本地MySQL、Redis。
- `backend-java/docs/sql/schema.sql`：按V1至V16顺序建立架构。
- `backend-java/docs/sql/reset_and_seed_test_data.sql`：导入500人中外学生混合测试数据。
- `backend-java/docs/sql/reset_and_seed_test_data_core.sql`：内部核心数据生成脚本，由主测试数据入口调用。
