-- V12: single-client subscription, feature entitlement and quota infrastructure

ALTER TABLE app_user
    DROP CHECK ck_app_user_type,
    ADD COLUMN password_change_required TINYINT NOT NULL DEFAULT 0
        COMMENT '是否必须修改初始或重置后的密码' AFTER welcome_acknowledged_at,
    ADD COLUMN system_admin_marker TINYINT GENERATED ALWAYS AS
        (CASE WHEN user_type = 'SYSTEM_ADMIN' THEN 1 ELSE NULL END) STORED,
    ADD CONSTRAINT ck_app_user_type
        CHECK (user_type IN ('STUDENT','ADMIN','SYSTEM_ADMIN')),
    ADD CONSTRAINT ck_app_user_password_change_required
        CHECK (password_change_required IN (0,1)),
    ADD CONSTRAINT ck_system_admin_not_student
        CHECK (user_type <> 'SYSTEM_ADMIN' OR student_id IS NULL),
    ADD UNIQUE KEY uk_single_system_admin (system_admin_marker);

CREATE TABLE feature_catalog (
    feature_code VARCHAR(96) NOT NULL,
    feature_name VARCHAR(160) NOT NULL,
    phase VARCHAR(16) NOT NULL,
    scope VARCHAR(16) NOT NULL,
    granularity VARCHAR(16) NOT NULL,
    action_type VARCHAR(16) NOT NULL,
    risk_level VARCHAR(16) NOT NULL,
    enabled_in_program TINYINT NOT NULL DEFAULT 1,
    sort_order INT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (feature_code),
    CONSTRAINT ck_feature_phase CHECK (phase IN ('PHASE1','PHASE2','PHASE3')),
    CONSTRAINT ck_feature_scope CHECK (scope IN ('ADMIN','STUDENT','SHARED')),
    CONSTRAINT ck_feature_granularity CHECK (granularity IN ('MODULE','OPERATION')),
    CONSTRAINT ck_feature_action CHECK (action_type IN ('READ','CREATE','UPDATE','EXECUTE','EXPORT','CONFIGURE')),
    CONSTRAINT ck_feature_risk CHECK (risk_level IN ('LOW','MEDIUM','HIGH')),
    CONSTRAINT ck_feature_enabled CHECK (enabled_in_program IN (0,1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='程序固化功能权限目录';

CREATE TABLE quota_catalog (
    quota_code VARCHAR(96) NOT NULL,
    quota_name VARCHAR(160) NOT NULL,
    unit_name VARCHAR(32) NOT NULL,
    enabled_in_program TINYINT NOT NULL DEFAULT 1,
    sort_order INT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (quota_code),
    CONSTRAINT ck_quota_enabled CHECK (enabled_in_program IN (0,1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='程序固化资源配额目录';

CREATE TABLE subscription_plan (
    id BIGINT NOT NULL AUTO_INCREMENT,
    plan_code VARCHAR(64) NOT NULL,
    plan_name VARCHAR(128) NOT NULL,
    enabled TINYINT NOT NULL DEFAULT 1,
    created_by BIGINT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_subscription_plan_code (plan_code),
    CONSTRAINT fk_subscription_plan_creator FOREIGN KEY (created_by) REFERENCES app_user(id) ON DELETE SET NULL,
    CONSTRAINT ck_subscription_plan_enabled CHECK (enabled IN (0,1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='订阅套餐稳定主记录';

CREATE TABLE subscription_plan_revision (
    id BIGINT NOT NULL AUTO_INCREMENT,
    plan_id BIGINT NOT NULL,
    revision INT NOT NULL,
    revision_name VARCHAR(160) NOT NULL,
    description VARCHAR(1000) NULL,
    enabled TINYINT NOT NULL DEFAULT 1,
    change_reason VARCHAR(500) NOT NULL,
    created_by BIGINT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_plan_revision (plan_id, revision),
    KEY idx_plan_revision_enabled (plan_id, enabled, revision),
    CONSTRAINT fk_plan_revision_plan FOREIGN KEY (plan_id) REFERENCES subscription_plan(id) ON DELETE RESTRICT,
    CONSTRAINT fk_plan_revision_creator FOREIGN KEY (created_by) REFERENCES app_user(id) ON DELETE SET NULL,
    CONSTRAINT ck_plan_revision_number CHECK (revision >= 1),
    CONSTRAINT ck_plan_revision_enabled CHECK (enabled IN (0,1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='不可变套餐修订';

CREATE TABLE plan_revision_feature (
    plan_revision_id BIGINT NOT NULL,
    feature_code VARCHAR(96) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (plan_revision_id, feature_code),
    CONSTRAINT fk_plan_feature_revision FOREIGN KEY (plan_revision_id) REFERENCES subscription_plan_revision(id) ON DELETE RESTRICT,
    CONSTRAINT fk_plan_feature_code FOREIGN KEY (feature_code) REFERENCES feature_catalog(feature_code) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='套餐修订功能集合';

CREATE TABLE plan_revision_quota (
    plan_revision_id BIGINT NOT NULL,
    quota_code VARCHAR(96) NOT NULL,
    quota_value BIGINT NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (plan_revision_id, quota_code),
    CONSTRAINT fk_plan_quota_revision FOREIGN KEY (plan_revision_id) REFERENCES subscription_plan_revision(id) ON DELETE RESTRICT,
    CONSTRAINT fk_plan_quota_code FOREIGN KEY (quota_code) REFERENCES quota_catalog(quota_code) ON DELETE RESTRICT,
    CONSTRAINT ck_plan_quota_value CHECK (quota_value >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='套餐修订资源配额';

CREATE TABLE service_subscription (
    id BIGINT NOT NULL AUTO_INCREMENT,
    subscription_code VARCHAR(64) NOT NULL,
    singleton_key TINYINT NOT NULL DEFAULT 1,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_service_subscription_code (subscription_code),
    UNIQUE KEY uk_single_service_subscription (singleton_key),
    CONSTRAINT ck_service_subscription_singleton CHECK (singleton_key = 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='单客户稳定订阅主记录';

CREATE TABLE service_subscription_revision (
    id BIGINT NOT NULL AUTO_INCREMENT,
    subscription_id BIGINT NOT NULL,
    revision INT NOT NULL,
    plan_revision_id BIGINT NOT NULL,
    subscription_type VARCHAR(24) NOT NULL,
    service_status VARCHAR(24) NOT NULL,
    contract_number VARCHAR(128) NULL,
    start_at DATETIME(3) NOT NULL,
    end_at DATETIME(3) NULL,
    signed_at DATETIME(3) NULL,
    emergency_stopped TINYINT NOT NULL DEFAULT 0,
    change_reason VARCHAR(500) NOT NULL,
    remark VARCHAR(1000) NULL,
    is_current TINYINT NOT NULL DEFAULT 1,
    current_marker TINYINT GENERATED ALWAYS AS
        (CASE WHEN is_current = 1 THEN 1 ELSE NULL END) STORED,
    created_by BIGINT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_subscription_revision (subscription_id, revision),
    UNIQUE KEY uk_subscription_current (subscription_id, current_marker),
    KEY idx_subscription_current_lookup (subscription_id, is_current, created_at),
    CONSTRAINT fk_subscription_revision_subscription FOREIGN KEY (subscription_id) REFERENCES service_subscription(id) ON DELETE RESTRICT,
    CONSTRAINT fk_subscription_revision_plan FOREIGN KEY (plan_revision_id) REFERENCES subscription_plan_revision(id) ON DELETE RESTRICT,
    CONSTRAINT fk_subscription_revision_creator FOREIGN KEY (created_by) REFERENCES app_user(id) ON DELETE SET NULL,
    CONSTRAINT ck_subscription_revision_number CHECK (revision >= 1),
    CONSTRAINT ck_subscription_type CHECK (subscription_type IN ('TRIAL','FIXED_TERM','LONG_TERM')),
    CONSTRAINT ck_service_status CHECK (service_status IN ('TRIAL','ACTIVE','SUSPENDED','EXPIRED','TERMINATED')),
    CONSTRAINT ck_subscription_emergency CHECK (emergency_stopped IN (0,1)),
    CONSTRAINT ck_subscription_current_flag CHECK (is_current IN (0,1)),
    CONSTRAINT ck_subscription_dates CHECK (
        (subscription_type = 'LONG_TERM' AND end_at IS NULL)
        OR
        (subscription_type IN ('TRIAL','FIXED_TERM') AND end_at IS NOT NULL AND end_at > start_at)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='不可变订阅修订';

CREATE TABLE subscription_feature_override (
    id BIGINT NOT NULL AUTO_INCREMENT,
    subscription_id BIGINT NOT NULL,
    feature_code VARCHAR(96) NOT NULL,
    override_type VARCHAR(16) NOT NULL,
    effective_from DATETIME(3) NOT NULL,
    effective_until DATETIME(3) NULL,
    change_reason VARCHAR(500) NOT NULL,
    created_by BIGINT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_feature_override_active (subscription_id, feature_code, effective_from, effective_until),
    CONSTRAINT fk_feature_override_subscription FOREIGN KEY (subscription_id) REFERENCES service_subscription(id) ON DELETE RESTRICT,
    CONSTRAINT fk_feature_override_code FOREIGN KEY (feature_code) REFERENCES feature_catalog(feature_code) ON DELETE RESTRICT,
    CONSTRAINT fk_feature_override_creator FOREIGN KEY (created_by) REFERENCES app_user(id) ON DELETE SET NULL,
    CONSTRAINT ck_feature_override_type CHECK (override_type IN ('GRANT','REVOKE')),
    CONSTRAINT ck_feature_override_dates CHECK (effective_until IS NULL OR effective_until > effective_from)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='订阅功能增补与移除';

CREATE TABLE subscription_quota_override (
    id BIGINT NOT NULL AUTO_INCREMENT,
    subscription_id BIGINT NOT NULL,
    quota_code VARCHAR(96) NOT NULL,
    quota_value BIGINT NOT NULL,
    effective_from DATETIME(3) NOT NULL,
    effective_until DATETIME(3) NULL,
    change_reason VARCHAR(500) NOT NULL,
    created_by BIGINT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_quota_override_active (subscription_id, quota_code, effective_from, effective_until),
    CONSTRAINT fk_quota_override_subscription FOREIGN KEY (subscription_id) REFERENCES service_subscription(id) ON DELETE RESTRICT,
    CONSTRAINT fk_quota_override_code FOREIGN KEY (quota_code) REFERENCES quota_catalog(quota_code) ON DELETE RESTRICT,
    CONSTRAINT fk_quota_override_creator FOREIGN KEY (created_by) REFERENCES app_user(id) ON DELETE SET NULL,
    CONSTRAINT ck_quota_override_value CHECK (quota_value >= 0),
    CONSTRAINT ck_quota_override_dates CHECK (effective_until IS NULL OR effective_until > effective_from)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='订阅资源配额覆盖';

CREATE TABLE service_quota_alert (
    id BIGINT NOT NULL AUTO_INCREMENT,
    quota_code VARCHAR(96) NOT NULL,
    alert_level VARCHAR(24) NOT NULL,
    used_value BIGINT NOT NULL,
    limit_value BIGINT NOT NULL,
    first_occurred_at DATETIME(3) NOT NULL,
    last_occurred_at DATETIME(3) NOT NULL,
    recovered_at DATETIME(3) NULL,
    active_marker VARCHAR(128) GENERATED ALWAYS AS
        (CASE WHEN recovered_at IS NULL THEN CONCAT(quota_code, ':', alert_level) ELSE NULL END) STORED,
    PRIMARY KEY (id),
    UNIQUE KEY uk_active_quota_alert (active_marker),
    CONSTRAINT fk_quota_alert_code FOREIGN KEY (quota_code) REFERENCES quota_catalog(quota_code) ON DELETE RESTRICT,
    CONSTRAINT ck_quota_alert_level CHECK (alert_level IN ('WARNING_80','EXCEEDED_100')),
    CONSTRAINT ck_quota_alert_values CHECK (used_value >= 0 AND limit_value >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='配额阈值和超额告警';

CREATE TABLE batch_entitlement_snapshot (
    id BIGINT NOT NULL AUTO_INCREMENT,
    batch_id BIGINT NOT NULL,
    subscription_revision_id BIGINT NOT NULL,
    granted_features_json JSON NOT NULL,
    quota_snapshot_json JSON NOT NULL,
    snapshot_version VARCHAR(32) NOT NULL,
    captured_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_batch_entitlement_snapshot (batch_id),
    CONSTRAINT fk_batch_entitlement_batch FOREIGN KEY (batch_id) REFERENCES selection_batch(id) ON DELETE RESTRICT,
    CONSTRAINT fk_batch_entitlement_subscription_revision FOREIGN KEY (subscription_revision_id) REFERENCES service_subscription_revision(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='批次启动时功能与配额快照';

CREATE TABLE platform_audit_log (
    id BIGINT NOT NULL AUTO_INCREMENT,
    operation_type VARCHAR(64) NOT NULL,
    operator_user_id BIGINT NULL,
    target_type VARCHAR(64) NULL,
    target_id VARCHAR(128) NULL,
    change_reason VARCHAR(500) NULL,
    before_json JSON NULL,
    after_json JSON NULL,
    request_id VARCHAR(64) NULL,
    success TINYINT NOT NULL DEFAULT 1,
    error_code VARCHAR(64) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_platform_audit_created (created_at),
    KEY idx_platform_audit_operation (operation_type, created_at),
    CONSTRAINT fk_platform_audit_operator FOREIGN KEY (operator_user_id) REFERENCES app_user(id) ON DELETE SET NULL,
    CONSTRAINT ck_platform_audit_success CHECK (success IN (0,1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统管理员平台操作审计';
