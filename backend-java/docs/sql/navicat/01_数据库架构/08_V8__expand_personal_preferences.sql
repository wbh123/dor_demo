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
