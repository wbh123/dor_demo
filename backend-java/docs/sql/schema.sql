-- ============================================================
-- 武汉科技大学学生宿舍智能选择系统
-- 数据库固化基线 SQL
--
-- 生成方式：python scripts/db/build_frozen_baseline.py
-- 开发期间 Flyway 迁移是唯一事实来源；本文件是便于部署和审阅的合并快照。
-- 第一阶段冻结后重新运行脚本并将输出纳入验收记录。
-- 不包含 src/test/resources 下的开发测试数据。
-- ============================================================

-- >>> BEGIN V1__create_phase1_schema.sql
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
-- <<< END V1__create_phase1_schema.sql

-- >>> BEGIN V2__enforce_fixed_room_gender.sql
-- 每间宿舍必须具有固定的男寝或女寝属性。
-- 宿舍楼可以保留 ANY，用于未来同一楼栋内按房间分别配置性别；
-- 但房间本身不得为 ANY，学生与房间性别必须一致。

ALTER TABLE room
    DROP CHECK ck_room_gender,
    ADD CONSTRAINT ck_room_gender
        CHECK (gender_restriction IN ('M','F'));
-- <<< END V2__enforce_fixed_room_gender.sql

-- >>> BEGIN V3__normalize_major_and_minimize_student.sql
-- ============================================================
-- 第一阶段学生与专业结构优化
--
-- 目标：
-- 1. 学生业务信息只保留学号、姓名、性别、专业外键；
-- 2. 专业信息独立维护，避免在学生表重复保存专业名称；
-- 3. 账号关联迁移到 app_user.student_id；
-- 4. 删除班级、年级、校区、组织、全局住宿资格等冗余字段；
-- 5. 住宿资格继续由 batch_student_eligibility 按批次维护；
-- 6. 数据导入任务支持专业目录导入。
-- ============================================================

CREATE TABLE major (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '专业主键',
    major_code VARCHAR(32) NOT NULL COMMENT '专业编号',
    major_name VARCHAR(128) NOT NULL COMMENT '专业名称',
    enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    CONSTRAINT ck_major_enabled CHECK (enabled IN (0,1)),
    UNIQUE KEY uk_major_code (major_code),
    UNIQUE KEY uk_major_name (major_name),
    KEY idx_major_enabled (enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='专业基础信息';

-- 兼容已经执行过V1、V2且存在学生数据的开发库。
-- 旧表只有专业名称，没有专业编号，因此使用稳定哈希生成迁移编号；
-- 后续管理员导入正式专业目录时可以更新为学校真实专业编号。
INSERT INTO major (major_code, major_name, enabled)
SELECT
    CONCAT('LEGACY-', UPPER(SUBSTRING(SHA2(major_name, 256), 1, 16))),
    major_name,
    1
FROM student
GROUP BY major_name;

ALTER TABLE student
    ADD COLUMN major_id BIGINT NULL COMMENT '专业主键' AFTER gender;

UPDATE student s
JOIN major m ON m.major_name = s.major_name
SET s.major_id = m.id;

ALTER TABLE app_user
    ADD COLUMN student_id BIGINT NULL COMMENT '学生主键；管理员为空' AFTER id;

UPDATE app_user u
JOIN student s
  ON u.user_type = 'STUDENT'
 AND u.username = s.student_number
SET u.student_id = s.id;

ALTER TABLE student
    DROP FOREIGN KEY fk_student_user,
    DROP FOREIGN KEY fk_student_organization,
    DROP FOREIGN KEY fk_student_campus,
    DROP INDEX uk_student_user,
    DROP INDEX idx_student_org,
    DROP INDEX idx_student_campus_gender,
    DROP INDEX idx_student_eligibility,
    DROP CHECK ck_student_grade,
    DROP CHECK ck_student_eligibility,
    DROP CHECK ck_student_profile_status,
    DROP CHECK ck_student_data_source;

ALTER TABLE student
    MODIFY COLUMN major_id BIGINT NOT NULL COMMENT '专业主键',
    ADD CONSTRAINT fk_student_major
        FOREIGN KEY (major_id) REFERENCES major(id) ON DELETE RESTRICT,
    ADD KEY idx_student_major_gender (major_id, gender),
    DROP COLUMN user_id,
    DROP COLUMN organization_id,
    DROP COLUMN campus_id,
    DROP COLUMN grade_year,
    DROP COLUMN major_name,
    DROP COLUMN class_name,
    DROP COLUMN housing_eligibility,
    DROP COLUMN profile_status,
    DROP COLUMN data_source,
    DROP COLUMN version;

ALTER TABLE app_user
    ADD CONSTRAINT fk_app_user_student
        FOREIGN KEY (student_id) REFERENCES student(id) ON DELETE RESTRICT,
    ADD UNIQUE KEY uk_app_user_student (student_id);

ALTER TABLE import_job
    DROP CHECK ck_import_type,
    ADD CONSTRAINT ck_import_type
        CHECK (import_type IN ('MAJOR','STUDENT','DORMITORY'));

DROP TABLE organization;
-- <<< END V3__normalize_major_and_minimize_student.sql

-- >>> BEGIN V4__refine_questionnaire_and_active_batch_rules.sql
-- ============================================================
-- V4：优化问卷吸烟偏好，并约束学生只能参加一个活动批次
-- ============================================================

-- 1. “是否接受室友吸烟”由布尔题调整为三态单选题。
UPDATE questionnaire_question
SET question_text = '是否接受室友吸烟？',
    question_type = 'SINGLE_CHOICE',
    updated_at = CURRENT_TIMESTAMP(3)
WHERE question_code = 'SMOKING_ACCEPTANCE';

DELETE qo
FROM questionnaire_option qo
JOIN questionnaire_question qq ON qq.id = qo.question_id
WHERE qq.question_code = 'SMOKING_ACCEPTANCE';

INSERT INTO questionnaire_option
(question_id, option_code, option_text, feature_value, sort_order, enabled)
SELECT id, 'ACCEPT', '接受', 1.0000, 1, 1
FROM questionnaire_question
WHERE question_code = 'SMOKING_ACCEPTANCE'
UNION ALL
SELECT id, 'REJECT', '不接受', 0.0000, 2, 1
FROM questionnaire_question
WHERE question_code = 'SMOKING_ACCEPTANCE'
UNION ALL
SELECT id, 'ANY', '均可', 0.5000, 3, 1
FROM questionnaire_question
WHERE question_code = 'SMOKING_ACCEPTANCE';

-- 将历史布尔答案转换为三态字符串。
UPDATE questionnaire_answer qa
JOIN questionnaire_question qq ON qq.id = qa.question_id
SET qa.answer_json = CASE
    WHEN JSON_UNQUOTE(qa.answer_json) = 'true' THEN JSON_QUOTE('ACCEPT')
    WHEN JSON_UNQUOTE(qa.answer_json) = 'false' THEN JSON_QUOTE('REJECT')
    ELSE JSON_QUOTE('ANY')
END,
qa.version = qa.version + 1
WHERE qq.question_code = 'SMOKING_ACCEPTANCE';

UPDATE student_feature
SET feature_vector_json = JSON_SET(
    feature_vector_json,
    '$.smokingAcceptance',
    CASE JSON_UNQUOTE(JSON_EXTRACT(feature_vector_json, '$.smokingAcceptance'))
        WHEN 'true' THEN 'ACCEPT'
        WHEN 'false' THEN 'REJECT'
        WHEN 'ACCEPT' THEN 'ACCEPT'
        WHEN 'REJECT' THEN 'REJECT'
        ELSE 'ANY'
    END
),
calculated_at = CURRENT_TIMESTAMP(3)
WHERE JSON_CONTAINS_PATH(feature_vector_json, 'one', '$.smokingAcceptance');

-- 2. 以最小锁表表达“同一学生同一时刻只能属于一个活动批次”。
-- 活动批次包括：已发布、选寝中、暂停。
-- 锁表由Spring事务服务维护，不依赖需要数据库高权限的触发器。
CREATE TABLE active_batch_student_lock (
    student_id BIGINT NOT NULL COMMENT '学生主键，同一学生只能出现一次',
    batch_id BIGINT NOT NULL COMMENT '当前活动批次主键',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (student_id),
    UNIQUE KEY uk_active_batch_student_batch (batch_id, student_id),
    CONSTRAINT fk_active_batch_lock_student
        FOREIGN KEY (student_id) REFERENCES student(id) ON DELETE CASCADE,
    CONSTRAINT fk_active_batch_lock_batch
        FOREIGN KEY (batch_id) REFERENCES selection_batch(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='活动选寝批次学生唯一锁';

-- 若升级时已经存在活动批次，先建立唯一锁；存在冲突时迁移会因主键冲突而失败。
INSERT INTO active_batch_student_lock (student_id, batch_id)
SELECT e.student_id, e.batch_id
FROM batch_student_eligibility e
JOIN selection_batch sb ON sb.id = e.batch_id
WHERE e.eligibility_status = 'ELIGIBLE'
  AND sb.batch_status IN ('PUBLISHED', 'OPEN', 'PAUSED');
-- <<< END V4__refine_questionnaire_and_active_batch_rules.sql

-- >>> BEGIN V5__add_room_bed_layout.sql
CREATE TABLE room_bed_layout (
    bed_id BIGINT NOT NULL COMMENT '床位主键，同时作为布局主键',
    layout_x DECIMAL(6,3) NOT NULL COMMENT '房间局部X坐标',
    layout_z DECIMAL(6,3) NOT NULL COMMENT '房间局部Z坐标',
    rotation_degrees SMALLINT NOT NULL DEFAULT 90 COMMENT '平面旋转角度',
    updated_by BIGINT NOT NULL COMMENT '最后修改管理员',
    version INT NOT NULL DEFAULT 0 COMMENT '布局记录版本',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (bed_id),
    CONSTRAINT fk_room_bed_layout_bed
        FOREIGN KEY (bed_id) REFERENCES bed(id) ON DELETE CASCADE,
    CONSTRAINT fk_room_bed_layout_operator
        FOREIGN KEY (updated_by) REFERENCES app_user(id) ON DELETE RESTRICT,
    CONSTRAINT ck_room_bed_layout_x CHECK (layout_x BETWEEN -5.200 AND 5.200),
    CONSTRAINT ck_room_bed_layout_z CHECK (layout_z BETWEEN -3.500 AND 3.500),
    CONSTRAINT ck_room_bed_layout_rotation CHECK (rotation_degrees IN (0, 90, 180, 270)),
    KEY idx_room_bed_layout_operator (updated_by)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='逐床位房间可视化布局';
-- <<< END V5__add_room_bed_layout.sql

-- >>> BEGIN V6__version_matching_weight_schemes.sql
ALTER TABLE matching_weight_scheme
    ADD COLUMN revision INT NOT NULL DEFAULT 1 COMMENT '方案修订号' AFTER scheme_name,
    ADD COLUMN created_by BIGINT NULL COMMENT '创建管理员' AFTER version,
    ADD COLUMN change_reason VARCHAR(500) NULL COMMENT '创建或修改原因' AFTER created_by,
    ADD COLUMN published_at DATETIME(3) NULL COMMENT '启用时间' AFTER change_reason;

ALTER TABLE matching_weight_scheme
    DROP INDEX uk_weight_scheme_code,
    ADD CONSTRAINT fk_matching_weight_scheme_creator
        FOREIGN KEY (created_by) REFERENCES app_user(id) ON DELETE RESTRICT,
    ADD CONSTRAINT ck_matching_weight_scheme_revision CHECK (revision >= 1),
    ADD UNIQUE KEY uk_weight_scheme_revision (scheme_code, revision),
    ADD KEY idx_matching_weight_scheme_enabled (enabled, published_at);

UPDATE matching_weight_scheme
SET revision = 1,
    weights_json = JSON_OBJECT(
        'sleepTimeMinutes', COALESCE(CAST(JSON_UNQUOTE(JSON_EXTRACT(weights_json, '$.sleepTimeMinutes')) AS DECIMAL(10,4)), 1.2),
        'wakeTimeMinutes', COALESCE(CAST(JSON_UNQUOTE(JSON_EXTRACT(weights_json, '$.wakeTimeMinutes')) AS DECIMAL(10,4)), 1.0),
        'sleepSensitivity', COALESCE(CAST(JSON_UNQUOTE(JSON_EXTRACT(weights_json, '$.sleepSensitivity')) AS DECIMAL(10,4)), 1.2),
        'noiseTolerance', COALESCE(CAST(JSON_UNQUOTE(JSON_EXTRACT(weights_json, '$.noiseTolerance')) AS DECIMAL(10,4)), 1.2),
        'cleaningFrequency', COALESCE(CAST(JSON_UNQUOTE(JSON_EXTRACT(weights_json, '$.cleaningFrequency')) AS DECIMAL(10,4)), 1.0),
        'tidinessRequirement', COALESCE(CAST(JSON_UNQUOTE(JSON_EXTRACT(weights_json, '$.tidinessRequirement')) AS DECIMAL(10,4)), 1.0),
        'airConditionerTemperature', COALESCE(CAST(JSON_UNQUOTE(JSON_EXTRACT(weights_json, '$.airConditionerTemperature')) AS DECIMAL(10,4)), 0.8),
        'studyFrequency', COALESCE(CAST(JSON_UNQUOTE(JSON_EXTRACT(weights_json, '$.studyFrequency')) AS DECIMAL(10,4)), 0.8),
        'gamingVoiceFrequency', COALESCE(CAST(JSON_UNQUOTE(JSON_EXTRACT(weights_json, '$.gamingVoiceFrequency')) AS DECIMAL(10,4)), 1.1),
        'socialActivity', COALESCE(CAST(JSON_UNQUOTE(JSON_EXTRACT(weights_json, '$.socialActivity')) AS DECIMAL(10,4)), 0.6)
    ),
    conflict_rules_json = JSON_OBJECT(
        'smokingConflictPenalty', COALESCE(
            CAST(JSON_UNQUOTE(JSON_EXTRACT(conflict_rules_json, '$.smokingConflictPenalty')) AS DECIMAL(10,4)),
            CAST(JSON_UNQUOTE(JSON_EXTRACT(conflict_rules_json, '$.smokingAcceptance.penalty')) AS DECIMAL(10,4)),
            25
        ),
        'sleepTimeWarningMinutes', COALESCE(
            CAST(JSON_UNQUOTE(JSON_EXTRACT(conflict_rules_json, '$.sleepTimeWarningMinutes')) AS DECIMAL(10,4)),
            60
        ),
        'cleaningWarningDifference', COALESCE(
            CAST(JSON_UNQUOTE(JSON_EXTRACT(conflict_rules_json, '$.cleaningWarningDifference')) AS DECIMAL(10,4)),
            1
        ),
        'gamingVoiceWarningDifference', COALESCE(
            CAST(JSON_UNQUOTE(JSON_EXTRACT(conflict_rules_json, '$.gamingVoiceWarningDifference')) AS DECIMAL(10,4)),
            1
        )
    ),
    change_reason = COALESCE(change_reason, '历史方案迁移'),
    published_at = CASE
        WHEN enabled = 1 THEN COALESCE(published_at, updated_at)
        ELSE published_at
    END;
-- <<< END V6__version_matching_weight_schemes.sql

-- >>> BEGIN V7__add_student_welcome_settings.sql
-- ============================================================
-- V7：学生首次登录欢迎确认与系统级欢迎语配置
-- ============================================================

ALTER TABLE app_user
    ADD COLUMN welcome_acknowledged_at DATETIME(3) NULL
        COMMENT '学生首次欢迎浮窗确认时间' AFTER last_login_at;

CREATE TABLE system_setting (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '系统设置主键',
    setting_key VARCHAR(64) NOT NULL COMMENT '设置键',
    setting_value VARCHAR(1000) NOT NULL COMMENT '设置值',
    version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    updated_by BIGINT NULL COMMENT '最后修改管理员',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    CONSTRAINT fk_system_setting_updated_by
        FOREIGN KEY (updated_by) REFERENCES app_user(id) ON DELETE RESTRICT,
    CONSTRAINT ck_system_setting_value_not_blank
        CHECK (CHAR_LENGTH(TRIM(setting_value)) BETWEEN 1 AND 1000),
    UNIQUE KEY uk_system_setting_key (setting_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='系统级可配置文本与参数';

INSERT INTO system_setting
(setting_key, setting_value, version, updated_by)
VALUES
('STUDENT_WELCOME_MESSAGE',
 '欢迎加入武汉科技大学宿舍智能选择系统。请先完善个人偏好，再根据楼层、剩余铺位和室友匹配情况选择合适的宿舍与床位。',
 0, NULL);
-- <<< END V7__add_student_welcome_settings.sql

-- >>> BEGIN V8__expand_personal_preferences.sql
-- ============================================================
-- V8：将生活习惯问卷统一为个人偏好，并增加高影响室友匹配维度
-- ============================================================

UPDATE questionnaire_version
SET questionnaire_name = REPLACE(questionnaire_name, '生活习惯问卷', '个人偏好'),
    description = REPLACE(description, '问卷', '个人偏好'),
    updated_at = CURRENT_TIMESTAMP(3)
WHERE questionnaire_name LIKE '%生活习惯问卷%'
   OR description LIKE '%问卷%';

-- 原空调温度题明确为夏季制冷温度，保留题目主键以兼容历史答案。
UPDATE questionnaire_question q
LEFT JOIN questionnaire_question existing
  ON existing.questionnaire_version_id = q.questionnaire_version_id
 AND existing.question_code = 'SUMMER_AC_TEMPERATURE'
SET q.question_code = 'SUMMER_AC_TEMPERATURE',
    q.question_text = '夏季使用空调制冷时，你偏好的温度是多少？',
    q.feature_key = 'summerAirConditionerTemperature',
    q.sort_order = 9,
    q.updated_at = CURRENT_TIMESTAMP(3)
WHERE q.question_code = 'AC_TEMPERATURE'
  AND existing.id IS NULL;

-- 为新增题目腾出连续排序空间。
UPDATE questionnaire_question
SET sort_order = sort_order + 6,
    updated_at = CURRENT_TIMESTAMP(3)
WHERE sort_order >= 9
  AND question_code <> 'SUMMER_AC_TEMPERATURE';

INSERT INTO questionnaire_question
(questionnaire_version_id, question_code, question_text, question_type,
 feature_key, required_flag, sort_order, enabled)
SELECT qv.id, definition.question_code, definition.question_text, definition.question_type,
       definition.feature_key, definition.required_flag, definition.sort_order, 1
FROM questionnaire_version qv
JOIN (
    SELECT 'SUMMER_AC_OVERNIGHT' AS question_code,
           '夏季是否接受宿舍整夜开启空调制冷？' AS question_text,
           'SINGLE_CHOICE' AS question_type,
           'summerOvernightAirConditioner' AS feature_key,
           1 AS required_flag, 8 AS sort_order
    UNION ALL
    SELECT 'WINTER_HEATING_ACCEPTANCE',
           '冬季是否接受宿舍开启空调制热？',
           'SINGLE_CHOICE', 'winterHeatingAcceptance', 1, 10
    UNION ALL
    SELECT 'WINTER_HEATING_TEMPERATURE',
           '冬季开启空调制热时，你偏好的温度是多少？',
           'INTEGER', 'winterHeatingTemperature', 0, 11
    UNION ALL
    SELECT 'AFTER_LIGHTS_ACTIVITY',
           '室友休息或熄灯后，你通常会保持怎样的活动状态？',
           'SINGLE_CHOICE', 'afterLightsActivity', 1, 12
    UNION ALL
    SELECT 'ALARM_SNOOZE',
           '早晨闹钟通常会响几次？',
           'SINGLE_CHOICE', 'alarmSnooze', 1, 13
    UNION ALL
    SELECT 'STRONG_FOOD_ODOR_ACCEPTANCE',
           '是否接受室友在宿舍食用气味较重的食物？',
           'SINGLE_CHOICE', 'strongFoodOdorAcceptance', 1, 14
) definition
WHERE qv.version_status IN ('DRAFT', 'PUBLISHED')
  AND NOT EXISTS (
      SELECT 1 FROM questionnaire_question q
      WHERE q.questionnaire_version_id = qv.id
        AND q.question_code = definition.question_code
  );

INSERT INTO questionnaire_option
(question_id, option_code, option_text, feature_value, sort_order, enabled)
SELECT q.id, definition.option_code, definition.option_text,
       definition.feature_value, definition.sort_order, 1
FROM questionnaire_question q
JOIN (
    SELECT 'SUMMER_AC_OVERNIGHT' AS question_code, 'REJECT' AS option_code,
           '不接受整夜开启' AS option_text, 1.0000 AS feature_value, 1 AS sort_order
    UNION ALL SELECT 'SUMMER_AC_OVERNIGHT', 'TIMER', '可以定时开启', 2.0000, 2
    UNION ALL SELECT 'SUMMER_AC_OVERNIGHT', 'ACCEPT', '接受整夜开启', 3.0000, 3
    UNION ALL SELECT 'SUMMER_AC_OVERNIGHT', 'ANY', '不在意', 2.0000, 4

    UNION ALL SELECT 'WINTER_HEATING_ACCEPTANCE', 'REJECT', '不接受制热', 1.0000, 1
    UNION ALL SELECT 'WINTER_HEATING_ACCEPTANCE', 'ACCEPT', '接受制热', 3.0000, 2
    UNION ALL SELECT 'WINTER_HEATING_ACCEPTANCE', 'ANY', '不在意', 2.0000, 3

    UNION ALL SELECT 'AFTER_LIGHTS_ACTIVITY', 'DARK_SILENT', '保持黑暗和安静', 1.0000, 1
    UNION ALL SELECT 'AFTER_LIGHTS_ACTIVITY', 'DESK_LAMP_HEADPHONES', '使用台灯和耳机', 2.0000, 2
    UNION ALL SELECT 'AFTER_LIGHTS_ACTIVITY', 'NORMAL_ACTIVITY', '仍会正常活动', 3.0000, 3
    UNION ALL SELECT 'AFTER_LIGHTS_ACTIVITY', 'ANY', '不在意', 2.0000, 4

    UNION ALL SELECT 'ALARM_SNOOZE', 'ONCE', '通常一次起床', 1.0000, 1
    UNION ALL SELECT 'ALARM_SNOOZE', 'SOMETIMES', '偶尔重复一次', 2.0000, 2
    UNION ALL SELECT 'ALARM_SNOOZE', 'REPEATED', '经常多次响铃', 3.0000, 3

    UNION ALL SELECT 'STRONG_FOOD_ODOR_ACCEPTANCE', 'REJECT', '不接受', 1.0000, 1
    UNION ALL SELECT 'STRONG_FOOD_ODOR_ACCEPTANCE', 'OCCASIONAL', '偶尔可以', 2.0000, 2
    UNION ALL SELECT 'STRONG_FOOD_ODOR_ACCEPTANCE', 'ACCEPT', '可以接受', 3.0000, 3
    UNION ALL SELECT 'STRONG_FOOD_ODOR_ACCEPTANCE', 'ANY', '不在意', 2.0000, 4
) definition ON definition.question_code = q.question_code
WHERE NOT EXISTS (
    SELECT 1 FROM questionnaire_option existing
    WHERE existing.question_id = q.id
      AND existing.option_code = definition.option_code
);
-- <<< END V8__expand_personal_preferences.sql

-- >>> BEGIN V9__add_student_contact_and_notifications.sql
-- ============================================================
-- V9：国际学生资料、多语言欢迎语与学生系统通知
-- ============================================================

ALTER TABLE student
    ADD COLUMN nationality_code CHAR(2) NOT NULL DEFAULT 'CN'
        COMMENT 'ISO 3166-1 alpha-2国籍代码' AFTER major_id,
    ADD COLUMN phone_number VARCHAR(32) NULL
        COMMENT '学生本人可维护的手机号码' AFTER nationality_code,
    ADD CONSTRAINT ck_student_nationality_code
        CHECK (nationality_code REGEXP '^[A-Z]{2}$'),
    ADD CONSTRAINT ck_student_phone_number
        CHECK (phone_number IS NULL OR phone_number REGEXP '^\\+?[0-9][0-9 -]{5,30}$'),
    ADD KEY idx_student_nationality (nationality_code);

CREATE TABLE student_notification (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '学生系统通知主键',
    student_id BIGINT NOT NULL COMMENT '接收学生主键',
    notification_type VARCHAR(64) NOT NULL COMMENT '通知类型',
    title_key VARCHAR(128) NOT NULL COMMENT '前端国际化标题键',
    message_key VARCHAR(128) NOT NULL COMMENT '前端国际化正文键',
    parameters_json JSON NOT NULL COMMENT '消息插值参数',
    read_at DATETIME(3) NULL COMMENT '阅读时间',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    CONSTRAINT fk_student_notification_student
        FOREIGN KEY (student_id) REFERENCES student(id) ON DELETE CASCADE,
    CONSTRAINT ck_student_notification_type
        CHECK (notification_type IN ('TEAM_MEMBER_REMOVED','TEAM_DISSOLVED','TEAM_INVITATION_CANCELLED')),
    KEY idx_student_notification_unread (student_id, read_at, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='学生系统通知';

-- 兼容V7中的纯文本欢迎语，将其升级为中英双语JSON。
UPDATE system_setting
SET setting_value = JSON_OBJECT(
        'zh-CN', setting_value,
        'en-US', 'Welcome to the Wuhan University of Science and Technology dormitory selection system. Complete your personal preferences first, then choose a suitable room and bed.'
    ),
    version = version + 1
WHERE setting_key = 'STUDENT_WELCOME_MESSAGE'
  AND JSON_VALID(setting_value) = 0;
-- <<< END V9__add_student_contact_and_notifications.sql

-- >>> BEGIN V10__add_batch_rule_templates.sql
-- V10: add batch rule templates

CREATE TABLE batch_rule_template (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '批次规则模板修订主键',
    rule_code VARCHAR(32) NOT NULL COMMENT '规则模板编码',
    rule_name VARCHAR(128) NOT NULL COMMENT '规则模板名称',
    revision INT NOT NULL COMMENT '不可变修订号',
    hold_duration_seconds INT NOT NULL COMMENT '临时占用秒数',
    hold_renewal_limit SMALLINT NOT NULL COMMENT '最大续期次数',
    allow_team TINYINT NOT NULL COMMENT '是否允许组队',
    team_min_size SMALLINT NOT NULL COMMENT '队伍最小人数',
    team_max_size SMALLINT NOT NULL COMMENT '队伍最大人数',
    allow_student_random TINYINT NOT NULL COMMENT '是否允许学生随机推荐',
    unselected_strategy VARCHAR(32) NOT NULL COMMENT '未选学生处理策略',
    rule_version VARCHAR(32) NOT NULL COMMENT '规则执行版本',
    enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否可供新批次选择',
    is_default TINYINT NOT NULL DEFAULT 0 COMMENT '是否为默认修订',
    default_marker TINYINT GENERATED ALWAYS AS
        (CASE WHEN is_default = 1 THEN 1 ELSE NULL END) STORED,
    created_by BIGINT NULL COMMENT '创建管理员；系统迁移可为空',
    change_reason VARCHAR(500) NOT NULL COMMENT '创建或修订原因',
    version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    CONSTRAINT fk_batch_rule_template_creator
        FOREIGN KEY (created_by) REFERENCES app_user(id) ON DELETE SET NULL,
    CONSTRAINT ck_batch_rule_template_revision CHECK (revision >= 1),
    CONSTRAINT ck_batch_rule_template_hold CHECK
        (hold_duration_seconds BETWEEN 30 AND 3600),
    CONSTRAINT ck_batch_rule_template_renewal CHECK
        (hold_renewal_limit BETWEEN 0 AND 20),
    CONSTRAINT ck_batch_rule_template_allow_team CHECK (allow_team IN (0,1)),
    CONSTRAINT ck_batch_rule_template_team_size CHECK (
        (allow_team = 0 AND team_min_size = 1 AND team_max_size = 1)
        OR
        (allow_team = 1
         AND team_min_size BETWEEN 2 AND team_max_size
         AND (
             (enabled = 1 AND team_max_size <= 5)
             OR (enabled = 0 AND team_max_size <= 20)
         ))
    ),
    CONSTRAINT ck_batch_rule_template_allow_random CHECK
        (allow_student_random IN (0,1)),
    CONSTRAINT ck_batch_rule_template_strategy CHECK
        (unselected_strategy IN ('NONE','ADMIN_ALLOCATION')),
    CONSTRAINT ck_batch_rule_template_enabled CHECK (enabled IN (0,1)),
    CONSTRAINT ck_batch_rule_template_default CHECK (is_default IN (0,1)),
    UNIQUE KEY uk_batch_rule_template_revision (rule_code, revision),
    UNIQUE KEY uk_batch_rule_template_default (default_marker),
    KEY idx_batch_rule_template_available (enabled, is_default, rule_code, revision)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='批次规则模板不可变修订';

INSERT INTO batch_rule_template
(rule_code, rule_name, revision,
 hold_duration_seconds, hold_renewal_limit,
 allow_team, team_min_size, team_max_size,
 allow_student_random, unselected_strategy, rule_version,
 enabled, is_default, created_by, change_reason)
VALUES
('SYSTEM_DEFAULT', '系统默认选寝规则', 1,
 300, 1, 1, 2, 5, 1, 'ADMIN_ALLOCATION', 'phase2-rule-template-v1',
 1, 1, NULL, 'Flyway V10创建系统默认规则模板');

INSERT INTO batch_rule_template
(rule_code, rule_name, revision,
 hold_duration_seconds, hold_renewal_limit,
 allow_team, team_min_size, team_max_size,
 allow_student_random, unselected_strategy, rule_version,
 enabled, is_default, created_by, change_reason)
SELECT CONCAT('MIGRATED_', LPAD(ranked.row_number_value, 4, '0')),
       CONCAT('历史批次规则 ', LPAD(ranked.row_number_value, 4, '0')),
       1,
       ranked.hold_duration_seconds,
       ranked.hold_renewal_limit,
       ranked.allow_team,
       ranked.team_min_size,
       ranked.team_max_size,
       ranked.allow_student_random,
       ranked.unselected_strategy,
       ranked.rule_version,
       0,
       0,
       NULL,
       'Flyway V10从历史批次规则快照迁移'
FROM (
    SELECT distinct_rules.*,
           ROW_NUMBER() OVER (
               ORDER BY hold_duration_seconds, hold_renewal_limit,
                        allow_team, team_min_size, team_max_size,
                        allow_student_random, unselected_strategy, rule_version
           ) AS row_number_value
    FROM (
        SELECT DISTINCT
               hold_duration_seconds,
               hold_renewal_limit,
               allow_team,
               team_min_size,
               team_max_size,
               allow_student_random,
               unselected_strategy,
               rule_version
        FROM selection_batch
        WHERE NOT (
            hold_duration_seconds = 300
            AND hold_renewal_limit = 1
            AND allow_team = 1
            AND team_min_size = 2
            AND team_max_size = 5
            AND allow_student_random = 1
            AND unselected_strategy = 'ADMIN_ALLOCATION'
            AND rule_version = 'phase2-rule-template-v1'
        )
    ) distinct_rules
) ranked;

ALTER TABLE selection_batch
    ADD COLUMN rule_template_id BIGINT NULL
        COMMENT '批次规则模板修订主键' AFTER matching_weight_scheme_id;

UPDATE selection_batch sb
JOIN batch_rule_template template
  ON template.hold_duration_seconds = sb.hold_duration_seconds
 AND template.hold_renewal_limit = sb.hold_renewal_limit
 AND template.allow_team = sb.allow_team
 AND template.team_min_size = sb.team_min_size
 AND template.team_max_size = sb.team_max_size
 AND template.allow_student_random = sb.allow_student_random
 AND template.unselected_strategy = sb.unselected_strategy
 AND template.rule_version = sb.rule_version
SET sb.rule_template_id = template.id
WHERE sb.rule_template_id IS NULL;

ALTER TABLE selection_batch
    ADD CONSTRAINT fk_selection_batch_rule_template
        FOREIGN KEY (rule_template_id)
        REFERENCES batch_rule_template(id)
        ON DELETE RESTRICT,
    ADD KEY idx_selection_batch_rule_template (rule_template_id);
-- <<< END V10__add_batch_rule_templates.sql

-- >>> BEGIN V11__harden_welcome_message_json.sql
-- V11：规范化V9之前可能遗留的欢迎语JSON标量或不完整对象。
-- V1至V10均保持不可变，本迁移只修复已有数据。

UPDATE system_setting
SET setting_value = JSON_OBJECT(
        'zh-CN',
        CASE
            WHEN JSON_TYPE(setting_value) = 'OBJECT'
                 AND COALESCE(JSON_TYPE(JSON_EXTRACT(setting_value, '$."zh-CN"')), '') = 'STRING'
                 AND LENGTH(TRIM(JSON_UNQUOTE(JSON_EXTRACT(setting_value, '$."zh-CN"')))) > 0
                THEN LEFT(TRIM(JSON_UNQUOTE(JSON_EXTRACT(setting_value, '$."zh-CN"'))), 700)
            WHEN JSON_TYPE(setting_value) = 'STRING'
                 AND LENGTH(TRIM(JSON_UNQUOTE(setting_value))) > 0
                THEN LEFT(TRIM(JSON_UNQUOTE(setting_value)), 700)
            ELSE '欢迎使用武汉科技大学学生宿舍智能选择系统。请先完成个人偏好，再选择合适的宿舍与床位。'
        END,
        'en-US',
        CASE
            WHEN JSON_TYPE(setting_value) = 'OBJECT'
                 AND COALESCE(JSON_TYPE(JSON_EXTRACT(setting_value, '$."en-US"')), '') = 'STRING'
                 AND LENGTH(TRIM(JSON_UNQUOTE(JSON_EXTRACT(setting_value, '$."en-US"')))) > 0
                THEN LEFT(TRIM(JSON_UNQUOTE(JSON_EXTRACT(setting_value, '$."en-US"'))), 220)
            ELSE 'Welcome to the Wuhan University of Science and Technology dormitory selection system. Complete your personal preferences first, then choose a suitable room and bed.'
        END
    ),
    version = version + 1
WHERE setting_key = 'STUDENT_WELCOME_MESSAGE'
  AND (
      JSON_TYPE(setting_value) <> 'OBJECT'
      OR COALESCE(JSON_TYPE(JSON_EXTRACT(setting_value, '$."zh-CN"')), '') <> 'STRING'
      OR COALESCE(LENGTH(TRIM(JSON_UNQUOTE(JSON_EXTRACT(setting_value, '$."zh-CN"')))), 0) = 0
      OR COALESCE(JSON_TYPE(JSON_EXTRACT(setting_value, '$."en-US"')), '') <> 'STRING'
      OR COALESCE(LENGTH(TRIM(JSON_UNQUOTE(JSON_EXTRACT(setting_value, '$."en-US"')))), 0) = 0
  );
-- <<< END V11__harden_welcome_message_json.sql
