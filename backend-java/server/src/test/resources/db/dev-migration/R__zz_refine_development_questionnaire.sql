-- ============================================================
-- 开发测试数据收口：确保重复数据迁移完成后仍使用三态吸烟偏好
-- 必须排在 R__development_test_data.sql 之后执行。
-- ============================================================

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

UPDATE student_feature
SET feature_vector_json = JSON_SET(
    feature_vector_json,
    '$.smokingAcceptance',
    ELT(1 + MOD(student_id, 3), 'ACCEPT', 'REJECT', 'ANY')
),
calculated_at = CURRENT_TIMESTAMP(3)
WHERE batch_id = 1 AND student_id BETWEEN 1 AND 520;

DROP PROCEDURE IF EXISTS assert_development_smoking_preferences;
DELIMITER $$
CREATE PROCEDURE assert_development_smoking_preferences()
BEGIN
    DECLARE option_count INT;
    DECLARE invalid_feature_count INT;

    SELECT COUNT(*) INTO option_count
    FROM questionnaire_option qo
    JOIN questionnaire_question qq ON qq.id = qo.question_id
    WHERE qq.question_code = 'SMOKING_ACCEPTANCE'
      AND qo.option_code IN ('ACCEPT', 'REJECT', 'ANY');

    SELECT COUNT(*) INTO invalid_feature_count
    FROM student_feature
    WHERE batch_id = 1
      AND student_id BETWEEN 1 AND 520
      AND JSON_UNQUOTE(JSON_EXTRACT(feature_vector_json, '$.smokingAcceptance'))
          NOT IN ('ACCEPT', 'REJECT', 'ANY');

    IF option_count <> 3 OR invalid_feature_count <> 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '开发测试数据中的吸烟偏好三态配置不正确';
    END IF;
END$$
DELIMITER ;
CALL assert_development_smoking_preferences();
DROP PROCEDURE assert_development_smoking_preferences;
