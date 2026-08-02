-- ============================================================================
-- 武汉科技大学学生宿舍智能选择系统：测试数据全量重置脚本
--
-- 使用方式：
--   mysql -u<user> -p <database> < backend-java/docs/sql/reset_and_seed_test_data.sql
--
-- 约束：
-- 1. 必须先完成 Flyway V1 至当前最新正式版本数据库迁移；
-- 2. 清空全部业务数据，但保留 flyway_schema_history；
-- 3. 管理员 username='admin' 的账号主键、密码哈希和状态原样保留；
-- 4. 学生学号均为12位数字；
-- 5. 学生账号恢复为待激活状态，便于重复测试激活流程。
-- ============================================================================

SET NAMES utf8mb4;
SET @database_name = DATABASE();

DROP TEMPORARY TABLE IF EXISTS preserved_admin_account;
CREATE TEMPORARY TABLE preserved_admin_account AS
SELECT *
FROM app_user
WHERE username='admin' AND user_type='ADMIN'
LIMIT 1;

SET @admin_count = (SELECT COUNT(*) FROM preserved_admin_account);

DROP PROCEDURE IF EXISTS assert_admin_exists;
DELIMITER $$
CREATE PROCEDURE assert_admin_exists()
BEGIN
    IF @admin_count <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '管理员账号admin不存在或不唯一，已停止清库';
    END IF;
END$$
DELIMITER ;
CALL assert_admin_exists();
DROP PROCEDURE assert_admin_exists;

DROP PROCEDURE IF EXISTS clear_all_business_data;
DELIMITER $$
CREATE PROCEDURE clear_all_business_data()
BEGIN
    DECLARE finished INT DEFAULT 0;
    DECLARE target_table VARCHAR(128);
    DECLARE table_cursor CURSOR FOR
        SELECT table_name
        FROM information_schema.tables
        WHERE table_schema=@database_name
          AND table_type='BASE TABLE'
          AND table_name <> 'flyway_schema_history'
        ORDER BY table_name;
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET finished = 1;

    SET FOREIGN_KEY_CHECKS=0;
    OPEN table_cursor;
    table_loop: LOOP
        FETCH table_cursor INTO target_table;
        IF finished = 1 THEN
            LEAVE table_loop;
        END IF;
        SET @delete_sql = CONCAT('DELETE FROM `', REPLACE(target_table, '`', '``'), '`');
        PREPARE delete_statement FROM @delete_sql;
        EXECUTE delete_statement;
        DEALLOCATE PREPARE delete_statement;
    END LOOP;
    CLOSE table_cursor;
    SET FOREIGN_KEY_CHECKS=1;
END$$
DELIMITER ;
CALL clear_all_business_data();
DROP PROCEDURE clear_all_business_data;

-- 恢复原管理员账号，所有字段均保持清库前值。
INSERT INTO app_user
SELECT * FROM preserved_admin_account;
DROP TEMPORARY TABLE preserved_admin_account;
SET @admin_id = (SELECT id FROM app_user WHERE username='admin' AND user_type='ADMIN');

-- ----------------------------------------------------------------------------
-- 基础目录
-- ----------------------------------------------------------------------------
INSERT INTO campus
(id, campus_code, campus_name, address, enabled)
VALUES
(1, 'HQ', '黄家湖校区', '湖北省武汉市青山区黄家湖西路', 1);

INSERT INTO major
(id, major_code, major_name, enabled)
VALUES
(1, 'M001', '计算机科学与技术', 1),
(2, 'M002', '软件工程', 1),
(3, 'M003', '土木工程', 1),
(4, 'M004', '机械工程', 1),
(5, 'M005', '国际经济与贸易', 1);

-- ----------------------------------------------------------------------------
-- 学生：全部为12位数字学号，含中国及国际学生
-- ----------------------------------------------------------------------------
INSERT INTO student
(id, student_number, student_name, gender, major_id, nationality_code, phone_number)
VALUES
(1,  '202600000001', '张明宇',       'M', 1, 'CN', '+86 138 0000 0001'),
(2,  '202600000002', '李浩然',       'M', 2, 'CN', '+86 138 0000 0002'),
(3,  '202600000003', '王子轩',       'M', 3, 'CN', '+86 138 0000 0003'),
(4,  '202600000004', '陈嘉乐',       'M', 4, 'CN', '+86 138 0000 0004'),
(5,  '202600000005', 'Michael Brown','M', 1, 'US', '+1 202 555 0105'),
(6,  '202600000006', 'Ivan Petrov',  'M', 2, 'RU', '+7 912 555 0106'),
(7,  '202600000007', '田中悠真',     'M', 3, 'JP', '+81 90 5555 0107'),
(8,  '202600000008', 'Kim Min-jun',  'M', 4, 'KR', '+82 10 5555 0108'),
(9,  '202600000009', '周雨桐',       'F', 1, 'CN', '+86 139 0000 0009'),
(10, '202600000010', '林若曦',       'F', 2, 'CN', '+86 139 0000 0010'),
(11, '202600000011', '赵欣怡',       'F', 3, 'CN', '+86 139 0000 0011'),
(12, '202600000012', '孙语涵',       'F', 4, 'CN', '+86 139 0000 0012'),
(13, '202600000013', 'Emma Wilson',  'F', 1, 'GB', '+44 7700 900013'),
(14, '202600000014', 'Sophie Martin','F', 2, 'FR', '+33 6 12 34 56 14'),
(15, '202600000015', '佐藤美咲',     'F', 3, 'JP', '+81 90 5555 0115'),
(16, '202600000016', 'Park Seo-yeon','F', 4, 'KR', '+82 10 5555 0116'),
(17, '202600000017', '刘宇航',       'M', 5, 'CN', NULL),
(18, '202600000018', 'Ahmed Hassan', 'M', 5, 'EG', '+20 100 555 0118'),
(19, '202600000019', '何思琪',       'F', 5, 'CN', NULL),
(20, '202600000020', 'Ananya Sharma','F', 5, 'IN', '+91 98765 00120');

INSERT INTO app_user
(student_id, username, password_hash, user_type, account_status, display_name,
 last_login_at, welcome_acknowledged_at, version)
SELECT id, student_number, NULL, 'STUDENT', 'PENDING', student_name,
       NULL, NULL, 0
FROM student
ORDER BY id;

-- ----------------------------------------------------------------------------
-- 宿舍资源：男女各一栋，每栋四个房间，覆盖四人间与五人间
-- ----------------------------------------------------------------------------
INSERT INTO dormitory_building
(id, campus_id, building_code, building_name, gender_restriction, enabled)
VALUES
(1, 1, 'M-A', '男生一舍', 'M', 1),
(2, 1, 'F-A', '女生一舍', 'F', 1);

INSERT INTO dormitory_floor
(id, building_id, floor_number, floor_name, enabled)
VALUES
(1, 1, 1, '男生一舍一层', 1),
(2, 1, 2, '男生一舍二层', 1),
(3, 2, 1, '女生一舍一层', 1),
(4, 2, 2, '女生一舍二层', 1);

INSERT INTO room
(id, floor_id, room_number, room_type, capacity, gender_restriction,
 operational_status, state_version, remark, version)
VALUES
(1, 1, '101', 'FOUR_PERSON', 4, 'M', 'ENABLED', 0, '四张上床下桌', 0),
(2, 1, '102', 'FIVE_PERSON', 5, 'M', 'ENABLED', 0, '三张上床下桌加一组上下铺', 0),
(3, 2, '201', 'FOUR_PERSON', 4, 'M', 'ENABLED', 0, '四张上床下桌', 0),
(4, 2, '202', 'FIVE_PERSON', 5, 'M', 'ENABLED', 0, '三张上床下桌加一组上下铺', 0),
(5, 3, '101', 'FOUR_PERSON', 4, 'F', 'ENABLED', 0, '四张上床下桌', 0),
(6, 3, '102', 'FIVE_PERSON', 5, 'F', 'ENABLED', 0, '三张上床下桌加一组上下铺', 0),
(7, 4, '201', 'FOUR_PERSON', 4, 'F', 'ENABLED', 0, '四张上床下桌', 0),
(8, 4, '202', 'FIVE_PERSON', 5, 'F', 'ENABLED', 0, '三张上床下桌加一组上下铺', 0);

INSERT INTO bed_frame
(id, room_id, frame_code, frame_type, enabled)
VALUES
(1, 2, 'BF-102-1', 'BUNK_FRAME', 1),
(2, 4, 'BF-202-1', 'BUNK_FRAME', 1),
(3, 6, 'BF-102-1', 'BUNK_FRAME', 1),
(4, 8, 'BF-202-1', 'BUNK_FRAME', 1);

INSERT INTO bed
(id, room_id, bed_frame_id, bed_code, bed_type, position_index, operational_status)
VALUES
-- 男生101
(1, 1, NULL, 'A', 'LOFT_BED_DESK', 1, 'ENABLED'),
(2, 1, NULL, 'B', 'LOFT_BED_DESK', 2, 'ENABLED'),
(3, 1, NULL, 'C', 'LOFT_BED_DESK', 3, 'ENABLED'),
(4, 1, NULL, 'D', 'LOFT_BED_DESK', 4, 'ENABLED'),
-- 男生102
(5, 2, NULL, 'A', 'LOFT_BED_DESK', 1, 'ENABLED'),
(6, 2, NULL, 'B', 'LOFT_BED_DESK', 2, 'ENABLED'),
(7, 2, NULL, 'C', 'LOFT_BED_DESK', 3, 'ENABLED'),
(8, 2, 1, 'D-UP', 'BUNK_UPPER', 4, 'ENABLED'),
(9, 2, 1, 'D-LOW', 'BUNK_LOWER', 5, 'ENABLED'),
-- 男生201
(10, 3, NULL, 'A', 'LOFT_BED_DESK', 1, 'ENABLED'),
(11, 3, NULL, 'B', 'LOFT_BED_DESK', 2, 'ENABLED'),
(12, 3, NULL, 'C', 'LOFT_BED_DESK', 3, 'ENABLED'),
(13, 3, NULL, 'D', 'LOFT_BED_DESK', 4, 'ENABLED'),
-- 男生202
(14, 4, NULL, 'A', 'LOFT_BED_DESK', 1, 'ENABLED'),
(15, 4, NULL, 'B', 'LOFT_BED_DESK', 2, 'ENABLED'),
(16, 4, NULL, 'C', 'LOFT_BED_DESK', 3, 'ENABLED'),
(17, 4, 2, 'D-UP', 'BUNK_UPPER', 4, 'ENABLED'),
(18, 4, 2, 'D-LOW', 'BUNK_LOWER', 5, 'ENABLED'),
-- 女生101
(19, 5, NULL, 'A', 'LOFT_BED_DESK', 1, 'ENABLED'),
(20, 5, NULL, 'B', 'LOFT_BED_DESK', 2, 'ENABLED'),
(21, 5, NULL, 'C', 'LOFT_BED_DESK', 3, 'ENABLED'),
(22, 5, NULL, 'D', 'LOFT_BED_DESK', 4, 'ENABLED'),
-- 女生102
(23, 6, NULL, 'A', 'LOFT_BED_DESK', 1, 'ENABLED'),
(24, 6, NULL, 'B', 'LOFT_BED_DESK', 2, 'ENABLED'),
(25, 6, NULL, 'C', 'LOFT_BED_DESK', 3, 'ENABLED'),
(26, 6, 3, 'D-UP', 'BUNK_UPPER', 4, 'ENABLED'),
(27, 6, 3, 'D-LOW', 'BUNK_LOWER', 5, 'ENABLED'),
-- 女生201
(28, 7, NULL, 'A', 'LOFT_BED_DESK', 1, 'ENABLED'),
(29, 7, NULL, 'B', 'LOFT_BED_DESK', 2, 'ENABLED'),
(30, 7, NULL, 'C', 'LOFT_BED_DESK', 3, 'ENABLED'),
(31, 7, NULL, 'D', 'LOFT_BED_DESK', 4, 'ENABLED'),
-- 女生202
(32, 8, NULL, 'A', 'LOFT_BED_DESK', 1, 'ENABLED'),
(33, 8, NULL, 'B', 'LOFT_BED_DESK', 2, 'ENABLED'),
(34, 8, NULL, 'C', 'LOFT_BED_DESK', 3, 'ENABLED'),
(35, 8, 4, 'D-UP', 'BUNK_UPPER', 4, 'ENABLED'),
(36, 8, 4, 'D-LOW', 'BUNK_LOWER', 5, 'ENABLED');

-- ----------------------------------------------------------------------------
-- 个人偏好题目与选项
-- ----------------------------------------------------------------------------
INSERT INTO questionnaire_version
(id, version_code, questionnaire_name, version_status, description, published_at)
VALUES
(1, 'PREFERENCE-2026-V1', '2026级学生个人偏好', 'PUBLISHED',
 '用于确定性室友匹配的个人偏好设置', CURRENT_TIMESTAMP(3));

INSERT INTO questionnaire_question
(id, questionnaire_version_id, question_code, question_text, question_type,
 feature_key, required_flag, sort_order, enabled)
VALUES
(1, 1, 'SLEEP_TIME', '通常几点入睡？', 'TIME', 'sleepTimeMinutes', 1, 1, 1),
(2, 1, 'WAKE_TIME', '通常几点起床？', 'TIME', 'wakeTimeMinutes', 1, 2, 1),
(3, 1, 'NAP_HABIT', '午休习惯如何？', 'SINGLE_CHOICE', 'napHabit', 1, 3, 1),
(4, 1, 'SLEEP_SENSITIVITY', '睡眠敏感度如何？', 'SINGLE_CHOICE', 'sleepSensitivity', 1, 4, 1),
(5, 1, 'NOISE_TOLERANCE', '可接受的宿舍噪声程度？', 'SINGLE_CHOICE', 'noiseTolerance', 1, 5, 1),
(6, 1, 'CLEANING_FREQUENCY', '打扫宿舍的频率？', 'SINGLE_CHOICE', 'cleaningFrequency', 1, 6, 1),
(7, 1, 'TIDINESS_REQUIREMENT', '对宿舍整洁程度的要求？', 'SINGLE_CHOICE', 'tidinessRequirement', 1, 7, 1),
(8, 1, 'SUMMER_AC_OVERNIGHT', '夏季是否接受整夜开启空调？', 'SINGLE_CHOICE', 'summerOvernightAirConditioner', 1, 8, 1),
(9, 1, 'SUMMER_AC_TEMPERATURE', '夏季制冷偏好温度？', 'INTEGER', 'summerAirConditionerTemperature', 1, 9, 1),
(10, 1, 'WINTER_HEATING_ACCEPTANCE', '冬季是否接受空调制热？', 'SINGLE_CHOICE', 'winterHeatingAcceptance', 1, 10, 1),
(11, 1, 'WINTER_HEATING_TEMPERATURE', '冬季制热偏好温度？', 'INTEGER', 'winterHeatingTemperature', 0, 11, 1),
(12, 1, 'AFTER_LIGHTS_ACTIVITY', '熄灯后通常保持何种活动状态？', 'SINGLE_CHOICE', 'afterLightsActivity', 1, 12, 1),
(13, 1, 'ALARM_SNOOZE', '闹钟通常会响几次？', 'SINGLE_CHOICE', 'alarmSnooze', 1, 13, 1),
(14, 1, 'STRONG_FOOD_ODOR_ACCEPTANCE', '是否接受重气味食物？', 'SINGLE_CHOICE', 'strongFoodOdorAcceptance', 1, 14, 1),
(15, 1, 'VENTILATION', '通风偏好如何？', 'SINGLE_CHOICE', 'ventilation', 1, 15, 1),
(16, 1, 'STUDY_FREQUENCY', '在宿舍学习的频率？', 'SINGLE_CHOICE', 'studyFrequency', 1, 16, 1),
(17, 1, 'GAMING_VOICE', '游戏或语音聊天频率？', 'SINGLE_CHOICE', 'gamingVoiceFrequency', 1, 17, 1),
(18, 1, 'SOCIAL_ACTIVITY', '宿舍社交活跃度？', 'SINGLE_CHOICE', 'socialActivity', 1, 18, 1),
(19, 1, 'SMOKING_ACCEPTANCE', '是否接受室友吸烟？', 'SINGLE_CHOICE', 'smokingAcceptance', 1, 19, 1),
(20, 1, 'BED_PREFERENCE', '床位类型偏好？', 'SINGLE_CHOICE', 'bedPreference', 1, 20, 1);

-- 通用五级选项。
INSERT INTO questionnaire_option
(question_id, option_code, option_text, feature_value, sort_order, enabled)
SELECT q.id, level.option_code, level.option_text, level.feature_value, level.sort_order, 1
FROM questionnaire_question q
JOIN (
    SELECT 'LEVEL_1' option_code, '非常低' option_text, 1.0000 feature_value, 1 sort_order
    UNION ALL SELECT 'LEVEL_2', '较低', 2.0000, 2
    UNION ALL SELECT 'LEVEL_3', '适中', 3.0000, 3
    UNION ALL SELECT 'LEVEL_4', '较高', 4.0000, 4
    UNION ALL SELECT 'LEVEL_5', '非常高', 5.0000, 5
) level
WHERE q.question_code IN (
    'SLEEP_SENSITIVITY','NOISE_TOLERANCE','CLEANING_FREQUENCY',
    'TIDINESS_REQUIREMENT','VENTILATION','STUDY_FREQUENCY',
    'GAMING_VOICE','SOCIAL_ACTIVITY'
);

INSERT INTO questionnaire_option
(question_id, option_code, option_text, feature_value, sort_order, enabled)
SELECT q.id, item.option_code, item.option_text, item.feature_value, item.sort_order, 1
FROM questionnaire_question q
JOIN (
    SELECT 'NAP_HABIT' question_code, 'NONE' option_code, '基本不午休' option_text, 0.0000 feature_value, 1 sort_order
    UNION ALL SELECT 'NAP_HABIT','SOMETIMES','偶尔午休',1.0000,2
    UNION ALL SELECT 'NAP_HABIT','OFTEN','经常午休',2.0000,3
    UNION ALL SELECT 'SUMMER_AC_OVERNIGHT','REJECT','不接受整夜开启',1.0000,1
    UNION ALL SELECT 'SUMMER_AC_OVERNIGHT','TIMER','可以定时开启',2.0000,2
    UNION ALL SELECT 'SUMMER_AC_OVERNIGHT','ACCEPT','接受整夜开启',3.0000,3
    UNION ALL SELECT 'SUMMER_AC_OVERNIGHT','ANY','不在意',2.0000,4
    UNION ALL SELECT 'WINTER_HEATING_ACCEPTANCE','REJECT','不接受制热',1.0000,1
    UNION ALL SELECT 'WINTER_HEATING_ACCEPTANCE','ACCEPT','接受制热',3.0000,2
    UNION ALL SELECT 'WINTER_HEATING_ACCEPTANCE','ANY','不在意',2.0000,3
    UNION ALL SELECT 'AFTER_LIGHTS_ACTIVITY','DARK_SILENT','保持黑暗和安静',1.0000,1
    UNION ALL SELECT 'AFTER_LIGHTS_ACTIVITY','DESK_LAMP_HEADPHONES','使用台灯和耳机',2.0000,2
    UNION ALL SELECT 'AFTER_LIGHTS_ACTIVITY','NORMAL_ACTIVITY','仍会正常活动',3.0000,3
    UNION ALL SELECT 'AFTER_LIGHTS_ACTIVITY','ANY','不在意',2.0000,4
    UNION ALL SELECT 'ALARM_SNOOZE','ONCE','通常一次起床',1.0000,1
    UNION ALL SELECT 'ALARM_SNOOZE','SOMETIMES','偶尔重复一次',2.0000,2
    UNION ALL SELECT 'ALARM_SNOOZE','REPEATED','经常多次响铃',3.0000,3
    UNION ALL SELECT 'STRONG_FOOD_ODOR_ACCEPTANCE','REJECT','不接受',1.0000,1
    UNION ALL SELECT 'STRONG_FOOD_ODOR_ACCEPTANCE','OCCASIONAL','偶尔可以',2.0000,2
    UNION ALL SELECT 'STRONG_FOOD_ODOR_ACCEPTANCE','ACCEPT','可以接受',3.0000,3
    UNION ALL SELECT 'STRONG_FOOD_ODOR_ACCEPTANCE','ANY','不在意',2.0000,4
    UNION ALL SELECT 'SMOKING_ACCEPTANCE','ACCEPT','接受',1.0000,1
    UNION ALL SELECT 'SMOKING_ACCEPTANCE','REJECT','不接受',0.0000,2
    UNION ALL SELECT 'SMOKING_ACCEPTANCE','ANY','均可',0.5000,3
    UNION ALL SELECT 'BED_PREFERENCE','ANY','无特别偏好',NULL,1
    UNION ALL SELECT 'BED_PREFERENCE','LOFT_BED_DESK','上床下桌',NULL,2
    UNION ALL SELECT 'BED_PREFERENCE','BUNK_UPPER','上下铺上铺',NULL,3
    UNION ALL SELECT 'BED_PREFERENCE','BUNK_LOWER','上下铺下铺',NULL,4
) item ON item.question_code=q.question_code;

-- ----------------------------------------------------------------------------
-- 匹配规则、选寝活动与范围
-- ----------------------------------------------------------------------------
INSERT INTO matching_weight_scheme
(id, scheme_code, scheme_name, revision, algorithm_version,
 weights_json, conflict_rules_json, enabled, version,
 created_by, change_reason, published_at)
VALUES
(1, 'DEFAULT', '默认个人偏好匹配方案', 1, 'weighted-distance-v2',
 JSON_OBJECT(
   'sleepTimeMinutes',1.2,'wakeTimeMinutes',1.0,'sleepSensitivity',1.2,
   'noiseTolerance',1.2,'cleaningFrequency',1.0,'tidinessRequirement',1.0,
   'summerAirConditionerTemperature',0.8,'summerOvernightAirConditioner',1.1,
   'winterHeatingAcceptance',0.8,'winterHeatingTemperature',0.6,
   'afterLightsActivity',1.2,'alarmSnooze',0.9,'strongFoodOdorAcceptance',0.7,
   'studyFrequency',0.8,'gamingVoiceFrequency',1.1,'socialActivity',0.6
 ),
 JSON_OBJECT(
   'smokingConflictPenalty',25,'sleepTimeWarningMinutes',60,
   'cleaningWarningDifference',1,'gamingVoiceWarningDifference',1
 ),
 1, 0, @admin_id, '测试数据初始化', CURRENT_TIMESTAMP(3));

INSERT INTO selection_batch
(id, batch_code, batch_name, batch_status,
 questionnaire_version_id, matching_weight_scheme_id,
 start_at, end_at, hold_duration_seconds, hold_renewal_limit,
 allow_team, team_min_size, team_max_size, allow_student_random,
 unselected_strategy, rule_version, created_by, published_at, version)
VALUES
(1, 'TEST-2026-AUTUMN', '2026级新生测试选寝', 'OPEN',
 1, 1,
 DATE_SUB(CURRENT_TIMESTAMP(3), INTERVAL 1 DAY),
 DATE_ADD(CURRENT_TIMESTAMP(3), INTERVAL 30 DAY),
 300, 1, 1, 2, 5, 1,
 'ADMIN_ALLOCATION', 'test-rule-v1', @admin_id,
 CURRENT_TIMESTAMP(3), 0);

INSERT INTO batch_student_eligibility
(batch_id, student_id, eligibility_status)
SELECT 1, id, 'ELIGIBLE' FROM student;

INSERT INTO active_batch_student_lock
(student_id, batch_id)
SELECT id, 1 FROM student;

INSERT INTO batch_building_scope
(batch_id, building_id)
SELECT 1, id FROM dormitory_building;

INSERT INTO batch_room_scope
(batch_id, room_id)
SELECT 1, id FROM room;

INSERT INTO batch_bed_scope
(batch_id, bed_id)
SELECT 1, id FROM bed;

INSERT INTO system_setting
(setting_key, setting_value, version, updated_by)
VALUES
('STUDENT_WELCOME_MESSAGE',
 JSON_OBJECT(
   'zh-CN','新同学，欢迎你！请先完成个人偏好，再选择适合自己的宿舍和床位。',
   'en-US','Welcome, new student! Complete your personal preferences first, then choose a suitable room and bed.'
 ),
 0, @admin_id);

-- ----------------------------------------------------------------------------
-- 结果校验
-- ----------------------------------------------------------------------------
DROP PROCEDURE IF EXISTS assert_reset_test_data;
DELIMITER $$
CREATE PROCEDURE assert_reset_test_data()
BEGIN
    DECLARE admin_total INT;
    DECLARE student_total INT;
    DECLARE invalid_student_numbers INT;
    DECLARE international_total INT;
    DECLARE bed_total INT;

    SELECT COUNT(*) INTO admin_total
    FROM app_user WHERE username='admin' AND user_type='ADMIN';
    SELECT COUNT(*) INTO student_total FROM student;
    SELECT COUNT(*) INTO invalid_student_numbers
    FROM student WHERE student_number NOT REGEXP '^[0-9]{12}$';
    SELECT COUNT(*) INTO international_total
    FROM student WHERE nationality_code <> 'CN';
    SELECT COUNT(*) INTO bed_total FROM bed;

    IF admin_total <> 1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='管理员账号恢复校验失败';
    END IF;
    IF student_total <> 20 OR invalid_student_numbers <> 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='学生测试数据或12位学号校验失败';
    END IF;
    IF international_total < 8 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='国际学生测试数据不足';
    END IF;
    IF bed_total <> 36 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='宿舍床位测试数据校验失败';
    END IF;
END$$
DELIMITER ;
CALL assert_reset_test_data();
DROP PROCEDURE assert_reset_test_data;

SELECT
    (SELECT COUNT(*) FROM student) AS student_count,
    (SELECT COUNT(*) FROM student WHERE nationality_code<>'CN') AS international_student_count,
    (SELECT COUNT(*) FROM room) AS room_count,
    (SELECT COUNT(*) FROM bed) AS bed_count,
    (SELECT COUNT(*) FROM selection_batch WHERE batch_status='OPEN') AS open_batch_count;
