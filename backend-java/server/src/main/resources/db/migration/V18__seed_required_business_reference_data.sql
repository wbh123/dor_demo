-- V18：为空数据库补齐批次创建所需的正式业务参考数据。
-- 已有已发布问卷或已启用匹配方案时，不覆盖管理员配置。

SET @published_questionnaire_count := (
    SELECT COUNT(*) FROM questionnaire_version WHERE version_status='PUBLISHED'
);

INSERT INTO questionnaire_version
(version_code, questionnaire_name, version_status, description, published_at, version)
SELECT
    'SYSTEM-PREFERENCE-V1',
    '系统默认学生生活习惯问卷',
    'PUBLISHED',
    '系统初始化的基础个人偏好问卷；管理员可在后续版本中新增问卷修订。',
    CURRENT_TIMESTAMP(3),
    0
WHERE @published_questionnaire_count=0
  AND NOT EXISTS (
      SELECT 1 FROM questionnaire_version WHERE version_code='SYSTEM-PREFERENCE-V1'
  );

UPDATE questionnaire_version
SET version_status='PUBLISHED',
    published_at=COALESCE(published_at,CURRENT_TIMESTAMP(3)),
    description=COALESCE(description,'系统初始化的基础个人偏好问卷；管理员可在后续版本中新增问卷修订。')
WHERE version_code='SYSTEM-PREFERENCE-V1'
  AND @published_questionnaire_count=0;

SET @system_questionnaire_id := (
    SELECT id FROM questionnaire_version
    WHERE version_code='SYSTEM-PREFERENCE-V1'
    LIMIT 1
);

-- 问卷采用V8后的最终题目结构；已有相同题目编码时保持原配置。
INSERT INTO questionnaire_question
(questionnaire_version_id,question_code,question_text,question_type,
 feature_key,required_flag,sort_order,enabled)
SELECT @system_questionnaire_id,definition.question_code,definition.question_text,
       definition.question_type,definition.feature_key,
       definition.required_flag,definition.sort_order,1
FROM (
    SELECT 'SLEEP_TIME' question_code,'通常几点入睡？' question_text,'TIME' question_type,'sleepTimeMinutes' feature_key,1 required_flag,1 sort_order
    UNION ALL SELECT 'WAKE_TIME','通常几点起床？','TIME','wakeTimeMinutes',1,2
    UNION ALL SELECT 'NAP_HABIT','午休频率如何？','SINGLE_CHOICE','napHabit',1,3
    UNION ALL SELECT 'SLEEP_SENSITIVITY','睡眠敏感程度如何？','SINGLE_CHOICE','sleepSensitivity',1,4
    UNION ALL SELECT 'NOISE_TOLERANCE','可接受的宿舍噪声程度如何？','SINGLE_CHOICE','noiseTolerance',1,5
    UNION ALL SELECT 'CLEANING_FREQUENCY','宿舍清洁频率如何？','SINGLE_CHOICE','cleaningFrequency',1,6
    UNION ALL SELECT 'TIDINESS_REQUIREMENT','对宿舍整洁程度要求如何？','SINGLE_CHOICE','tidinessRequirement',1,7
    UNION ALL SELECT 'SUMMER_AC_OVERNIGHT','夏季夜间是否接受整夜开启空调？','SINGLE_CHOICE','summerOvernightAirConditioner',1,8
    UNION ALL SELECT 'SUMMER_AC_TEMPERATURE','夏季使用空调时偏好的温度是多少？','INTEGER','summerAirConditionerTemperature',1,9
    UNION ALL SELECT 'WINTER_HEATING_ACCEPTANCE','冬季是否接受空调制热？','SINGLE_CHOICE','winterHeatingAcceptance',1,10
    UNION ALL SELECT 'WINTER_HEATING_TEMPERATURE','冬季制热时偏好的温度是多少？','INTEGER','winterHeatingTemperature',0,11
    UNION ALL SELECT 'AFTER_LIGHTS_ACTIVITY','熄灯后通常还会做什么？','SINGLE_CHOICE','afterLightsActivity',1,12
    UNION ALL SELECT 'ALARM_SNOOZE','是否会设置多个闹钟或重复响铃？','SINGLE_CHOICE','alarmSnooze',1,13
    UNION ALL SELECT 'STRONG_FOOD_ODOR_ACCEPTANCE','是否接受在宿舍食用榴莲、螺蛳粉等气味较重的食物？','SINGLE_CHOICE','strongFoodOdorAcceptance',1,14
    UNION ALL SELECT 'VENTILATION','通风偏好如何？','SINGLE_CHOICE','ventilationPreference',1,15
    UNION ALL SELECT 'STUDY_FREQUENCY','在宿舍学习的频率如何？','SINGLE_CHOICE','studyFrequency',1,16
    UNION ALL SELECT 'GAMING_VOICE','游戏或语音交流频率如何？','SINGLE_CHOICE','gamingVoiceFrequency',1,17
    UNION ALL SELECT 'SOCIAL_ACTIVITY','宿舍社交活跃程度如何？','SINGLE_CHOICE','socialActivity',1,18
    UNION ALL SELECT 'SMOKING_ACCEPTANCE','是否接受室友吸烟？','SINGLE_CHOICE','smokingAcceptance',1,19
    UNION ALL SELECT 'BED_PREFERENCE','偏好的床位类型是什么？','SINGLE_CHOICE','bedPreference',1,20
) definition
WHERE @system_questionnaire_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM questionnaire_question existing
      WHERE existing.questionnaire_version_id=@system_questionnaire_id
        AND existing.question_code=definition.question_code
  );

-- 通用单选题选项。
INSERT INTO questionnaire_option
(question_id,option_code,option_text,feature_value,sort_order,enabled)
SELECT question.id,definition.option_code,definition.option_text,
       definition.feature_value,definition.sort_order,1
FROM questionnaire_question question
JOIN questionnaire_version version_record
  ON version_record.id=question.questionnaire_version_id
JOIN (
    SELECT 'NAP_HABIT' question_code,'NEVER' option_code,'基本不午休' option_text,1.0000 feature_value,1 sort_order
    UNION ALL SELECT 'NAP_HABIT','SOMETIMES','偶尔午休',3.0000,2
    UNION ALL SELECT 'NAP_HABIT','DAILY','几乎每天午休',5.0000,3
    UNION ALL SELECT 'SLEEP_SENSITIVITY','LOW','不太敏感',1.0000,1
    UNION ALL SELECT 'SLEEP_SENSITIVITY','MEDIUM','一般',3.0000,2
    UNION ALL SELECT 'SLEEP_SENSITIVITY','HIGH','非常敏感',5.0000,3
    UNION ALL SELECT 'NOISE_TOLERANCE','LOW','偏好安静',1.0000,1
    UNION ALL SELECT 'NOISE_TOLERANCE','MEDIUM','可接受一般声音',3.0000,2
    UNION ALL SELECT 'NOISE_TOLERANCE','HIGH','可接受较多声音',5.0000,3
    UNION ALL SELECT 'CLEANING_FREQUENCY','WEEKLY','每周一次左右',1.0000,1
    UNION ALL SELECT 'CLEANING_FREQUENCY','OFTEN','每两至三天一次',3.0000,2
    UNION ALL SELECT 'CLEANING_FREQUENCY','DAILY','每天清洁',5.0000,3
    UNION ALL SELECT 'TIDINESS_REQUIREMENT','LOW','基本整洁即可',1.0000,1
    UNION ALL SELECT 'TIDINESS_REQUIREMENT','MEDIUM','保持一般整洁',3.0000,2
    UNION ALL SELECT 'TIDINESS_REQUIREMENT','HIGH','要求非常整洁',5.0000,3
    UNION ALL SELECT 'SUMMER_AC_OVERNIGHT','ACCEPT','接受整夜开启',5.0000,1
    UNION ALL SELECT 'SUMMER_AC_OVERNIGHT','REJECT','不接受整夜开启',1.0000,2
    UNION ALL SELECT 'SUMMER_AC_OVERNIGHT','ANY','均可',3.0000,3
    UNION ALL SELECT 'WINTER_HEATING_ACCEPTANCE','ACCEPT','接受制热',5.0000,1
    UNION ALL SELECT 'WINTER_HEATING_ACCEPTANCE','REJECT','不接受制热',1.0000,2
    UNION ALL SELECT 'WINTER_HEATING_ACCEPTANCE','ANY','均可',3.0000,3
    UNION ALL SELECT 'AFTER_LIGHTS_ACTIVITY','SLEEP','直接休息',1.0000,1
    UNION ALL SELECT 'AFTER_LIGHTS_ACTIVITY','PHONE_SILENT','静音使用手机',2.0000,2
    UNION ALL SELECT 'AFTER_LIGHTS_ACTIVITY','DESK_LIGHT_STUDY','使用台灯学习或工作',4.0000,3
    UNION ALL SELECT 'AFTER_LIGHTS_ACTIVITY','VOICE_OR_GAME','语音、游戏或娱乐',5.0000,4
    UNION ALL SELECT 'ALARM_SNOOZE','SINGLE','单个闹钟，响后起床',1.0000,1
    UNION ALL SELECT 'ALARM_SNOOZE','FEW','少量重复响铃',3.0000,2
    UNION ALL SELECT 'ALARM_SNOOZE','MANY','多个闹钟或长时间重复响铃',5.0000,3
    UNION ALL SELECT 'STRONG_FOOD_ODOR_ACCEPTANCE','ACCEPT','接受',5.0000,1
    UNION ALL SELECT 'STRONG_FOOD_ODOR_ACCEPTANCE','REJECT','不接受',1.0000,2
    UNION ALL SELECT 'STRONG_FOOD_ODOR_ACCEPTANCE','ANY','均可',3.0000,3
    UNION ALL SELECT 'VENTILATION','LOW','较少通风',1.0000,1
    UNION ALL SELECT 'VENTILATION','MEDIUM','适度通风',3.0000,2
    UNION ALL SELECT 'VENTILATION','HIGH','经常通风',5.0000,3
    UNION ALL SELECT 'STUDY_FREQUENCY','LOW','很少在宿舍学习',1.0000,1
    UNION ALL SELECT 'STUDY_FREQUENCY','MEDIUM','偶尔在宿舍学习',3.0000,2
    UNION ALL SELECT 'STUDY_FREQUENCY','HIGH','经常在宿舍学习',5.0000,3
    UNION ALL SELECT 'GAMING_VOICE','LOW','很少游戏或语音',1.0000,1
    UNION ALL SELECT 'GAMING_VOICE','MEDIUM','偶尔游戏或语音',3.0000,2
    UNION ALL SELECT 'GAMING_VOICE','HIGH','经常游戏或语音',5.0000,3
    UNION ALL SELECT 'SOCIAL_ACTIVITY','LOW','偏好安静独处',1.0000,1
    UNION ALL SELECT 'SOCIAL_ACTIVITY','MEDIUM','适度交流',3.0000,2
    UNION ALL SELECT 'SOCIAL_ACTIVITY','HIGH','喜欢活跃交流',5.0000,3
    UNION ALL SELECT 'SMOKING_ACCEPTANCE','ACCEPT','接受',1.0000,1
    UNION ALL SELECT 'SMOKING_ACCEPTANCE','REJECT','不接受',0.0000,2
    UNION ALL SELECT 'SMOKING_ACCEPTANCE','ANY','均可',0.5000,3
    UNION ALL SELECT 'BED_PREFERENCE','ANY','无特殊偏好',0.0000,1
    UNION ALL SELECT 'BED_PREFERENCE','LOFT_BED_DESK','上床下桌',1.0000,2
    UNION ALL SELECT 'BED_PREFERENCE','BUNK_UPPER','上下铺上铺',2.0000,3
    UNION ALL SELECT 'BED_PREFERENCE','BUNK_LOWER','上下铺下铺',3.0000,4
) definition ON definition.question_code=question.question_code
WHERE version_record.version_code='SYSTEM-PREFERENCE-V1'
  AND NOT EXISTS (
      SELECT 1 FROM questionnaire_option existing
      WHERE existing.question_id=question.id
        AND existing.option_code=definition.option_code
  );

-- 如果没有启用的匹配方案，则补齐系统默认方案；已有方案不被覆盖。
SET @enabled_matching_scheme_count := (
    SELECT COUNT(*) FROM matching_weight_scheme WHERE enabled=1
);

INSERT INTO matching_weight_scheme
(scheme_code,scheme_name,revision,algorithm_version,
 weights_json,conflict_rules_json,enabled,version,
 created_by,change_reason,published_at)
SELECT
    'SYSTEM_DEFAULT',
    '系统默认生活习惯匹配方案',
    1,
    'weighted-distance-v2',
    JSON_OBJECT(
        'sleepTimeMinutes',1.2,
        'wakeTimeMinutes',1.0,
        'napHabit',0.5,
        'sleepSensitivity',1.2,
        'noiseTolerance',1.2,
        'cleaningFrequency',1.0,
        'tidinessRequirement',1.0,
        'summerAirConditionerTemperature',0.8,
        'winterHeatingTemperature',0.6,
        'summerOvernightAirConditioner',1.1,
        'winterHeatingAcceptance',0.8,
        'afterLightsActivity',1.2,
        'alarmSnooze',0.9,
        'strongFoodOdorAcceptance',0.7,
        'ventilationPreference',0.6,
        'studyFrequency',0.8,
        'gamingVoiceFrequency',1.1,
        'socialActivity',0.6,
        'smokingAcceptance',2.0,
        'bedPreference',0.5
    ),
    JSON_OBJECT(
        'smokingConflictPenalty',25,
        'sleepTimeWarningMinutes',60,
        'cleaningWarningDifference',1,
        'gamingVoiceWarningDifference',1
    ),
    1,
    0,
    NULL,
    'Flyway V18初始化系统默认匹配方案',
    CURRENT_TIMESTAMP(3)
WHERE @enabled_matching_scheme_count=0
  AND NOT EXISTS (
      SELECT 1 FROM matching_weight_scheme
      WHERE scheme_code='SYSTEM_DEFAULT' AND revision=1
  );

UPDATE matching_weight_scheme
SET scheme_name='系统默认生活习惯匹配方案',
    algorithm_version='weighted-distance-v2',
    weights_json=JSON_OBJECT(
        'sleepTimeMinutes',1.2,
        'wakeTimeMinutes',1.0,
        'napHabit',0.5,
        'sleepSensitivity',1.2,
        'noiseTolerance',1.2,
        'cleaningFrequency',1.0,
        'tidinessRequirement',1.0,
        'summerAirConditionerTemperature',0.8,
        'winterHeatingTemperature',0.6,
        'summerOvernightAirConditioner',1.1,
        'winterHeatingAcceptance',0.8,
        'afterLightsActivity',1.2,
        'alarmSnooze',0.9,
        'strongFoodOdorAcceptance',0.7,
        'ventilationPreference',0.6,
        'studyFrequency',0.8,
        'gamingVoiceFrequency',1.1,
        'socialActivity',0.6,
        'smokingAcceptance',2.0,
        'bedPreference',0.5
    ),
    conflict_rules_json=JSON_OBJECT(
        'smokingConflictPenalty',25,
        'sleepTimeWarningMinutes',60,
        'cleaningWarningDifference',1,
        'gamingVoiceWarningDifference',1
    ),
    enabled=1,
    change_reason=COALESCE(change_reason,'Flyway V18恢复系统默认匹配方案'),
    published_at=COALESCE(published_at,CURRENT_TIMESTAMP(3))
WHERE scheme_code='SYSTEM_DEFAULT'
  AND revision=1
  AND @enabled_matching_scheme_count=0;
