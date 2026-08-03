-- ============================================================
-- 武汉科技大学学生宿舍智能选择系统
-- 第一阶段数据库结构基线
-- 数据库：MySQL 8.4+
-- 字符集：utf8mb4
-- 说明：开发期间由 Flyway 管理，禁止修改已执行迁移。
-- ============================================================

SET NAMES utf8mb4;

CREATE TABLE organization (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '组织主键',
    parent_id BIGINT NULL COMMENT '父组织主键',
    organization_code VARCHAR(64) NOT NULL COMMENT '组织编码',
    organization_name VARCHAR(128) NOT NULL COMMENT '组织名称',
    organization_type VARCHAR(32) NOT NULL COMMENT '组织类型：UNIVERSITY/COLLEGE/MAJOR/CLASS',
    sort_order INT NOT NULL DEFAULT 0 COMMENT '排序号',
    enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用',
    version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    CONSTRAINT fk_organization_parent FOREIGN KEY (parent_id) REFERENCES organization(id) ON DELETE RESTRICT,
    CONSTRAINT ck_organization_type CHECK (organization_type IN ('UNIVERSITY','COLLEGE','MAJOR','CLASS')),
    CONSTRAINT ck_organization_enabled CHECK (enabled IN (0,1)),
    UNIQUE KEY uk_organization_code (organization_code),
    KEY idx_organization_parent (parent_id),
    KEY idx_organization_type_enabled (organization_type, enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='学校组织结构';

CREATE TABLE app_user (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '用户主键',
    username VARCHAR(64) NOT NULL COMMENT '登录名',
    password_hash VARCHAR(255) NULL COMMENT '密码哈希；外部身份认证时可为空',
    user_type VARCHAR(32) NOT NULL COMMENT '用户类型：STUDENT/ADMIN',
    account_status VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT '账户状态',
    display_name VARCHAR(128) NOT NULL COMMENT '显示名称',
    last_login_at DATETIME(3) NULL COMMENT '最后登录时间',
    version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    CONSTRAINT ck_app_user_type CHECK (user_type IN ('STUDENT','ADMIN')),
    CONSTRAINT ck_app_user_status CHECK (account_status IN ('PENDING','ACTIVE','LOCKED','DISABLED')),
    UNIQUE KEY uk_app_user_username (username),
    KEY idx_app_user_type_status (user_type, account_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统用户';

CREATE TABLE campus (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '校区主键',
    campus_code VARCHAR(32) NOT NULL COMMENT '校区编码',
    campus_name VARCHAR(128) NOT NULL COMMENT '校区名称',
    address VARCHAR(255) NULL COMMENT '校区地址',
    enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用',
    version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    CONSTRAINT ck_campus_enabled CHECK (enabled IN (0,1)),
    UNIQUE KEY uk_campus_code (campus_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='校区';

CREATE TABLE student (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '学生主键',
    user_id BIGINT NULL COMMENT '关联用户主键',
    student_number CHAR(12) NOT NULL COMMENT '12位数字学号',
    student_name VARCHAR(128) NOT NULL COMMENT '学生姓名',
    gender CHAR(1) NOT NULL COMMENT '性别：M/F',
    organization_id BIGINT NOT NULL COMMENT '班级或所属组织主键',
    campus_id BIGINT NOT NULL COMMENT '所属校区主键',
    grade_year SMALLINT NOT NULL COMMENT '入学年级',
    major_name VARCHAR(128) NOT NULL COMMENT '专业名称快照',
    class_name VARCHAR(128) NOT NULL COMMENT '班级名称快照',
    housing_eligibility VARCHAR(32) NOT NULL DEFAULT 'ELIGIBLE' COMMENT '住宿资格',
    profile_status VARCHAR(32) NOT NULL DEFAULT 'INCOMPLETE' COMMENT '资料状态',
    data_source VARCHAR(32) NOT NULL DEFAULT 'IMPORT' COMMENT '数据来源',
    version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    CONSTRAINT fk_student_user FOREIGN KEY (user_id) REFERENCES app_user(id) ON DELETE RESTRICT,
    CONSTRAINT fk_student_organization FOREIGN KEY (organization_id) REFERENCES organization(id) ON DELETE RESTRICT,
    CONSTRAINT fk_student_campus FOREIGN KEY (campus_id) REFERENCES campus(id) ON DELETE RESTRICT,
    CONSTRAINT ck_student_number CHECK (student_number REGEXP '^[0-9]{12}$'),
    CONSTRAINT ck_student_gender CHECK (gender IN ('M','F')),
    CONSTRAINT ck_student_grade CHECK (grade_year BETWEEN 2000 AND 2200),
    CONSTRAINT ck_student_eligibility CHECK (housing_eligibility IN ('ELIGIBLE','INELIGIBLE','SUSPENDED')),
    CONSTRAINT ck_student_profile_status CHECK (profile_status IN ('INCOMPLETE','COMPLETE','LOCKED')),
    CONSTRAINT ck_student_data_source CHECK (data_source IN ('IMPORT','MANUAL','SYNTHETIC_PHASE1')),
    UNIQUE KEY uk_student_number (student_number),
    UNIQUE KEY uk_student_user (user_id),
    KEY idx_student_org (organization_id),
    KEY idx_student_campus_gender (campus_id, gender),
    KEY idx_student_eligibility (housing_eligibility)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='学生档案';

CREATE TABLE dormitory_building (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '宿舍楼主键',
    campus_id BIGINT NOT NULL COMMENT '校区主键',
    building_code VARCHAR(32) NOT NULL COMMENT '宿舍楼编码',
    building_name VARCHAR(128) NOT NULL COMMENT '宿舍楼名称',
    gender_restriction VARCHAR(8) NOT NULL COMMENT '性别限制：M/F/ANY',
    enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用',
    version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    CONSTRAINT fk_building_campus FOREIGN KEY (campus_id) REFERENCES campus(id) ON DELETE RESTRICT,
    CONSTRAINT ck_building_gender CHECK (gender_restriction IN ('M','F','ANY')),
    CONSTRAINT ck_building_enabled CHECK (enabled IN (0,1)),
    UNIQUE KEY uk_building_campus_code (campus_id, building_code),
    KEY idx_building_gender_enabled (gender_restriction, enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='宿舍楼';

CREATE TABLE dormitory_floor (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '楼层主键',
    building_id BIGINT NOT NULL COMMENT '宿舍楼主键',
    floor_number SMALLINT NOT NULL COMMENT '楼层号',
    floor_name VARCHAR(64) NOT NULL COMMENT '楼层名称',
    enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用',
    version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    CONSTRAINT fk_floor_building FOREIGN KEY (building_id) REFERENCES dormitory_building(id) ON DELETE RESTRICT,
    CONSTRAINT ck_floor_number CHECK (floor_number BETWEEN -5 AND 100),
    CONSTRAINT ck_floor_enabled CHECK (enabled IN (0,1)),
    UNIQUE KEY uk_floor_building_number (building_id, floor_number)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='宿舍楼层';

CREATE TABLE room (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '房间主键',
    floor_id BIGINT NOT NULL COMMENT '楼层主键',
    room_number VARCHAR(32) NOT NULL COMMENT '房间号',
    room_type VARCHAR(32) NOT NULL COMMENT '房型',
    capacity SMALLINT NOT NULL COMMENT '规划床位容量',
    gender_restriction VARCHAR(8) NOT NULL COMMENT '性别限制：M/F/ANY',
    operational_status VARCHAR(32) NOT NULL DEFAULT 'ENABLED' COMMENT '运行状态',
    state_version BIGINT NOT NULL DEFAULT 0 COMMENT '房间实时状态版本',
    remark VARCHAR(500) NULL COMMENT '备注',
    version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    CONSTRAINT fk_room_floor FOREIGN KEY (floor_id) REFERENCES dormitory_floor(id) ON DELETE RESTRICT,
    CONSTRAINT ck_room_type CHECK (room_type IN ('FOUR_PERSON','FIVE_PERSON','SIX_PERSON','OTHER')),
    CONSTRAINT ck_room_capacity CHECK (capacity BETWEEN 1 AND 20),
    CONSTRAINT ck_room_gender CHECK (gender_restriction IN ('M','F','ANY')),
    CONSTRAINT ck_room_status CHECK (operational_status IN ('ENABLED','DISABLED','MAINTENANCE')),
    UNIQUE KEY uk_room_floor_number (floor_id, room_number),
    KEY idx_room_status_gender (operational_status, gender_restriction)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='宿舍房间';

CREATE TABLE bed_frame (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '床架主键',
    room_id BIGINT NOT NULL COMMENT '房间主键',
    frame_code VARCHAR(32) NOT NULL COMMENT '床架编码',
    frame_type VARCHAR(32) NOT NULL COMMENT '床架类型',
    enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用',
    version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    CONSTRAINT fk_bed_frame_room FOREIGN KEY (room_id) REFERENCES room(id) ON DELETE RESTRICT,
    CONSTRAINT ck_bed_frame_type CHECK (frame_type IN ('BUNK_FRAME','OTHER')),
    CONSTRAINT ck_bed_frame_enabled CHECK (enabled IN (0,1)),
    UNIQUE KEY uk_bed_frame_room_code (room_id, frame_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='共享床架';

CREATE TABLE bed (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '床位主键',
    room_id BIGINT NOT NULL COMMENT '房间主键',
    bed_frame_id BIGINT NULL COMMENT '共享床架主键；上床下桌可为空',
    bed_code VARCHAR(32) NOT NULL COMMENT '床位编码',
    bed_type VARCHAR(32) NOT NULL COMMENT '床位类型',
    position_index SMALLINT NOT NULL COMMENT '房间内排序号',
    operational_status VARCHAR(32) NOT NULL DEFAULT 'ENABLED' COMMENT '运行状态',
    version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    CONSTRAINT fk_bed_room FOREIGN KEY (room_id) REFERENCES room(id) ON DELETE RESTRICT,
    CONSTRAINT fk_bed_frame FOREIGN KEY (bed_frame_id) REFERENCES bed_frame(id) ON DELETE RESTRICT,
    CONSTRAINT ck_bed_type CHECK (bed_type IN ('LOFT_BED_DESK','BUNK_UPPER','BUNK_LOWER','OTHER')),
    CONSTRAINT ck_bed_position CHECK (position_index BETWEEN 1 AND 20),
    CONSTRAINT ck_bed_status CHECK (operational_status IN ('ENABLED','DISABLED','MAINTENANCE')),
    UNIQUE KEY uk_bed_room_code (room_id, bed_code),
    UNIQUE KEY uk_bed_room_position (room_id, position_index),
    KEY idx_bed_room_status (room_id, operational_status),
    KEY idx_bed_frame (bed_frame_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='独立床位';

CREATE TABLE import_job (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '导入任务主键',
    import_type VARCHAR(32) NOT NULL COMMENT '导入类型',
    file_name VARCHAR(255) NOT NULL COMMENT '原文件名',
    file_sha256 CHAR(64) NULL COMMENT '文件摘要',
    operation_mode VARCHAR(32) NOT NULL COMMENT 'VALIDATE/INSERT/UPSERT',
    job_status VARCHAR(32) NOT NULL COMMENT '任务状态',
    total_rows INT NOT NULL DEFAULT 0,
    success_rows INT NOT NULL DEFAULT 0,
    failed_rows INT NOT NULL DEFAULT 0,
    operator_user_id BIGINT NOT NULL COMMENT '操作用户',
    started_at DATETIME(3) NULL,
    finished_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    CONSTRAINT fk_import_job_operator FOREIGN KEY (operator_user_id) REFERENCES app_user(id) ON DELETE RESTRICT,
    CONSTRAINT ck_import_type CHECK (import_type IN ('STUDENT','DORMITORY')),
    CONSTRAINT ck_import_mode CHECK (operation_mode IN ('VALIDATE','INSERT','UPSERT')),
    CONSTRAINT ck_import_status CHECK (job_status IN ('CREATED','VALIDATING','RUNNING','SUCCEEDED','PARTIAL_SUCCESS','FAILED')),
    KEY idx_import_job_type_status (import_type, job_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='数据导入任务';

CREATE TABLE import_error (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '导入错误主键',
    import_job_id BIGINT NOT NULL COMMENT '导入任务主键',
    `row_number` INT NOT NULL COMMENT '原始行号',
    field_name VARCHAR(128) NULL COMMENT '错误字段',
    error_code VARCHAR(64) NOT NULL COMMENT '错误代码',
    error_message VARCHAR(500) NOT NULL COMMENT '错误说明',
    raw_data JSON NULL COMMENT '原始行数据',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    CONSTRAINT fk_import_error_job FOREIGN KEY (import_job_id) REFERENCES import_job(id) ON DELETE CASCADE,
    KEY idx_import_error_job_row (import_job_id, `row_number`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='数据导入错误';

CREATE TABLE questionnaire_version (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '问卷版本主键',
    version_code VARCHAR(32) NOT NULL COMMENT '版本编码',
    questionnaire_name VARCHAR(128) NOT NULL COMMENT '问卷名称',
    version_status VARCHAR(32) NOT NULL DEFAULT 'DRAFT' COMMENT '版本状态',
    description VARCHAR(500) NULL,
    published_at DATETIME(3) NULL,
    version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    CONSTRAINT ck_questionnaire_status CHECK (version_status IN ('DRAFT','PUBLISHED','ARCHIVED')),
    UNIQUE KEY uk_questionnaire_version_code (version_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='生活习惯问卷版本';

CREATE TABLE questionnaire_question (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '问卷题目主键',
    questionnaire_version_id BIGINT NOT NULL COMMENT '问卷版本主键',
    question_code VARCHAR(64) NOT NULL COMMENT '题目编码',
    question_text VARCHAR(500) NOT NULL COMMENT '题目文本',
    question_type VARCHAR(32) NOT NULL COMMENT '题目类型',
    feature_key VARCHAR(64) NOT NULL COMMENT '特征键',
    required_flag TINYINT NOT NULL DEFAULT 1 COMMENT '是否必填',
    sort_order INT NOT NULL DEFAULT 0,
    enabled TINYINT NOT NULL DEFAULT 1,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    CONSTRAINT fk_question_version FOREIGN KEY (questionnaire_version_id) REFERENCES questionnaire_version(id) ON DELETE RESTRICT,
    CONSTRAINT ck_question_type CHECK (question_type IN ('SINGLE_CHOICE','MULTIPLE_CHOICE','INTEGER','TIME','BOOLEAN')),
    CONSTRAINT ck_question_required CHECK (required_flag IN (0,1)),
    CONSTRAINT ck_question_enabled CHECK (enabled IN (0,1)),
    UNIQUE KEY uk_questionnaire_question_code (questionnaire_version_id, question_code),
    UNIQUE KEY uk_questionnaire_feature_key (questionnaire_version_id, feature_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='问卷题目';

CREATE TABLE questionnaire_option (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '问卷选项主键',
    question_id BIGINT NOT NULL COMMENT '题目主键',
    option_code VARCHAR(64) NOT NULL COMMENT '选项编码',
    option_text VARCHAR(255) NOT NULL COMMENT '选项文本',
    feature_value DECIMAL(10,4) NULL COMMENT '标准化前特征值',
    sort_order INT NOT NULL DEFAULT 0,
    enabled TINYINT NOT NULL DEFAULT 1,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    CONSTRAINT fk_option_question FOREIGN KEY (question_id) REFERENCES questionnaire_question(id) ON DELETE CASCADE,
    CONSTRAINT ck_option_enabled CHECK (enabled IN (0,1)),
    UNIQUE KEY uk_question_option_code (question_id, option_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='问卷选项';

CREATE TABLE matching_weight_scheme (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '匹配权重方案主键',
    scheme_code VARCHAR(32) NOT NULL COMMENT '方案编码',
    scheme_name VARCHAR(128) NOT NULL COMMENT '方案名称',
    algorithm_version VARCHAR(32) NOT NULL COMMENT '算法版本',
    weights_json JSON NOT NULL COMMENT '各特征权重',
    conflict_rules_json JSON NOT NULL COMMENT '关键冲突规则',
    enabled TINYINT NOT NULL DEFAULT 1,
    version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    CONSTRAINT ck_weight_scheme_enabled CHECK (enabled IN (0,1)),
    UNIQUE KEY uk_weight_scheme_code (scheme_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='匹配权重方案';

CREATE TABLE selection_batch (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '选寝批次主键',
    batch_code VARCHAR(32) NOT NULL COMMENT '批次编码',
    batch_name VARCHAR(128) NOT NULL COMMENT '批次名称',
    batch_status VARCHAR(32) NOT NULL DEFAULT 'DRAFT' COMMENT '批次状态',
    questionnaire_version_id BIGINT NOT NULL COMMENT '问卷版本主键',
    matching_weight_scheme_id BIGINT NOT NULL COMMENT '匹配权重方案主键',
    start_at DATETIME(3) NOT NULL COMMENT '开始时间',
    end_at DATETIME(3) NOT NULL COMMENT '结束时间',
    hold_duration_seconds INT NOT NULL DEFAULT 300 COMMENT '临时占用秒数',
    hold_renewal_limit SMALLINT NOT NULL DEFAULT 0 COMMENT '最大续期次数',
    allow_team TINYINT NOT NULL DEFAULT 1 COMMENT '是否允许组队',
    team_min_size SMALLINT NOT NULL DEFAULT 2,
    team_max_size SMALLINT NOT NULL DEFAULT 5,
    allow_student_random TINYINT NOT NULL DEFAULT 1 COMMENT '是否允许学生随机选择',
    unselected_strategy VARCHAR(32) NOT NULL DEFAULT 'ADMIN_ALLOCATION' COMMENT '未选学生处理策略',
    rule_version VARCHAR(32) NOT NULL COMMENT '规则版本',
    created_by BIGINT NOT NULL COMMENT '创建用户',
    published_at DATETIME(3) NULL,
    version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    CONSTRAINT fk_batch_questionnaire FOREIGN KEY (questionnaire_version_id) REFERENCES questionnaire_version(id) ON DELETE RESTRICT,
    CONSTRAINT fk_batch_weight_scheme FOREIGN KEY (matching_weight_scheme_id) REFERENCES matching_weight_scheme(id) ON DELETE RESTRICT,
    CONSTRAINT fk_batch_creator FOREIGN KEY (created_by) REFERENCES app_user(id) ON DELETE RESTRICT,
    CONSTRAINT ck_batch_status CHECK (batch_status IN ('DRAFT','PUBLISHED','OPEN','PAUSED','CLOSED','ALLOCATING','FINISHED','CANCELLED')),
    CONSTRAINT ck_batch_time CHECK (end_at > start_at),
    CONSTRAINT ck_batch_hold_duration CHECK (hold_duration_seconds BETWEEN 30 AND 3600),
    CONSTRAINT ck_batch_renewal_limit CHECK (hold_renewal_limit BETWEEN 0 AND 20),
    CONSTRAINT ck_batch_allow_team CHECK (allow_team IN (0,1)),
    CONSTRAINT ck_batch_team_size CHECK (team_min_size BETWEEN 1 AND team_max_size AND team_max_size <= 20),
    CONSTRAINT ck_batch_allow_random CHECK (allow_student_random IN (0,1)),
    CONSTRAINT ck_batch_unselected_strategy CHECK (unselected_strategy IN ('NONE','ADMIN_ALLOCATION')),
    UNIQUE KEY uk_selection_batch_code (batch_code),
    KEY idx_batch_status_time (batch_status, start_at, end_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='选寝批次';

CREATE TABLE batch_student_eligibility (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '批次资格主键',
    batch_id BIGINT NOT NULL COMMENT '选寝批次主键',
    student_id BIGINT NOT NULL COMMENT '学生主键',
    eligibility_status VARCHAR(32) NOT NULL DEFAULT 'ELIGIBLE' COMMENT '资格状态',
    reason_code VARCHAR(64) NULL COMMENT '原因代码',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    CONSTRAINT fk_eligibility_batch FOREIGN KEY (batch_id) REFERENCES selection_batch(id) ON DELETE CASCADE,
    CONSTRAINT fk_eligibility_student FOREIGN KEY (student_id) REFERENCES student(id) ON DELETE RESTRICT,
    CONSTRAINT ck_batch_eligibility_status CHECK (eligibility_status IN ('ELIGIBLE','INELIGIBLE','SUSPENDED')),
    UNIQUE KEY uk_batch_student_eligibility (batch_id, student_id),
    KEY idx_eligibility_status (batch_id, eligibility_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='批次学生资格';

CREATE TABLE batch_building_scope (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '批次楼栋范围主键',
    batch_id BIGINT NOT NULL,
    building_id BIGINT NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    CONSTRAINT fk_batch_building_scope_batch FOREIGN KEY (batch_id) REFERENCES selection_batch(id) ON DELETE CASCADE,
    CONSTRAINT fk_batch_building_scope_building FOREIGN KEY (building_id) REFERENCES dormitory_building(id) ON DELETE RESTRICT,
    UNIQUE KEY uk_batch_building_scope (batch_id, building_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='批次允许楼栋';

CREATE TABLE batch_room_scope (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '批次房间范围主键',
    batch_id BIGINT NOT NULL,
    room_id BIGINT NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    CONSTRAINT fk_batch_room_scope_batch FOREIGN KEY (batch_id) REFERENCES selection_batch(id) ON DELETE CASCADE,
    CONSTRAINT fk_batch_room_scope_room FOREIGN KEY (room_id) REFERENCES room(id) ON DELETE RESTRICT,
    UNIQUE KEY uk_batch_room_scope (batch_id, room_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='批次允许房间';

CREATE TABLE batch_bed_scope (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '批次床位范围主键',
    batch_id BIGINT NOT NULL,
    bed_id BIGINT NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    CONSTRAINT fk_batch_bed_scope_batch FOREIGN KEY (batch_id) REFERENCES selection_batch(id) ON DELETE CASCADE,
    CONSTRAINT fk_batch_bed_scope_bed FOREIGN KEY (bed_id) REFERENCES bed(id) ON DELETE RESTRICT,
    UNIQUE KEY uk_batch_bed_scope (batch_id, bed_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='批次允许床位';

CREATE TABLE questionnaire_answer (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '问卷答案主键',
    batch_id BIGINT NOT NULL COMMENT '选寝批次主键',
    questionnaire_version_id BIGINT NOT NULL COMMENT '问卷版本主键',
    student_id BIGINT NOT NULL COMMENT '学生主键',
    question_id BIGINT NOT NULL COMMENT '题目主键',
    answer_json JSON NOT NULL COMMENT '原始答案',
    submitted_at DATETIME(3) NULL COMMENT '提交时间',
    version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    CONSTRAINT fk_answer_batch FOREIGN KEY (batch_id) REFERENCES selection_batch(id) ON DELETE RESTRICT,
    CONSTRAINT fk_answer_questionnaire FOREIGN KEY (questionnaire_version_id) REFERENCES questionnaire_version(id) ON DELETE RESTRICT,
    CONSTRAINT fk_answer_student FOREIGN KEY (student_id) REFERENCES student(id) ON DELETE RESTRICT,
    CONSTRAINT fk_answer_question FOREIGN KEY (question_id) REFERENCES questionnaire_question(id) ON DELETE RESTRICT,
    UNIQUE KEY uk_answer_batch_student_question (batch_id, student_id, question_id),
    KEY idx_answer_student (student_id, batch_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='学生问卷原始答案';

CREATE TABLE student_feature (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '学生特征主键',
    batch_id BIGINT NOT NULL COMMENT '选寝批次主键',
    student_id BIGINT NOT NULL COMMENT '学生主键',
    algorithm_version VARCHAR(32) NOT NULL COMMENT '特征算法版本',
    feature_vector_json JSON NOT NULL COMMENT '标准化特征向量',
    explanation_tags_json JSON NOT NULL COMMENT '可公开解释标签',
    calculated_at DATETIME(3) NOT NULL COMMENT '计算时间',
    source_answer_version INT NOT NULL DEFAULT 0 COMMENT '来源答案版本',
    PRIMARY KEY (id),
    CONSTRAINT fk_feature_batch FOREIGN KEY (batch_id) REFERENCES selection_batch(id) ON DELETE CASCADE,
    CONSTRAINT fk_feature_student FOREIGN KEY (student_id) REFERENCES student(id) ON DELETE RESTRICT,
    UNIQUE KEY uk_feature_batch_student (batch_id, student_id),
    KEY idx_feature_algorithm (batch_id, algorithm_version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='学生标准化匹配特征';

CREATE TABLE selection_team (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '选寝队伍主键',
    batch_id BIGINT NOT NULL COMMENT '选寝批次主键',
    team_code VARCHAR(32) NOT NULL COMMENT '队伍编码',
    team_name VARCHAR(128) NOT NULL COMMENT '队伍名称',
    leader_student_id BIGINT NOT NULL COMMENT '队长学生主键',
    team_status VARCHAR(32) NOT NULL DEFAULT 'FORMING' COMMENT '队伍状态',
    locked_at DATETIME(3) NULL,
    version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    CONSTRAINT fk_team_batch FOREIGN KEY (batch_id) REFERENCES selection_batch(id) ON DELETE RESTRICT,
    CONSTRAINT fk_team_leader FOREIGN KEY (leader_student_id) REFERENCES student(id) ON DELETE RESTRICT,
    CONSTRAINT ck_team_status CHECK (team_status IN ('FORMING','LOCKED','SELECTING','COMPLETED','DISSOLVED')),
    UNIQUE KEY uk_team_batch_code (batch_id, team_code),
    KEY idx_team_batch_status (batch_id, team_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='选寝队伍';

CREATE TABLE selection_team_member (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '队伍成员主键',
    team_id BIGINT NOT NULL COMMENT '队伍主键',
    batch_id BIGINT NOT NULL COMMENT '冗余批次主键，用于唯一约束',
    student_id BIGINT NOT NULL COMMENT '学生主键',
    member_role VARCHAR(16) NOT NULL COMMENT '成员角色',
    member_status VARCHAR(32) NOT NULL DEFAULT 'INVITED' COMMENT '成员状态',
    active_marker TINYINT GENERATED ALWAYS AS (
        CASE WHEN member_status IN ('INVITED','JOINED','LOCKED') THEN 1 ELSE NULL END
    ) STORED COMMENT '有效队伍成员唯一标记',
    joined_at DATETIME(3) NULL,
    left_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    CONSTRAINT fk_team_member_team FOREIGN KEY (team_id) REFERENCES selection_team(id) ON DELETE CASCADE,
    CONSTRAINT fk_team_member_batch FOREIGN KEY (batch_id) REFERENCES selection_batch(id) ON DELETE RESTRICT,
    CONSTRAINT fk_team_member_student FOREIGN KEY (student_id) REFERENCES student(id) ON DELETE RESTRICT,
    CONSTRAINT ck_team_member_role CHECK (member_role IN ('LEADER','MEMBER')),
    CONSTRAINT ck_team_member_status CHECK (member_status IN ('INVITED','JOINED','LOCKED','LEFT','REMOVED','REJECTED')),
    UNIQUE KEY uk_team_member_team_student (team_id, student_id),
    UNIQUE KEY uk_active_team_member (batch_id, student_id, active_marker),
    KEY idx_team_member_status (team_id, member_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='选寝队伍成员';

CREATE TABLE team_invitation (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '队伍邀请主键',
    team_id BIGINT NOT NULL COMMENT '队伍主键',
    inviter_student_id BIGINT NOT NULL COMMENT '邀请人学生主键',
    invitee_student_id BIGINT NOT NULL COMMENT '被邀请学生主键',
    invitation_status VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT '邀请状态',
    invitation_token CHAR(36) NOT NULL COMMENT '邀请令牌',
    expires_at DATETIME(3) NOT NULL COMMENT '过期时间',
    responded_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    CONSTRAINT fk_invitation_team FOREIGN KEY (team_id) REFERENCES selection_team(id) ON DELETE CASCADE,
    CONSTRAINT fk_invitation_inviter FOREIGN KEY (inviter_student_id) REFERENCES student(id) ON DELETE RESTRICT,
    CONSTRAINT fk_invitation_invitee FOREIGN KEY (invitee_student_id) REFERENCES student(id) ON DELETE RESTRICT,
    CONSTRAINT ck_invitation_status CHECK (invitation_status IN ('PENDING','ACCEPTED','REJECTED','EXPIRED','CANCELLED')),
    UNIQUE KEY uk_invitation_token (invitation_token),
    KEY idx_invitation_invitee_status (invitee_student_id, invitation_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='队伍邀请';

CREATE TABLE bed_assignment (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '当前有效分配主键',
    batch_id BIGINT NOT NULL COMMENT '选寝批次主键',
    student_id BIGINT NOT NULL COMMENT '学生主键',
    bed_id BIGINT NOT NULL COMMENT '床位主键',
    team_id BIGINT NULL COMMENT '队伍主键',
    assignment_method VARCHAR(32) NOT NULL COMMENT '分配方式',
    assignment_status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT '分配状态',
    allocation_run_id BIGINT NULL COMMENT '随机分配执行主键；后续添加外键',
    assigned_by BIGINT NULL COMMENT '操作用户；学生自选时可为空',
    assigned_at DATETIME(3) NOT NULL COMMENT '分配时间',
    version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    CONSTRAINT fk_assignment_batch FOREIGN KEY (batch_id) REFERENCES selection_batch(id) ON DELETE RESTRICT,
    CONSTRAINT fk_assignment_student FOREIGN KEY (student_id) REFERENCES student(id) ON DELETE RESTRICT,
    CONSTRAINT fk_assignment_bed FOREIGN KEY (bed_id) REFERENCES bed(id) ON DELETE RESTRICT,
    CONSTRAINT fk_assignment_team FOREIGN KEY (team_id) REFERENCES selection_team(id) ON DELETE RESTRICT,
    CONSTRAINT fk_assignment_operator FOREIGN KEY (assigned_by) REFERENCES app_user(id) ON DELETE RESTRICT,
    CONSTRAINT ck_assignment_method CHECK (assignment_method IN ('SELF_SELECT','TEAM_SELECT','STUDENT_RANDOM','ADMIN_RANDOM','MANUAL_ADJUSTMENT')),
    CONSTRAINT ck_assignment_status CHECK (assignment_status IN ('ACTIVE')),
    UNIQUE KEY uk_assignment_batch_student (batch_id, student_id),
    UNIQUE KEY uk_assignment_batch_bed (batch_id, bed_id),
    KEY idx_assignment_batch_method (batch_id, assignment_method),
    KEY idx_assignment_team (team_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='当前有效床位分配';

CREATE TABLE assignment_history (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '分配历史主键',
    assignment_id BIGINT NULL COMMENT '当前分配主键',
    batch_id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    bed_id BIGINT NOT NULL,
    event_type VARCHAR(32) NOT NULL COMMENT '历史事件类型',
    assignment_method VARCHAR(32) NOT NULL,
    operator_user_id BIGINT NULL,
    reason VARCHAR(500) NULL,
    previous_data JSON NULL,
    current_data JSON NULL,
    occurred_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_assignment_history_assignment FOREIGN KEY (assignment_id) REFERENCES bed_assignment(id) ON DELETE SET NULL,
    CONSTRAINT fk_assignment_history_batch FOREIGN KEY (batch_id) REFERENCES selection_batch(id) ON DELETE RESTRICT,
    CONSTRAINT fk_assignment_history_student FOREIGN KEY (student_id) REFERENCES student(id) ON DELETE RESTRICT,
    CONSTRAINT fk_assignment_history_bed FOREIGN KEY (bed_id) REFERENCES bed(id) ON DELETE RESTRICT,
    CONSTRAINT fk_assignment_history_operator FOREIGN KEY (operator_user_id) REFERENCES app_user(id) ON DELETE RESTRICT,
    CONSTRAINT ck_assignment_history_event CHECK (event_type IN ('CREATED','ADJUSTED','CANCELLED','RESTORED')),
    KEY idx_assignment_history_student (batch_id, student_id, occurred_at),
    KEY idx_assignment_history_bed (batch_id, bed_id, occurred_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='床位分配历史';

CREATE TABLE allocation_run (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '随机分配执行主键',
    batch_id BIGINT NOT NULL COMMENT '选寝批次主键',
    execution_code VARCHAR(64) NOT NULL COMMENT '执行编号',
    idempotency_key VARCHAR(128) NOT NULL COMMENT '幂等键',
    run_mode VARCHAR(16) NOT NULL COMMENT 'PREVIEW/COMMIT',
    run_status VARCHAR(32) NOT NULL COMMENT '执行状态',
    algorithm_version VARCHAR(32) NOT NULL,
    rule_version VARCHAR(32) NOT NULL,
    random_seed BIGINT NOT NULL,
    student_snapshot_json JSON NOT NULL,
    bed_snapshot_json JSON NOT NULL,
    summary_json JSON NULL,
    operator_user_id BIGINT NOT NULL,
    started_at DATETIME(3) NOT NULL,
    finished_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    CONSTRAINT fk_allocation_run_batch FOREIGN KEY (batch_id) REFERENCES selection_batch(id) ON DELETE RESTRICT,
    CONSTRAINT fk_allocation_run_operator FOREIGN KEY (operator_user_id) REFERENCES app_user(id) ON DELETE RESTRICT,
    CONSTRAINT ck_allocation_run_mode CHECK (run_mode IN ('PREVIEW','COMMIT')),
    CONSTRAINT ck_allocation_run_status CHECK (run_status IN ('CREATED','RUNNING','SUCCEEDED','PARTIAL_SUCCESS','FAILED')),
    UNIQUE KEY uk_allocation_execution_code (execution_code),
    UNIQUE KEY uk_allocation_idempotency (batch_id, idempotency_key),
    KEY idx_allocation_batch_status (batch_id, run_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='统一随机分配执行';

ALTER TABLE bed_assignment
    ADD CONSTRAINT fk_assignment_allocation_run
    FOREIGN KEY (allocation_run_id) REFERENCES allocation_run(id) ON DELETE RESTRICT;

CREATE TABLE allocation_run_result (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '随机分配结果主键',
    allocation_run_id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    bed_id BIGINT NULL,
    result_status VARCHAR(32) NOT NULL,
    score DECIMAL(10,4) NULL,
    failure_code VARCHAR(64) NULL,
    explanation_json JSON NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    CONSTRAINT fk_allocation_result_run FOREIGN KEY (allocation_run_id) REFERENCES allocation_run(id) ON DELETE CASCADE,
    CONSTRAINT fk_allocation_result_student FOREIGN KEY (student_id) REFERENCES student(id) ON DELETE RESTRICT,
    CONSTRAINT fk_allocation_result_bed FOREIGN KEY (bed_id) REFERENCES bed(id) ON DELETE RESTRICT,
    CONSTRAINT ck_allocation_result_status CHECK (result_status IN ('ASSIGNED','UNASSIGNED','SKIPPED','FAILED')),
    UNIQUE KEY uk_allocation_result_student (allocation_run_id, student_id),
    KEY idx_allocation_result_status (allocation_run_id, result_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='统一随机分配明细';

CREATE TABLE audit_log (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '审计日志主键',
    request_id VARCHAR(64) NULL COMMENT '请求编号',
    operator_user_id BIGINT NULL COMMENT '操作用户',
    operator_type VARCHAR(32) NOT NULL COMMENT '操作人类型',
    action_type VARCHAR(64) NOT NULL COMMENT '动作类型',
    resource_type VARCHAR(64) NOT NULL COMMENT '资源类型',
    resource_id VARCHAR(64) NULL COMMENT '资源标识',
    result_status VARCHAR(32) NOT NULL COMMENT '操作结果',
    reason VARCHAR(500) NULL COMMENT '操作原因',
    before_data JSON NULL,
    after_data JSON NULL,
    ip_address VARCHAR(64) NULL,
    occurred_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_audit_operator FOREIGN KEY (operator_user_id) REFERENCES app_user(id) ON DELETE SET NULL,
    CONSTRAINT ck_audit_operator_type CHECK (operator_type IN ('STUDENT','ADMIN','SYSTEM')),
    CONSTRAINT ck_audit_result CHECK (result_status IN ('SUCCESS','FAILED','REJECTED')),
    KEY idx_audit_resource (resource_type, resource_id, occurred_at),
    KEY idx_audit_operator (operator_user_id, occurred_at),
    KEY idx_audit_request (request_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='操作审计日志';
