-- 开发环境个人偏好扩展：在测试问卷生成后执行。
-- 该文件仅位于测试资源目录，不会被正式应用默认加载。

UPDATE questionnaire_version
SET questionnaire_name = '2026级学生个人偏好',
    description = '用于确定性室友匹配的个人偏好设置',
    updated_at = CURRENT_TIMESTAMP(3)
WHERE id = 1;

UPDATE questionnaire_question
SET question_code = 'SUMMER_AC_TEMPERATURE',
    question_text = '夏季使用空调制冷时，你偏好的温度是多少？',
    feature_key = 'summerAirConditionerTemperature',
    sort_order = 9,
    updated_at = CURRENT_TIMESTAMP(3)
WHERE questionnaire_version_id = 1
  AND question_code = 'AC_TEMPERATURE';

UPDATE questionnaire_question
SET sort_order = sort_order + 6,
    updated_at = CURRENT_TIMESTAMP(3)
WHERE questionnaire_version_id = 1
  AND sort_order >= 9
  AND question_code <> 'SUMMER_AC_TEMPERATURE';

INSERT INTO questionnaire_question
(questionnaire_version_id, question_code, question_text, question_type,
 feature_key, required_flag, sort_order, enabled)
VALUES
(1, 'SUMMER_AC_OVERNIGHT', '夏季是否接受宿舍整夜开启空调制冷？',
 'SINGLE_CHOICE', 'summerOvernightAirConditioner', 1, 8, 1),
(1, 'WINTER_HEATING_ACCEPTANCE', '冬季是否接受宿舍开启空调制热？',
 'SINGLE_CHOICE', 'winterHeatingAcceptance', 1, 10, 1),
(1, 'WINTER_HEATING_TEMPERATURE', '冬季开启空调制热时，你偏好的温度是多少？',
 'INTEGER', 'winterHeatingTemperature', 0, 11, 1),
(1, 'AFTER_LIGHTS_ACTIVITY', '室友休息或熄灯后，你通常会保持怎样的活动状态？',
 'SINGLE_CHOICE', 'afterLightsActivity', 1, 12, 1),
(1, 'ALARM_SNOOZE', '早晨闹钟通常会响几次？',
 'SINGLE_CHOICE', 'alarmSnooze', 1, 13, 1),
(1, 'STRONG_FOOD_ODOR_ACCEPTANCE', '是否接受室友在宿舍食用气味较重的食物？',
 'SINGLE_CHOICE', 'strongFoodOdorAcceptance', 1, 14, 1)
ON DUPLICATE KEY UPDATE
question_text=VALUES(question_text),
question_type=VALUES(question_type),
feature_key=VALUES(feature_key),
required_flag=VALUES(required_flag),
sort_order=VALUES(sort_order),
enabled=VALUES(enabled);

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
WHERE q.questionnaire_version_id = 1
ON DUPLICATE KEY UPDATE
option_text=VALUES(option_text),
feature_value=VALUES(feature_value),
sort_order=VALUES(sort_order),
enabled=VALUES(enabled);

UPDATE student_feature
SET feature_vector_json = JSON_SET(
        JSON_REMOVE(feature_vector_json, '$.airConditionerTemperature'),
        '$.summerAirConditionerTemperature',
            COALESCE(JSON_EXTRACT(feature_vector_json, '$.airConditionerTemperature'), 26),
        '$.summerOvernightAirConditioner', 2,
        '$.winterHeatingAcceptance', 2,
        '$.winterHeatingTemperature', 22 + MOD(student_id, 3),
        '$.afterLightsActivity', 1 + MOD(student_id, 3),
        '$.alarmSnooze', 1 + MOD(student_id, 3),
        '$.strongFoodOdorAcceptance', 1 + MOD(student_id, 3)
    ),
    algorithm_version = 'feature-v2',
    calculated_at = CURRENT_TIMESTAMP(3)
WHERE batch_id = 1;

UPDATE matching_weight_scheme
SET weights_json = JSON_SET(
        JSON_REMOVE(weights_json, '$.airConditionerTemperature'),
        '$.summerAirConditionerTemperature', 0.8,
        '$.winterHeatingTemperature', 0.6,
        '$.summerOvernightAirConditioner', 1.1,
        '$.winterHeatingAcceptance', 0.8,
        '$.afterLightsActivity', 1.2,
        '$.alarmSnooze', 0.9,
        '$.strongFoodOdorAcceptance', 0.7
    ),
    algorithm_version = 'weighted-distance-v2'
WHERE id = 1;

DROP PROCEDURE IF EXISTS assert_expanded_personal_preferences;
DELIMITER $$
CREATE PROCEDURE assert_expanded_personal_preferences()
BEGIN
    DECLARE question_count_value INT;
    DECLARE weight_count_value INT;

    SELECT COUNT(*) INTO question_count_value
    FROM questionnaire_question
    WHERE questionnaire_version_id = 1
      AND question_code IN (
        'SUMMER_AC_OVERNIGHT', 'SUMMER_AC_TEMPERATURE',
        'WINTER_HEATING_ACCEPTANCE', 'WINTER_HEATING_TEMPERATURE',
        'AFTER_LIGHTS_ACTIVITY', 'ALARM_SNOOZE',
        'STRONG_FOOD_ODOR_ACCEPTANCE'
      );

    SELECT JSON_LENGTH(weights_json) INTO weight_count_value
    FROM matching_weight_scheme WHERE id = 1;

    IF question_count_value <> 7 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'expanded personal preference question assertion failed';
    END IF;
    IF weight_count_value <> 16 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'expanded personal preference weight assertion failed';
    END IF;
END$$
DELIMITER ;
CALL assert_expanded_personal_preferences();
DROP PROCEDURE assert_expanded_personal_preferences;
