-- ============================================================================
-- 武汉科技大学学生宿舍智能选择系统：V14全量测试数据脚本
--
-- 使用方式：
--   mysql -u<user> -p <database> < backend-java/docs/sql/reset_and_seed_test_data.sql
--
-- 前置条件：
-- 1. 数据库结构必须已经执行至Flyway V14，或已经导入最新schema.sql；
-- 2. 本脚本只重置和生成测试业务数据，不创建数据库结构；
-- 3. 保留V12-V14的平台功能目录、配额目录、套餐和稳定订阅主记录；
-- 4. 保留SYSTEM_ADMIN和已有业务ADMIN账号；若admin不存在则创建本地测试管理员；
-- 5. 生成500名学生：男生250、女生250，中国学生400、国际学生100；
-- 6. 生成100个五人间和500个床位，正常情况下全部学生均有床位可分配；
-- 7. 生成个人偏好、组队、通知、部分已有住宿分配和批次授权快照。
--
-- 警告：只能用于开发或测试数据库，禁止在生产数据库执行。
-- ============================================================================

SET NAMES utf8mb4;
SET @database_name = DATABASE();

-- ----------------------------------------------------------------------------
-- 前置结构校验：V14必须存在SYSTEM_ADMIN扩展和订阅基础表。
-- ----------------------------------------------------------------------------
DROP PROCEDURE IF EXISTS assert_v14_schema;
DELIMITER $$
CREATE PROCEDURE assert_v14_schema()
BEGIN
    DECLARE password_column_count INT;
    DECLARE subscription_table_count INT;
    DECLARE latest_version INT;

    SELECT COUNT(*) INTO password_column_count
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'app_user'
      AND column_name = 'password_change_required';

    SELECT COUNT(*) INTO subscription_table_count
    FROM information_schema.tables
    WHERE table_schema = DATABASE()
      AND table_name IN (
        'feature_catalog', 'quota_catalog', 'subscription_plan',
        'subscription_plan_revision', 'service_subscription',
        'service_subscription_revision', 'batch_entitlement_snapshot'
      );

    IF EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = DATABASE() AND table_name = 'flyway_schema_history'
    ) THEN
        SELECT MAX(CAST(version AS UNSIGNED)) INTO latest_version
        FROM flyway_schema_history
        WHERE success = 1 AND version IS NOT NULL;
        IF latest_version < 14 THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = '数据库Flyway版本低于V14，请先执行正式迁移';
        END IF;
    END IF;

    IF password_column_count <> 1 OR subscription_table_count <> 7 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '数据库结构不是V14，请先导入最新schema.sql或执行Flyway V1-V14';
    END IF;
END$$
DELIMITER ;
CALL assert_v14_schema();
DROP PROCEDURE assert_v14_schema;

-- ----------------------------------------------------------------------------
-- 保存管理员和系统管理员。生成列system_admin_marker不写入临时表。
-- ----------------------------------------------------------------------------
DROP TEMPORARY TABLE IF EXISTS preserved_privileged_accounts;
CREATE TEMPORARY TABLE preserved_privileged_accounts AS
SELECT id, student_id, username, password_hash, user_type, account_status,
       display_name, last_login_at, welcome_acknowledged_at,
       password_change_required, version, created_at, updated_at
FROM app_user
WHERE user_type IN ('ADMIN', 'SYSTEM_ADMIN');

-- ----------------------------------------------------------------------------
-- 清空可变业务数据。
-- 平台功能目录、配额目录、套餐修订、稳定订阅主记录和批次规则模板保留。
-- ----------------------------------------------------------------------------
DROP PROCEDURE IF EXISTS clear_mutable_test_data;
DELIMITER $$
CREATE PROCEDURE clear_mutable_test_data()
BEGIN
    DECLARE finished INT DEFAULT 0;
    DECLARE target_table VARCHAR(128);
    DECLARE table_cursor CURSOR FOR
        SELECT table_name
        FROM information_schema.tables
        WHERE table_schema = @database_name
          AND table_type = 'BASE TABLE'
          AND table_name NOT IN (
              'flyway_schema_history',
              'feature_catalog',
              'quota_catalog',
              'subscription_plan',
              'subscription_plan_revision',
              'plan_revision_feature',
              'plan_revision_quota',
              'service_subscription',
              'batch_rule_template'
          )
        ORDER BY table_name;
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET finished = 1;

    SET FOREIGN_KEY_CHECKS = 0;
    OPEN table_cursor;
    table_loop: LOOP
        FETCH table_cursor INTO target_table;
        IF finished = 1 THEN
            LEAVE table_loop;
        END IF;
        SET @delete_sql = CONCAT(
            'DELETE FROM `', REPLACE(target_table, '`', '``'), '`'
        );
        PREPARE delete_statement FROM @delete_sql;
        EXECUTE delete_statement;
        DEALLOCATE PREPARE delete_statement;
    END LOOP;
    CLOSE table_cursor;
    SET FOREIGN_KEY_CHECKS = 1;
END$$
DELIMITER ;
CALL clear_mutable_test_data();
DROP PROCEDURE clear_mutable_test_data;

-- 恢复特权账号。
INSERT INTO app_user
(id, student_id, username, password_hash, user_type, account_status,
 display_name, last_login_at, welcome_acknowledged_at,
 password_change_required, version, created_at, updated_at)
SELECT id, student_id, username, password_hash, user_type, account_status,
       display_name, last_login_at, welcome_acknowledged_at,
       password_change_required, version, created_at, updated_at
FROM preserved_privileged_accounts;
DROP TEMPORARY TABLE preserved_privileged_accounts;

-- 如果业务管理员不存在，创建仅用于本地测试的管理员。
INSERT INTO app_user
(student_id, username, password_hash, user_type, account_status,
 display_name, password_change_required)
SELECT NULL, 'admin', '{noop}Dormitory@2026', 'ADMIN', 'ACTIVE',
       '测试管理员', 0
WHERE NOT EXISTS (
    SELECT 1 FROM app_user WHERE username = 'admin' AND user_type = 'ADMIN'
);

SET @admin_id = (
    SELECT id FROM app_user
    WHERE username = 'admin' AND user_type = 'ADMIN'
    ORDER BY id LIMIT 1
);
SET @system_admin_id = (
    SELECT id FROM app_user
    WHERE user_type = 'SYSTEM_ADMIN'
    ORDER BY id LIMIT 1
);
SET @plan_revision_id = (
    SELECT revision_record.id
    FROM subscription_plan_revision revision_record
    JOIN subscription_plan plan_record ON plan_record.id = revision_record.plan_id
    WHERE plan_record.plan_code = 'FULL_CURRENT'
      AND revision_record.revision = 1
    LIMIT 1
);
SET @service_subscription_id = (
    SELECT id FROM service_subscription
    WHERE subscription_code = 'PRIMARY_SERVICE'
    LIMIT 1
);
SET @rule_template_id = (
    SELECT id FROM batch_rule_template
    WHERE enabled = 1 AND is_default = 1
    ORDER BY revision DESC LIMIT 1
);

DROP PROCEDURE IF EXISTS assert_platform_seed;
DELIMITER $$
CREATE PROCEDURE assert_platform_seed()
BEGIN
    IF @admin_id IS NULL THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '测试管理员创建失败';
    END IF;
    IF @system_admin_id IS NULL THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'V13系统管理员不存在';
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM app_user
        WHERE id = @system_admin_id
          AND password_hash LIKE '{bcrypt}$2%'
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'SYSTEM_ADMIN密码尚未经过V14编码修复';
    END IF;
    IF @plan_revision_id IS NULL OR @service_subscription_id IS NULL THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'V13默认套餐或订阅不存在';
    END IF;
    IF @rule_template_id IS NULL THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'V10默认批次规则模板不存在';
    END IF;
END$$
DELIMITER ;
CALL assert_platform_seed();
DROP PROCEDURE assert_platform_seed;

-- 重建确定性的当前长期订阅修订。
INSERT INTO service_subscription_revision
(subscription_id, revision, plan_revision_id, subscription_type, service_status,
 contract_number, start_at, end_at, signed_at, emergency_stopped,
 change_reason, remark, is_current, created_by)
VALUES
(@service_subscription_id, 1, @plan_revision_id, 'LONG_TERM', 'ACTIVE',
 'LOCAL-TEST-500', CURRENT_TIMESTAMP(3), NULL, CURRENT_TIMESTAMP(3), 0,
 'V14测试数据初始化', '500人中外学生混合测试环境', 1, @system_admin_id);

INSERT INTO platform_audit_log
(operation_type, operator_user_id, target_type, target_id,
 change_reason, after_json, success)
VALUES
('SUBSCRIPTION_CREATE', @system_admin_id, 'SERVICE_SUBSCRIPTION',
 CAST(@service_subscription_id AS CHAR), 'V14测试数据初始化',
 JSON_OBJECT('serviceStatus','ACTIVE','planCode','FULL_CURRENT','studentScale',500), 1);

-- ----------------------------------------------------------------------------
-- 基础目录。
-- ----------------------------------------------------------------------------
INSERT INTO campus
(id, campus_code, campus_name, address, enabled)
VALUES
(1, 'HQ', '黄家湖校区', '湖北省武汉市黄家湖西路', 1);

INSERT INTO major
(id, major_code, major_name, enabled)
VALUES
(1, 'M001', '计算机科学与技术', 1),
(2, 'M002', '软件工程', 1),
(3, 'M003', '土木工程', 1),
(4, 'M004', '机械工程', 1),
(5, 'M005', '国际经济与贸易', 1),
(6, 'M006', '自动化', 1),
(7, 'M007', '材料科学与工程', 1),
(8, 'M008', '临床医学', 1),
(9, 'M009', '工商管理', 1),
(10, 'M010', '建筑学', 1);

-- ----------------------------------------------------------------------------
-- 学生：500人；男女各250人；中国学生400人；国际学生100人。
-- 账号状态混合：150个ACTIVE、350个PENDING。
-- ----------------------------------------------------------------------------
DROP PROCEDURE IF EXISTS seed_students;
DELIMITER $$
CREATE PROCEDURE seed_students()
BEGIN
    DECLARE i INT DEFAULT 1;
    DECLARE gender_value CHAR(1);
    DECLARE nationality_value CHAR(2);
    DECLARE student_name_value VARCHAR(128);
    DECLARE phone_value VARCHAR(32);
    DECLARE account_status_value VARCHAR(32);

    WHILE i <= 500 DO
        SET gender_value = IF(i <= 250, 'M', 'F');
        SET nationality_value = CASE
            WHEN i <= 400 THEN 'CN'
            ELSE ELT(MOD(i - 401, 10) + 1,
                     'US','GB','JP','KR','FR','DE','RU','IN','EG','TH')
        END;
        SET student_name_value = CASE
            WHEN nationality_value = 'CN'
                THEN CONCAT(IF(gender_value='M','男生测试','女生测试'), LPAD(i, 4, '0'))
            ELSE CONCAT(
                ELT(MOD(i - 401, 10) + 1,
                    'Alex','Emma','Haruto','Min-jun','Camille',
                    'Lukas','Ivan','Ananya','Ahmed','Narin'),
                ' Student ', LPAD(i, 4, '0')
            )
        END;
        SET phone_value = CASE
            WHEN nationality_value = 'CN'
                THEN CONCAT('+86 13', MOD(i, 10), ' ', LPAD(i, 4, '0'), ' ', LPAD(i, 4, '0'))
            ELSE CONCAT('+', 20 + MOD(i, 80), ' ', LPAD(10000000 + i, 8, '0'))
        END;
        SET account_status_value = IF(MOD(i - 1, 10) < 3, 'ACTIVE', 'PENDING');

        INSERT INTO student
        (id, student_number, student_name, gender, major_id,
         nationality_code, phone_number)
        VALUES
        (i, CONCAT('2026', LPAD(i, 8, '0')), student_name_value,
         gender_value, 1 + MOD(i - 1, 10), nationality_value, phone_value);

        INSERT INTO app_user
        (student_id, username, password_hash, user_type, account_status,
         display_name, last_login_at, welcome_acknowledged_at,
         password_change_required, version)
        VALUES
        (i, CONCAT('2026', LPAD(i, 8, '0')),
         IF(account_status_value='ACTIVE', '{noop}Student@2026', NULL),
         'STUDENT', account_status_value, student_name_value,
         IF(account_status_value='ACTIVE', DATE_SUB(CURRENT_TIMESTAMP(3), INTERVAL MOD(i, 30) DAY), NULL),
         IF(account_status_value='ACTIVE', CURRENT_TIMESTAMP(3), NULL),
         0, 0);

        SET i = i + 1;
    END WHILE;
END$$
DELIMITER ;
CALL seed_students();
DROP PROCEDURE seed_students;

-- ----------------------------------------------------------------------------
-- 宿舍资源：2栋、10层、100个五人间、500个床位。
-- 每间房包含3张上床下桌和1组上下铺。
-- ----------------------------------------------------------------------------
INSERT INTO dormitory_building
(id, campus_id, building_code, building_name, gender_restriction, enabled)
VALUES
(1, 1, 'M-A', '男生测试宿舍', 'M', 1),
(2, 1, 'F-A', '女生测试宿舍', 'F', 1);

DROP PROCEDURE IF EXISTS seed_dormitory_resources;
DELIMITER $$
CREATE PROCEDURE seed_dormitory_resources()
BEGIN
    DECLARE building_index INT DEFAULT 1;
    DECLARE floor_index INT;
    DECLARE room_index INT;
    DECLARE floor_id_value INT;
    DECLARE room_id_value INT;
    DECLARE bed_base INT;
    DECLARE gender_value CHAR(1);

    WHILE building_index <= 2 DO
        SET gender_value = IF(building_index = 1, 'M', 'F');
        SET floor_index = 1;
        WHILE floor_index <= 5 DO
            SET floor_id_value = (building_index - 1) * 5 + floor_index;
            INSERT INTO dormitory_floor
            (id, building_id, floor_number, floor_name, enabled)
            VALUES
            (floor_id_value, building_index, floor_index,
             CONCAT(IF(building_index=1,'男生','女生'), '测试宿舍', floor_index, '层'), 1);

            SET room_index = 1;
            WHILE room_index <= 10 DO
                SET room_id_value = (building_index - 1) * 50
                                  + (floor_index - 1) * 10 + room_index;
                SET bed_base = (room_id_value - 1) * 5;

                INSERT INTO room
                (id, floor_id, room_number, room_type, capacity,
                 gender_restriction, operational_status, state_version, remark, version)
                VALUES
                (room_id_value, floor_id_value,
                 CONCAT(floor_index, LPAD(room_index, 2, '0')),
                 'FIVE_PERSON', 5, gender_value, 'ENABLED', 0,
                 '三张上床下桌加一组上下铺', 0);

                INSERT INTO bed_frame
                (id, room_id, frame_code, frame_type, enabled)
                VALUES
                (room_id_value, room_id_value,
                 CONCAT('BF-', room_id_value, '-D'), 'BUNK_FRAME', 1);

                INSERT INTO bed
                (id, room_id, bed_frame_id, bed_code, bed_type,
                 position_index, operational_status)
                VALUES
                (bed_base + 1, room_id_value, NULL, 'A', 'LOFT_BED_DESK', 1, 'ENABLED'),
                (bed_base + 2, room_id_value, NULL, 'B', 'LOFT_BED_DESK', 2, 'ENABLED'),
                (bed_base + 3, room_id_value, NULL, 'C', 'LOFT_BED_DESK', 3, 'ENABLED'),
                (bed_base + 4, room_id_value, room_id_value, 'D-UP', 'BUNK_UPPER', 4, 'ENABLED'),
                (bed_base + 5, room_id_value, room_id_value, 'D-LOW', 'BUNK_LOWER', 5, 'ENABLED');

                INSERT INTO room_bed_layout
                (bed_id, layout_x, layout_z, rotation_degrees, updated_by, version)
                VALUES
                (bed_base + 1, -3.300, -1.700, 90, @admin_id, 0),
                (bed_base + 2,  0.000, -1.700, 90, @admin_id, 0),
                (bed_base + 3,  3.300, -1.700, 90, @admin_id, 0),
                (bed_base + 4, -1.650,  1.700, 90, @admin_id, 0),
                (bed_base + 5, -1.650,  1.700, 90, @admin_id, 0);

                SET room_index = room_index + 1;
            END WHILE;
            SET floor_index = floor_index + 1;
        END WHILE;
        SET building_index = building_index + 1;
    END WHILE;
END$$
DELIMITER ;
CALL seed_dormitory_resources();
DROP PROCEDURE seed_dormitory_resources;

-- ----------------------------------------------------------------------------
-- 个人偏好题目与选项。
-- ----------------------------------------------------------------------------
INSERT INTO questionnaire_version
(id, version_code, questionnaire_name, version_status, description, published_at)
VALUES
(1, 'PREFERENCE-2026-V14', '2026级学生个人偏好', 'PUBLISHED',
 '500人测试数据使用的个人偏好版本', CURRENT_TIMESTAMP(3));

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

INSERT INTO questionnaire_option
(question_id, option_code, option_text, feature_value, sort_order, enabled)
SELECT q.id, level.option_code, level.option_text,
       level.feature_value, level.sort_order, 1
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
SELECT q.id, item.option_code, item.option_text,
       item.feature_value, item.sort_order, 1
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
) item ON item.question_code = q.question_code;

-- ----------------------------------------------------------------------------
-- 匹配规则、选寝批次与功能授权快照。
-- ----------------------------------------------------------------------------
INSERT INTO matching_weight_scheme
(id, scheme_code, scheme_name, revision, algorithm_version,
 weights_json, conflict_rules_json, enabled, version,
 created_by, change_reason, published_at)
VALUES
(1, 'DEFAULT', '500人测试默认匹配方案', 1, 'weighted-distance-v2',
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
 1, 0, @admin_id, 'V14测试数据初始化', CURRENT_TIMESTAMP(3));

INSERT INTO selection_batch
(id, batch_code, batch_name, batch_status,
 questionnaire_version_id, matching_weight_scheme_id, rule_template_id,
 start_at, end_at, hold_duration_seconds, hold_renewal_limit,
 allow_team, team_min_size, team_max_size, allow_student_random,
 unselected_strategy, rule_version, created_by, published_at, version)
VALUES
(1, 'TEST-2026-500', '2026级500人测试选寝', 'OPEN',
 1, 1, @rule_template_id,
 DATE_SUB(CURRENT_TIMESTAMP(3), INTERVAL 1 DAY),
 DATE_ADD(CURRENT_TIMESTAMP(3), INTERVAL 30 DAY),
 300, 1, 1, 2, 5, 1,
 'ADMIN_ALLOCATION', 'phase2-rule-template-v1', @admin_id,
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
   'zh-CN','新同学，欢迎你！当前是500人测试环境，请先完成个人偏好，再选择合适的宿舍和床位。',
   'en-US','Welcome! This is a 500-student test environment. Complete your preferences, then choose a room and bed.'
 ),
 0, @admin_id);

INSERT INTO batch_entitlement_snapshot
(batch_id, subscription_revision_id, granted_features_json,
 quota_snapshot_json, snapshot_version)
SELECT
    1,
    subscription_revision.id,
    (
        SELECT JSON_ARRAYAGG(plan_feature.feature_code)
        FROM plan_revision_feature plan_feature
        WHERE plan_feature.plan_revision_id = subscription_revision.plan_revision_id
    ),
    (
        SELECT JSON_OBJECTAGG(plan_quota.quota_code, plan_quota.quota_value)
        FROM plan_revision_quota plan_quota
        WHERE plan_quota.plan_revision_id = subscription_revision.plan_revision_id
    ),
    'entitlement-v1'
FROM service_subscription_revision subscription_revision
WHERE subscription_revision.subscription_id = @service_subscription_id
  AND subscription_revision.is_current = 1
LIMIT 1;

-- ----------------------------------------------------------------------------
-- 为前450名学生生成完整个人偏好和匹配画像；后50名保留未填写状态。
-- ----------------------------------------------------------------------------
DROP PROCEDURE IF EXISTS seed_preferences;
DELIMITER $$
CREATE PROCEDURE seed_preferences()
BEGIN
    DECLARE i INT DEFAULT 1;
    DECLARE level_value INT;
    WHILE i <= 450 DO
        SET level_value = 1 + MOD(i - 1, 5);

        INSERT INTO questionnaire_answer
        (batch_id, questionnaire_version_id, student_id, question_id,
         answer_json, submitted_at, version)
        VALUES
        (1,1,i,1, JSON_QUOTE(CONCAT(LPAD(22 + MOD(i,3),2,'0'),':00')), CURRENT_TIMESTAMP(3),0),
        (1,1,i,2, JSON_QUOTE(CONCAT(LPAD(6 + MOD(i,3),2,'0'),':30')), CURRENT_TIMESTAMP(3),0),
        (1,1,i,3, JSON_QUOTE(ELT(1 + MOD(i,3),'NONE','SOMETIMES','OFTEN')), CURRENT_TIMESTAMP(3),0),
        (1,1,i,4, JSON_EXTRACT(JSON_ARRAY(level_value),'$[0]'), CURRENT_TIMESTAMP(3),0),
        (1,1,i,5, JSON_EXTRACT(JSON_ARRAY(1 + MOD(i+1,5)),'$[0]'), CURRENT_TIMESTAMP(3),0),
        (1,1,i,6, JSON_EXTRACT(JSON_ARRAY(1 + MOD(i+2,5)),'$[0]'), CURRENT_TIMESTAMP(3),0),
        (1,1,i,7, JSON_EXTRACT(JSON_ARRAY(1 + MOD(i+3,5)),'$[0]'), CURRENT_TIMESTAMP(3),0),
        (1,1,i,8, JSON_QUOTE(ELT(1 + MOD(i,4),'REJECT','TIMER','ACCEPT','ANY')), CURRENT_TIMESTAMP(3),0),
        (1,1,i,9, JSON_EXTRACT(JSON_ARRAY(24 + MOD(i,4)),'$[0]'), CURRENT_TIMESTAMP(3),0),
        (1,1,i,10, JSON_QUOTE(ELT(1 + MOD(i,3),'REJECT','ACCEPT','ANY')), CURRENT_TIMESTAMP(3),0),
        (1,1,i,11, JSON_EXTRACT(JSON_ARRAY(20 + MOD(i,5)),'$[0]'), CURRENT_TIMESTAMP(3),0),
        (1,1,i,12, JSON_QUOTE(ELT(1 + MOD(i,4),'DARK_SILENT','DESK_LAMP_HEADPHONES','NORMAL_ACTIVITY','ANY')), CURRENT_TIMESTAMP(3),0),
        (1,1,i,13, JSON_QUOTE(ELT(1 + MOD(i,3),'ONCE','SOMETIMES','REPEATED')), CURRENT_TIMESTAMP(3),0),
        (1,1,i,14, JSON_QUOTE(ELT(1 + MOD(i,4),'REJECT','OCCASIONAL','ACCEPT','ANY')), CURRENT_TIMESTAMP(3),0),
        (1,1,i,15, JSON_EXTRACT(JSON_ARRAY(1 + MOD(i,5)),'$[0]'), CURRENT_TIMESTAMP(3),0),
        (1,1,i,16, JSON_EXTRACT(JSON_ARRAY(1 + MOD(i+1,5)),'$[0]'), CURRENT_TIMESTAMP(3),0),
        (1,1,i,17, JSON_EXTRACT(JSON_ARRAY(1 + MOD(i+2,5)),'$[0]'), CURRENT_TIMESTAMP(3),0),
        (1,1,i,18, JSON_EXTRACT(JSON_ARRAY(1 + MOD(i+3,5)),'$[0]'), CURRENT_TIMESTAMP(3),0),
        (1,1,i,19, JSON_QUOTE(ELT(1 + MOD(i,3),'ACCEPT','REJECT','ANY')), CURRENT_TIMESTAMP(3),0),
        (1,1,i,20, JSON_QUOTE(ELT(1 + MOD(i,4),'ANY','LOFT_BED_DESK','BUNK_UPPER','BUNK_LOWER')), CURRENT_TIMESTAMP(3),0);

        INSERT INTO student_feature
        (batch_id, student_id, algorithm_version,
         feature_vector_json, explanation_tags_json,
         calculated_at, source_answer_version)
        VALUES
        (1, i, 'feature-v2',
         JSON_OBJECT(
           'sleepTimeMinutes', (22 + MOD(i,3)) * 60,
           'wakeTimeMinutes', (6 + MOD(i,3)) * 60 + 30,
           'sleepSensitivity', level_value,
           'noiseTolerance', 1 + MOD(i+1,5),
           'cleaningFrequency', 1 + MOD(i+2,5),
           'tidinessRequirement', 1 + MOD(i+3,5),
           'summerOvernightAirConditioner', 1 + MOD(i,3),
           'summerAirConditionerTemperature', 24 + MOD(i,4),
           'winterHeatingAcceptance', 1 + MOD(i,3),
           'winterHeatingTemperature', 20 + MOD(i,5),
           'afterLightsActivity', 1 + MOD(i,3),
           'alarmSnooze', 1 + MOD(i,3),
           'strongFoodOdorAcceptance', 1 + MOD(i,3),
           'studyFrequency', 1 + MOD(i+1,5),
           'gamingVoiceFrequency', 1 + MOD(i+2,5),
           'socialActivity', 1 + MOD(i+3,5),
           'smokingAcceptance', ELT(1 + MOD(i,3),'ACCEPT','REJECT','ANY')
         ),
         JSON_ARRAY(
           IF(level_value >= 4, '睡眠较敏感', '作息适应度较高'),
           IF(MOD(i,2)=0, '重视整洁', '社交适中')
         ),
         CURRENT_TIMESTAMP(3), 1);

        SET i = i + 1;
    END WHILE;
END$$
DELIMITER ;
CALL seed_preferences();
DROP PROCEDURE seed_preferences;

-- ----------------------------------------------------------------------------
-- 组队样例：10个已锁定五人队，10个组建中五人队。
-- ----------------------------------------------------------------------------
DROP PROCEDURE IF EXISTS seed_teams;
DELIMITER $$
CREATE PROCEDURE seed_teams()
BEGIN
    DECLARE team_index INT DEFAULT 1;
    DECLARE base_student INT;
    DECLARE member_offset INT;
    DECLARE team_status_value VARCHAR(32);
    DECLARE member_status_value VARCHAR(32);

    WHILE team_index <= 20 DO
        SET base_student = (team_index - 1) * 5 + 1;
        SET team_status_value = IF(team_index <= 10, 'LOCKED', 'FORMING');

        INSERT INTO selection_team
        (id, batch_id, team_code, team_name, leader_student_id,
         team_status, locked_at, version)
        VALUES
        (team_index, 1, CONCAT('TEAM-', LPAD(team_index,3,'0')),
         CONCAT('测试小组', LPAD(team_index,2,'0')), base_student,
         team_status_value,
         IF(team_index <= 10, CURRENT_TIMESTAMP(3), NULL), 0);

        SET member_offset = 0;
        WHILE member_offset < 5 DO
            SET member_status_value = CASE
                WHEN team_index <= 10 THEN 'LOCKED'
                WHEN member_offset <= 2 THEN 'JOINED'
                ELSE 'INVITED'
            END;

            INSERT INTO selection_team_member
            (team_id, batch_id, student_id, member_role, member_status,
             joined_at, left_at)
            VALUES
            (team_index, 1, base_student + member_offset,
             IF(member_offset=0,'LEADER','MEMBER'), member_status_value,
             IF(member_status_value IN ('JOINED','LOCKED'), CURRENT_TIMESTAMP(3), NULL),
             NULL);

            IF team_index > 10 AND member_offset >= 3 THEN
                INSERT INTO team_invitation
                (team_id, inviter_student_id, invitee_student_id,
                 invitation_status, invitation_token, expires_at, responded_at)
                VALUES
                (team_index, base_student, base_student + member_offset,
                 'PENDING', UUID(), DATE_ADD(CURRENT_TIMESTAMP(3), INTERVAL 7 DAY), NULL);

                INSERT INTO student_notification
                (student_id, notification_type, title_key, message_key,
                 parameters_json, read_at)
                VALUES
                (base_student + member_offset, 'TEAM_INVITATION_CANCELLED',
                 'notification.teamInvitationPending.title',
                 'notification.teamInvitationPending.message',
                 JSON_OBJECT('leaderStudentId',base_student,'teamId',team_index), NULL);
            END IF;

            SET member_offset = member_offset + 1;
        END WHILE;

        SET team_index = team_index + 1;
    END WHILE;
END$$
DELIMITER ;
CALL seed_teams();
DROP PROCEDURE seed_teams;

-- ----------------------------------------------------------------------------
-- 50个已有分配：男生25人、女生25人；保留450名学生用于统一分配。
-- ----------------------------------------------------------------------------
INSERT INTO bed_assignment
(batch_id, student_id, bed_id, team_id, assignment_method,
 assignment_status, allocation_run_id, assigned_by, assigned_at, version)
SELECT 1, sequence_value, sequence_value, NULL, 'MANUAL_ADJUSTMENT',
       'ACTIVE', NULL, @admin_id, CURRENT_TIMESTAMP(3), 0
FROM (
    SELECT 200 + unit_value AS sequence_value
    FROM (
        SELECT 1 unit_value UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5
        UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9 UNION ALL SELECT 10
        UNION ALL SELECT 11 UNION ALL SELECT 12 UNION ALL SELECT 13 UNION ALL SELECT 14 UNION ALL SELECT 15
        UNION ALL SELECT 16 UNION ALL SELECT 17 UNION ALL SELECT 18 UNION ALL SELECT 19 UNION ALL SELECT 20
        UNION ALL SELECT 21 UNION ALL SELECT 22 UNION ALL SELECT 23 UNION ALL SELECT 24 UNION ALL SELECT 25
    ) male_sequence
    UNION ALL
    SELECT 450 + unit_value
    FROM (
        SELECT 1 unit_value UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5
        UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9 UNION ALL SELECT 10
        UNION ALL SELECT 11 UNION ALL SELECT 12 UNION ALL SELECT 13 UNION ALL SELECT 14 UNION ALL SELECT 15
        UNION ALL SELECT 16 UNION ALL SELECT 17 UNION ALL SELECT 18 UNION ALL SELECT 19 UNION ALL SELECT 20
        UNION ALL SELECT 21 UNION ALL SELECT 22 UNION ALL SELECT 23 UNION ALL SELECT 24 UNION ALL SELECT 25
    ) female_sequence
) assigned_students;

INSERT INTO assignment_history
(assignment_id, batch_id, student_id, bed_id, event_type,
 assignment_method, operator_user_id, reason,
 previous_data, current_data, occurred_at)
SELECT assignment.id, assignment.batch_id, assignment.student_id,
       assignment.bed_id, 'CREATED', assignment.assignment_method,
       @admin_id, 'V14测试数据预置住宿结果', NULL,
       JSON_OBJECT('bedId',assignment.bed_id,'method',assignment.assignment_method),
       assignment.assigned_at
FROM bed_assignment assignment;

-- ----------------------------------------------------------------------------
-- 最终校验。
-- ----------------------------------------------------------------------------
DROP PROCEDURE IF EXISTS assert_v14_test_data;
DELIMITER $$
CREATE PROCEDURE assert_v14_test_data()
BEGIN
    DECLARE admin_total INT;
    DECLARE system_admin_total INT;
    DECLARE student_total INT;
    DECLARE male_total INT;
    DECLARE female_total INT;
    DECLARE domestic_total INT;
    DECLARE international_total INT;
    DECLARE active_account_total INT;
    DECLARE pending_account_total INT;
    DECLARE invalid_student_numbers INT;
    DECLARE room_total INT;
    DECLARE bed_total INT;
    DECLARE eligibility_total INT;
    DECLARE answer_total INT;
    DECLARE feature_total INT;
    DECLARE team_total INT;
    DECLARE invitation_total INT;
    DECLARE notification_total INT;
    DECLARE assignment_total INT;
    DECLARE snapshot_total INT;

    SELECT COUNT(*) INTO admin_total
    FROM app_user WHERE username='admin' AND user_type='ADMIN';
    SELECT COUNT(*) INTO system_admin_total
    FROM app_user
    WHERE user_type='SYSTEM_ADMIN' AND password_hash LIKE '{bcrypt}$2%';
    SELECT COUNT(*) INTO student_total FROM student;
    SELECT COUNT(*) INTO male_total FROM student WHERE gender='M';
    SELECT COUNT(*) INTO female_total FROM student WHERE gender='F';
    SELECT COUNT(*) INTO domestic_total FROM student WHERE nationality_code='CN';
    SELECT COUNT(*) INTO international_total FROM student WHERE nationality_code<>'CN';
    SELECT COUNT(*) INTO active_account_total
    FROM app_user WHERE user_type='STUDENT' AND account_status='ACTIVE';
    SELECT COUNT(*) INTO pending_account_total
    FROM app_user WHERE user_type='STUDENT' AND account_status='PENDING';
    SELECT COUNT(*) INTO invalid_student_numbers
    FROM student WHERE student_number NOT REGEXP '^[0-9]{12}$';
    SELECT COUNT(*) INTO room_total FROM room;
    SELECT COUNT(*) INTO bed_total FROM bed;
    SELECT COUNT(*) INTO eligibility_total
    FROM batch_student_eligibility
    WHERE batch_id=1 AND eligibility_status='ELIGIBLE';
    SELECT COUNT(*) INTO answer_total FROM questionnaire_answer;
    SELECT COUNT(*) INTO feature_total FROM student_feature;
    SELECT COUNT(*) INTO team_total FROM selection_team;
    SELECT COUNT(*) INTO invitation_total FROM team_invitation;
    SELECT COUNT(*) INTO notification_total FROM student_notification;
    SELECT COUNT(*) INTO assignment_total FROM bed_assignment;
    SELECT COUNT(*) INTO snapshot_total FROM batch_entitlement_snapshot WHERE batch_id=1;

    IF admin_total <> 1 OR system_admin_total <> 1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='管理员或V14系统管理员校验失败';
    END IF;
    IF student_total <> 500 OR male_total <> 250 OR female_total <> 250 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='500人或男女比例校验失败';
    END IF;
    IF domestic_total <> 400 OR international_total <> 100 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='中外学生比例校验失败';
    END IF;
    IF active_account_total <> 150 OR pending_account_total <> 350 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='学生激活状态混合比例校验失败';
    END IF;
    IF invalid_student_numbers <> 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='存在非12位数字学号';
    END IF;
    IF room_total <> 100 OR bed_total <> 500 OR eligibility_total <> 500 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='房间、床位或选寝资格数量校验失败';
    END IF;
    IF answer_total <> 9000 OR feature_total <> 450 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='个人偏好测试数据数量校验失败';
    END IF;
    IF team_total <> 20 OR invitation_total <> 20 OR notification_total <> 20 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='组队或通知测试数据数量校验失败';
    END IF;
    IF assignment_total <> 50 OR snapshot_total <> 1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='已有分配或批次功能快照校验失败';
    END IF;
END$$
DELIMITER ;
CALL assert_v14_test_data();
DROP PROCEDURE assert_v14_test_data;

SELECT
    14 AS expected_schema_version,
    (SELECT COUNT(*) FROM student) AS student_count,
    (SELECT COUNT(*) FROM student WHERE gender='M') AS male_student_count,
    (SELECT COUNT(*) FROM student WHERE gender='F') AS female_student_count,
    (SELECT COUNT(*) FROM student WHERE nationality_code='CN') AS domestic_student_count,
    (SELECT COUNT(*) FROM student WHERE nationality_code<>'CN') AS international_student_count,
    (SELECT COUNT(*) FROM app_user WHERE user_type='STUDENT' AND account_status='ACTIVE') AS active_student_account_count,
    (SELECT COUNT(*) FROM app_user WHERE user_type='STUDENT' AND account_status='PENDING') AS pending_student_account_count,
    (SELECT COUNT(*) FROM room) AS room_count,
    (SELECT COUNT(*) FROM bed) AS bed_count,
    (SELECT COUNT(*) FROM batch_student_eligibility WHERE eligibility_status='ELIGIBLE') AS eligible_student_count,
    (SELECT COUNT(*) FROM questionnaire_answer) AS questionnaire_answer_count,
    (SELECT COUNT(*) FROM student_feature) AS student_feature_count,
    (SELECT COUNT(*) FROM selection_team) AS team_count,
    (SELECT COUNT(*) FROM bed_assignment) AS existing_assignment_count;
