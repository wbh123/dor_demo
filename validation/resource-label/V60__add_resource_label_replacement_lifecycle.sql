-- Exact validation mirror of private main V60 for public CI only.
-- V60: resource label replacement / reissue lifecycle.
-- Keep resource_label state machine unchanged (UNBOUND / BOUND / REVOKED) and persist
-- replacement semantics as immutable relations so historical bindings are never overwritten.

CREATE TABLE resource_label_replacement (
    id BIGINT NOT NULL AUTO_INCREMENT,
    old_label_id BIGINT NOT NULL COMMENT '已作废且被替代的旧标签',
    new_label_id BIGINT NOT NULL COMMENT '替代旧标签的新标签',
    reason_code VARCHAR(24) NOT NULL COMMENT 'DAMAGED/LOST/UNREADABLE/CONTENT_ERROR/WRONG_BINDING/OUTDATED/OTHER',
    reason VARCHAR(500) NULL COMMENT '人工补充原因；OTHER 时业务层强制必填',
    operator_user_id BIGINT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_resource_label_replacement_old (old_label_id),
    UNIQUE KEY uk_resource_label_replacement_new (new_label_id),
    KEY idx_resource_label_replacement_created (created_at),
    CONSTRAINT fk_resource_label_replacement_old
        FOREIGN KEY (old_label_id) REFERENCES resource_label(id) ON DELETE RESTRICT,
    CONSTRAINT fk_resource_label_replacement_new
        FOREIGN KEY (new_label_id) REFERENCES resource_label(id) ON DELETE RESTRICT,
    CONSTRAINT fk_resource_label_replacement_operator
        FOREIGN KEY (operator_user_id) REFERENCES app_user(id) ON DELETE SET NULL,
    CONSTRAINT ck_resource_label_replacement_distinct CHECK (old_label_id <> new_label_id),
    CONSTRAINT ck_resource_label_replacement_reason CHECK (
        reason_code IN ('DAMAGED','LOST','UNREADABLE','CONTENT_ERROR','WRONG_BINDING','OUTDATED','OTHER')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='资源标签换标/补标永久替代关系';

-- Existing GENERATE/BIND/REVOKE/PRINT facts remain intact. REPLACE is an additional
-- lifecycle event; it never substitutes the old label REVOKE or new label BIND/GENERATE audit.
ALTER TABLE resource_label_audit
    DROP CHECK ck_resource_label_audit_action;

ALTER TABLE resource_label_audit
    ADD CONSTRAINT ck_resource_label_audit_action
        CHECK (action_type IN ('GENERATE','BIND','REBIND','REVOKE','PRINT','REPLACE'));
