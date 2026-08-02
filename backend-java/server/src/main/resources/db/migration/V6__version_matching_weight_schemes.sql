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