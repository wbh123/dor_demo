-- 第二阶段开发数据收口：将第一阶段测试方案转换为可运营的权重与冲突规则。
-- 该文件仅位于测试资源目录，不会被正式应用默认加载。

UPDATE matching_weight_scheme
SET revision = 1,
    weights_json = JSON_OBJECT(
        'sleepTimeMinutes', 1.2,
        'wakeTimeMinutes', 1.0,
        'sleepSensitivity', 1.2,
        'noiseTolerance', 1.2,
        'cleaningFrequency', 1.0,
        'tidinessRequirement', 1.0,
        'airConditionerTemperature', 0.8,
        'studyFrequency', 0.8,
        'gamingVoiceFrequency', 1.1,
        'socialActivity', 0.6
    ),
    conflict_rules_json = JSON_OBJECT(
        'smokingConflictPenalty', 25,
        'sleepTimeWarningMinutes', 60,
        'cleaningWarningDifference', 1,
        'gamingVoiceWarningDifference', 1
    ),
    created_by = COALESCE(created_by, 1),
    change_reason = COALESCE(change_reason, '开发测试默认方案'),
    published_at = CASE
        WHEN enabled = 1 THEN COALESCE(published_at, CURRENT_TIMESTAMP(3))
        ELSE published_at
    END
WHERE id = 1;

DROP PROCEDURE IF EXISTS assert_development_matching_scheme_rules;
DELIMITER $$
CREATE PROCEDURE assert_development_matching_scheme_rules()
BEGIN
    DECLARE revision_count INT;
    DECLARE enabled_count INT;
    DECLARE weight_key_count INT;
    DECLARE rule_key_count INT;

    SELECT COUNT(*) INTO revision_count
    FROM matching_weight_scheme
    WHERE id = 1 AND revision = 1;

    SELECT COUNT(*) INTO enabled_count
    FROM matching_weight_scheme
    WHERE enabled = 1;

    SELECT JSON_LENGTH(weights_json) INTO weight_key_count
    FROM matching_weight_scheme
    WHERE id = 1;

    SELECT JSON_LENGTH(conflict_rules_json) INTO rule_key_count
    FROM matching_weight_scheme
    WHERE id = 1;

    IF revision_count <> 1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'development matching revision assertion failed';
    END IF;
    IF enabled_count <> 1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'development enabled matching scheme assertion failed';
    END IF;
    IF weight_key_count <> 10 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'development matching weight key assertion failed';
    END IF;
    IF rule_key_count <> 4 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'development matching rule key assertion failed';
    END IF;
END$$
DELIMITER ;
CALL assert_development_matching_scheme_rules();
DROP PROCEDURE assert_development_matching_scheme_rules;
