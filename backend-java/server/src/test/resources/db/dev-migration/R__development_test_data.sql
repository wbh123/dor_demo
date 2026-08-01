-- ============================================================
-- 第一阶段本地开发测试数据（仅用于开发与自动化测试）
-- 学生：520（男260 / 女260）
-- 专业：5
-- 床位：640（男320 / 女320）
-- 学号：12位数字，统一以2026开头
-- 当前房型：男生64个五人间；女生80个四人间
-- 扩展规则：房型与房间性别独立配置，男女不混住
-- 本文件位于 src/test/resources，不会被正式应用默认加载。
-- ============================================================

SET NAMES utf8mb4;

-- 本迁移只允许在专用本地开发数据库中执行。
-- 固定测试主键会在重复执行时先清理，避免规则调整后留下旧数据。
SET FOREIGN_KEY_CHECKS = 0;
DELETE FROM allocation_run_result WHERE allocation_run_id = 1;
DELETE FROM allocation_run WHERE id = 1;
DELETE FROM assignment_history WHERE batch_id = 1;
DELETE FROM bed_assignment WHERE batch_id = 1;
DELETE FROM team_invitation WHERE team_id IN (SELECT id FROM selection_team WHERE batch_id = 1);
DELETE FROM selection_team_member WHERE batch_id = 1;
DELETE FROM selection_team WHERE batch_id = 1;
DELETE FROM questionnaire_answer WHERE batch_id = 1;
DELETE FROM student_feature WHERE batch_id = 1;
DELETE FROM batch_bed_scope WHERE batch_id = 1;
DELETE FROM batch_room_scope WHERE batch_id = 1;
DELETE FROM batch_building_scope WHERE batch_id = 1;
DELETE FROM batch_student_eligibility WHERE batch_id = 1;
DELETE FROM selection_batch WHERE id = 1;
DELETE FROM app_user WHERE student_id BETWEEN 1 AND 520;
DELETE FROM student WHERE id BETWEEN 1 AND 520;
DELETE FROM major WHERE id BETWEEN 1 AND 5;
DELETE FROM bed WHERE id BETWEEN 1 AND 1000;
DELETE FROM bed_frame WHERE id BETWEEN 1 AND 1000;
DELETE FROM room WHERE id BETWEEN 1 AND 1000;
DELETE FROM dormitory_floor WHERE id BETWEEN 1 AND 1000;
DELETE FROM dormitory_building WHERE id BETWEEN 1 AND 1000;
SET FOREIGN_KEY_CHECKS = 1;

INSERT INTO major
(id, major_code, major_name, enabled)
VALUES
(1, 'M001', '测试专业1', 1),
(2, 'M002', '测试专业2', 1),
(3, 'M003', '测试专业3', 1),
(4, 'M004', '测试专业4', 1),
(5, 'M005', '测试专业5', 1)
ON DUPLICATE KEY UPDATE
major_code=VALUES(major_code),
major_name=VALUES(major_name),
enabled=VALUES(enabled);

INSERT INTO app_user
(id, student_id, username, password_hash, user_type, account_status, display_name)
VALUES
(1, NULL, 'admin', '{noop}Dormitory@2026', 'ADMIN', 'ACTIVE', '测试管理员')
ON DUPLICATE KEY UPDATE
student_id=VALUES(student_id),
password_hash=VALUES(password_hash),
user_type=VALUES(user_type),
account_status=VALUES(account_status),
display_name=VALUES(display_name);

INSERT INTO campus
(id, campus_code, campus_name, address, enabled)
VALUES
(1, 'MAIN', '主校区测试数据', NULL, 1)
ON DUPLICATE KEY UPDATE
campus_name=VALUES(campus_name),
address=VALUES(address),
enabled=VALUES(enabled);

DROP PROCEDURE IF EXISTS seed_phase1_students;
DELIMITER $$
CREATE PROCEDURE seed_phase1_students()
BEGIN
  DECLARE i INT DEFAULT 1;
  DECLARE gender_value CHAR(1);
  DECLARE gender_index INT;
  DECLARE major_id_value BIGINT;
  DECLARE student_number_value CHAR(12);
  DECLARE student_name_value VARCHAR(128);

  WHILE i <= 520 DO
    SET gender_value = IF(i <= 260, 'M', 'F');
    SET gender_index = IF(i <= 260, i, i - 260);
    SET major_id_value = 1 + MOD(i - 1, 5);
    SET student_number_value = CONCAT('2026', LPAD(i, 8, '0'));
    SET student_name_value = CONCAT(
      IF(gender_value='M', '测试男生', '测试女生'),
      LPAD(gender_index, 3, '0')
    );

    INSERT INTO student
    (id, student_number, student_name, gender, major_id)
    VALUES
    (i, student_number_value, student_name_value, gender_value, major_id_value);

    INSERT INTO app_user
    (id, student_id, username, password_hash, user_type, account_status, display_name)
    VALUES
    (i + 1, i, student_number_value, NULL, 'STUDENT', 'PENDING', student_name_value);

    SET i = i + 1;
  END WHILE;
END$$
DELIMITER ;
CALL seed_phase1_students();
DROP PROCEDURE seed_phase1_students;

DROP PROCEDURE IF EXISTS seed_phase1_dormitories;
DELIMITER $$
CREATE PROCEDURE seed_phase1_dormitories()
BEGIN
  DECLARE building_index INT DEFAULT 1;
  DECLARE floor_index INT;
  DECLARE room_index INT;
  DECLARE floor_id_value INT;
  DECLARE room_id_value INT DEFAULT 0;
  DECLARE bed_id_value INT DEFAULT 0;
  DECLARE rooms_per_floor INT;
  DECLARE gender_value CHAR(1);
  DECLARE gender_building_index INT;
  DECLARE building_code_value VARCHAR(32);
  DECLARE building_name_value VARCHAR(128);
  DECLARE room_number_value VARCHAR(32);
  DECLARE room_type_value VARCHAR(32);
  DECLARE room_capacity_value INT;

  WHILE building_index <= 8 DO
    SET gender_value = IF(building_index <= 4, 'M', 'F');
    SET gender_building_index = IF(building_index <= 4, building_index, building_index - 4);
    SET rooms_per_floor = IF(gender_value='M', 4, 5);
    SET room_type_value = IF(gender_value='M', 'FIVE_PERSON', 'FOUR_PERSON');
    SET room_capacity_value = IF(gender_value='M', 5, 4);
    SET building_code_value = CONCAT(IF(gender_value='M','M','F'), LPAD(gender_building_index, 2, '0'));
    SET building_name_value = CONCAT(IF(gender_value='M','男生','女生'), '测试宿舍', gender_building_index, '栋');

    INSERT INTO dormitory_building
    (id, campus_id, building_code, building_name, gender_restriction, enabled)
    VALUES
    (building_index, 1, building_code_value, building_name_value, gender_value, 1);

    SET floor_index = 1;
    WHILE floor_index <= 4 DO
      SET floor_id_value = (building_index - 1) * 4 + floor_index;

      INSERT INTO dormitory_floor
      (id, building_id, floor_number, floor_name, enabled)
      VALUES
      (floor_id_value, building_index, floor_index, CONCAT(floor_index, '层'), 1);

      SET room_index = 1;
      WHILE room_index <= rooms_per_floor DO
        SET room_id_value = room_id_value + 1;
        SET room_number_value = CONCAT(floor_index, LPAD(room_index, 2, '0'));

        INSERT INTO room
        (id, floor_id, room_number, room_type, capacity, gender_restriction,
         operational_status, state_version, remark)
        VALUES
        (room_id_value, floor_id_value, room_number_value, room_type_value,
         room_capacity_value, gender_value, 'ENABLED', 0,
         IF(gender_value='M',
            '当前需求：男生五人间；房型后续可由管理员按房间调整',
            '当前需求：女生四人间；房型后续可由管理员按房间调整'));

        IF gender_value = 'M' THEN
          INSERT INTO bed_frame
          (id, room_id, frame_code, frame_type, enabled)
          VALUES
          (room_id_value, room_id_value, 'D', 'BUNK_FRAME', 1);

          INSERT INTO bed
          (id, room_id, bed_frame_id, bed_code, bed_type, position_index, operational_status)
          VALUES
          (bed_id_value + 1, room_id_value, NULL, 'A', 'LOFT_BED_DESK', 1, 'ENABLED'),
          (bed_id_value + 2, room_id_value, NULL, 'B', 'LOFT_BED_DESK', 2, 'ENABLED'),
          (bed_id_value + 3, room_id_value, NULL, 'C', 'LOFT_BED_DESK', 3, 'ENABLED'),
          (bed_id_value + 4, room_id_value, room_id_value, 'D-U', 'BUNK_UPPER', 4, 'ENABLED'),
          (bed_id_value + 5, room_id_value, room_id_value, 'D-L', 'BUNK_LOWER', 5, 'ENABLED');
          SET bed_id_value = bed_id_value + 5;
        ELSE
          INSERT INTO bed
          (id, room_id, bed_frame_id, bed_code, bed_type, position_index, operational_status)
          VALUES
          (bed_id_value + 1, room_id_value, NULL, 'A', 'LOFT_BED_DESK', 1, 'ENABLED'),
          (bed_id_value + 2, room_id_value, NULL, 'B', 'LOFT_BED_DESK', 2, 'ENABLED'),
          (bed_id_value + 3, room_id_value, NULL, 'C', 'LOFT_BED_DESK', 3, 'ENABLED'),
          (bed_id_value + 4, room_id_value, NULL, 'D', 'LOFT_BED_DESK', 4, 'ENABLED');
          SET bed_id_value = bed_id_value + 4;
        END IF;

        SET room_index = room_index + 1;
      END WHILE;
      SET floor_index = floor_index + 1;
    END WHILE;
    SET building_index = building_index + 1;
  END WHILE;
END$$
DELIMITER ;
CALL seed_phase1_dormitories();
DROP PROCEDURE seed_phase1_dormitories;

INSERT INTO questionnaire_version
(id, version_code, questionnaire_name, version_status, description, published_at)
VALUES
(1, 'LIFE-HABIT-2026-V1', '2026级学生生活习惯问卷', 'PUBLISHED',
 '第一阶段确定性匹配算法测试问卷', '2026-08-01 00:00:00.000')
ON DUPLICATE KEY UPDATE
questionnaire_name=VALUES(questionnaire_name),
version_status=VALUES(version_status),
description=VALUES(description),
published_at=VALUES(published_at);

INSERT INTO questionnaire_question
(id, questionnaire_version_id, question_code, question_text, question_type,
 feature_key, required_flag, sort_order, enabled)
VALUES
(1,1,'SLEEP_TIME','通常几点入睡？','TIME','sleepTimeMinutes',1,1,1),
(2,1,'WAKE_TIME','通常几点起床？','TIME','wakeTimeMinutes',1,2,1),
(3,1,'NAP_HABIT','午休频率如何？','SINGLE_CHOICE','napHabit',1,3,1),
(4,1,'SLEEP_SENSITIVITY','睡眠敏感程度如何？','SINGLE_CHOICE','sleepSensitivity',1,4,1),
(5,1,'NOISE_TOLERANCE','可接受的宿舍噪声程度如何？','SINGLE_CHOICE','noiseTolerance',1,5,1),
(6,1,'CLEANING_FREQUENCY','宿舍清洁频率如何？','SINGLE_CHOICE','cleaningFrequency',1,6,1),
(7,1,'TIDINESS_REQUIREMENT','对宿舍整洁程度要求如何？','SINGLE_CHOICE','tidinessRequirement',1,7,1),
(8,1,'AC_TEMPERATURE','偏好的空调温度是多少？','INTEGER','airConditionerTemperature',1,8,1),
(9,1,'VENTILATION','通风偏好如何？','SINGLE_CHOICE','ventilationPreference',1,9,1),
(10,1,'STUDY_FREQUENCY','在宿舍学习的频率如何？','SINGLE_CHOICE','studyFrequency',1,10,1),
(11,1,'GAMING_VOICE','游戏或语音交流频率如何？','SINGLE_CHOICE','gamingVoiceFrequency',1,11,1),
(12,1,'SOCIAL_ACTIVITY','宿舍社交活跃程度如何？','SINGLE_CHOICE','socialActivity',1,12,1),
(13,1,'SMOKING_ACCEPTANCE','是否接受室友吸烟？','BOOLEAN','smokingAcceptance',1,13,1),
(14,1,'BED_PREFERENCE','偏好的床位类型是什么？','SINGLE_CHOICE','bedPreference',1,14,1)
ON DUPLICATE KEY UPDATE
question_text=VALUES(question_text),
question_type=VALUES(question_type),
feature_key=VALUES(feature_key),
required_flag=VALUES(required_flag),
sort_order=VALUES(sort_order),
enabled=VALUES(enabled);

INSERT INTO matching_weight_scheme
(id, scheme_code, scheme_name, algorithm_version, weights_json, conflict_rules_json, enabled)
VALUES
(1, 'DEFAULT-2026-V1', '2026级默认生活习惯权重', 'weighted-distance-v1',
 JSON_OBJECT(
  'sleepTimeMinutes',1.2,'wakeTimeMinutes',1.0,'napHabit',0.5,
  'sleepSensitivity',1.2,'noiseTolerance',1.2,'cleaningFrequency',1.0,
  'tidinessRequirement',1.0,'airConditionerTemperature',0.8,
  'ventilationPreference',0.6,'studyFrequency',0.8,
  'gamingVoiceFrequency',1.1,'socialActivity',0.6,
  'smokingAcceptance',2.0,'bedPreference',0.5
 ),
 JSON_OBJECT(
  'smokingAcceptance',JSON_OBJECT('type','HARD_CONFLICT','penalty',100),
  'sleepSensitivityVsGamingVoice',JSON_OBJECT('type','PAIRWISE','penalty',20)
 ),
 1)
ON DUPLICATE KEY UPDATE
scheme_name=VALUES(scheme_name),
algorithm_version=VALUES(algorithm_version),
weights_json=VALUES(weights_json),
conflict_rules_json=VALUES(conflict_rules_json),
enabled=VALUES(enabled);

INSERT INTO selection_batch
(id, batch_code, batch_name, batch_status, questionnaire_version_id,
 matching_weight_scheme_id, start_at, end_at, hold_duration_seconds,
 hold_renewal_limit, allow_team, team_min_size, team_max_size,
 allow_student_random, unselected_strategy, rule_version, created_by)
VALUES
(1, 'PHASE1-2026-TEST', '2026级第一阶段测试选寝批次', 'DRAFT', 1, 1,
 DATE_ADD(CURRENT_TIMESTAMP(3), INTERVAL 7 DAY),
 DATE_ADD(CURRENT_TIMESTAMP(3), INTERVAL 14 DAY),
 300, 1, 1, 2, 5, 1, 'ADMIN_ALLOCATION', 'phase1-rule-v1', 1)
ON DUPLICATE KEY UPDATE
batch_name=VALUES(batch_name),
batch_status='DRAFT',
questionnaire_version_id=VALUES(questionnaire_version_id),
matching_weight_scheme_id=VALUES(matching_weight_scheme_id),
hold_duration_seconds=VALUES(hold_duration_seconds),
hold_renewal_limit=VALUES(hold_renewal_limit),
allow_team=VALUES(allow_team),
team_min_size=VALUES(team_min_size),
team_max_size=VALUES(team_max_size),
allow_student_random=VALUES(allow_student_random),
unselected_strategy=VALUES(unselected_strategy),
rule_version=VALUES(rule_version);

DROP PROCEDURE IF EXISTS seed_phase1_batch_students;
DELIMITER $$
CREATE PROCEDURE seed_phase1_batch_students()
BEGIN
  DECLARE i INT DEFAULT 1;
  DECLARE gender_value CHAR(1);
  DECLARE sleep_sensitivity INT;
  DECLARE noise_tolerance INT;
  DECLARE cleaning_frequency INT;
  DECLARE gaming_voice_frequency INT;
  DECLARE social_activity INT;
  DECLARE bed_preference_value VARCHAR(32);

  WHILE i <= 520 DO
    SET gender_value = IF(i <= 260, 'M', 'F');
    SET sleep_sensitivity = 1 + MOD(i, 5);
    SET noise_tolerance = 6 - sleep_sensitivity;
    SET cleaning_frequency = 1 + MOD(i * 2, 5);
    SET gaming_voice_frequency = 1 + MOD(i * 3, 5);
    SET social_activity = 1 + MOD(i * 4, 5);
    SET bed_preference_value =
      ELT(1 + MOD(i, 4), 'ANY', 'LOFT_BED_DESK', 'BUNK_UPPER', 'BUNK_LOWER');

    INSERT INTO batch_student_eligibility
    (id, batch_id, student_id, eligibility_status, reason_code)
    VALUES
    (i, 1, i, 'ELIGIBLE', NULL);

    INSERT INTO student_feature
    (id, batch_id, student_id, algorithm_version, feature_vector_json,
     explanation_tags_json, calculated_at, source_answer_version)
    VALUES
    (i, 1, i, 'feature-v1',
     JSON_OBJECT(
       'sleepTimeMinutes', MOD(1320 + i * 10, 1440),
       'wakeTimeMinutes', 360 + MOD(i, 3) * 60 + MOD(i * 5, 60),
       'napHabit', MOD(i, 3),
       'sleepSensitivity', sleep_sensitivity,
       'noiseTolerance', noise_tolerance,
       'cleaningFrequency', cleaning_frequency,
       'tidinessRequirement', 1 + MOD(i + 2, 5),
       'airConditionerTemperature', 23 + MOD(i, 5),
       'ventilationPreference', 1 + MOD(i + 1, 5),
       'studyFrequency', 1 + MOD(i + 3, 5),
       'gamingVoiceFrequency', gaming_voice_frequency,
       'socialActivity', social_activity,
       'smokingAcceptance', FALSE,
       'bedPreference', bed_preference_value,
       'gender', gender_value
     ),
     JSON_ARRAY(
       IF(sleep_sensitivity >= 4, '睡眠较敏感', '作息适中'),
       IF(cleaning_frequency >= 4, '重视卫生', '清洁习惯适中'),
       IF(gaming_voice_frequency <= 2, '偏好安静', '交流频率适中')
     ),
     '2026-08-01 00:00:00.000', 0);

    SET i = i + 1;
  END WHILE;
END$$
DELIMITER ;
CALL seed_phase1_batch_students();
DROP PROCEDURE seed_phase1_batch_students;

INSERT INTO batch_building_scope (id, batch_id, building_id)
VALUES
(1,1,1),(2,1,2),(3,1,3),(4,1,4),
(5,1,5),(6,1,6),(7,1,7),(8,1,8);

DROP PROCEDURE IF EXISTS assert_phase1_development_data;
DELIMITER $$
CREATE PROCEDURE assert_phase1_development_data()
BEGIN
  DECLARE student_count_value INT;
  DECLARE major_count_value INT;
  DECLARE male_student_count_value INT;
  DECLARE female_student_count_value INT;
  DECLARE room_count_value INT;
  DECLARE male_room_count_value INT;
  DECLARE female_room_count_value INT;
  DECLARE bed_count_value INT;
  DECLARE male_bed_count_value INT;
  DECLARE female_bed_count_value INT;
  DECLARE invalid_gender_room_count INT;
  DECLARE invalid_male_layout_count INT;
  DECLARE invalid_female_layout_count INT;

  SELECT COUNT(*) INTO student_count_value
  FROM student WHERE id BETWEEN 1 AND 520;
  SELECT COUNT(*) INTO major_count_value
  FROM major WHERE id BETWEEN 1 AND 5;
  SELECT COUNT(*) INTO male_student_count_value
  FROM student WHERE id BETWEEN 1 AND 520 AND gender='M';
  SELECT COUNT(*) INTO female_student_count_value
  FROM student WHERE id BETWEEN 1 AND 520 AND gender='F';

  SELECT COUNT(*) INTO room_count_value FROM room WHERE id BETWEEN 1 AND 144;
  SELECT COUNT(*) INTO male_room_count_value
  FROM room WHERE id BETWEEN 1 AND 144
    AND gender_restriction='M' AND room_type='FIVE_PERSON' AND capacity=5;
  SELECT COUNT(*) INTO female_room_count_value
  FROM room WHERE id BETWEEN 1 AND 144
    AND gender_restriction='F' AND room_type='FOUR_PERSON' AND capacity=4;
  SELECT COUNT(*) INTO invalid_gender_room_count
  FROM room WHERE id BETWEEN 1 AND 144 AND gender_restriction NOT IN ('M','F');

  SELECT COUNT(*) INTO bed_count_value FROM bed WHERE id BETWEEN 1 AND 640;
  SELECT COUNT(*) INTO male_bed_count_value
  FROM bed b JOIN room r ON r.id=b.room_id
  WHERE b.id BETWEEN 1 AND 640 AND r.gender_restriction='M';
  SELECT COUNT(*) INTO female_bed_count_value
  FROM bed b JOIN room r ON r.id=b.room_id
  WHERE b.id BETWEEN 1 AND 640 AND r.gender_restriction='F';

  SELECT COUNT(*) INTO invalid_male_layout_count
  FROM (
    SELECT r.id
    FROM room r JOIN bed b ON b.room_id=r.id
    WHERE r.id BETWEEN 1 AND 144 AND r.gender_restriction='M'
    GROUP BY r.id
    HAVING COUNT(*) <> 5
       OR SUM(b.bed_type='LOFT_BED_DESK') <> 3
       OR SUM(b.bed_type='BUNK_UPPER') <> 1
       OR SUM(b.bed_type='BUNK_LOWER') <> 1
  ) invalid_male;

  SELECT COUNT(*) INTO invalid_female_layout_count
  FROM (
    SELECT r.id
    FROM room r JOIN bed b ON b.room_id=r.id
    WHERE r.id BETWEEN 1 AND 144 AND r.gender_restriction='F'
    GROUP BY r.id
    HAVING COUNT(*) <> 4
       OR SUM(b.bed_type='LOFT_BED_DESK') <> 4
       OR SUM(b.bed_type IN ('BUNK_UPPER','BUNK_LOWER')) <> 0
  ) invalid_female;

  IF student_count_value <> 520
     OR major_count_value <> 5
     OR male_student_count_value <> 260
     OR female_student_count_value <> 260 THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT='第一阶段学生或专业测试数据数量不正确';
  END IF;

  IF room_count_value <> 144
     OR male_room_count_value <> 64
     OR female_room_count_value <> 80
     OR invalid_gender_room_count <> 0
     OR invalid_male_layout_count <> 0
     OR invalid_female_layout_count <> 0 THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT='第一阶段房间类型、性别或床位布局不正确';
  END IF;

  IF bed_count_value <> 640
     OR male_bed_count_value <> 320
     OR female_bed_count_value <> 320 THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT='第一阶段床位测试数据数量不正确';
  END IF;
END$$
DELIMITER ;
CALL assert_phase1_development_data();
DROP PROCEDURE assert_phase1_development_data;
