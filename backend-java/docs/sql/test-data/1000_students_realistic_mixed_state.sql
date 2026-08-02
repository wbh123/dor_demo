-- ============================================================================
-- 1000人乱序真实业务状态模拟数据
-- 前置：数据库已通过 backend-java/docs/sql/schema.sql 初始化到V16。
-- 执行目录：backend-java/docs/sql/test-data
-- 命令：mysql -u<user> -p <database> < 1000_students_realistic_mixed_state.sql
--
-- 最终场景：
-- - 1000名学生，账号状态和录入来源乱序分布；
-- - 260间五人寝、1300张床；
-- - 840名有效在住学生：160名ROOM模式未确认床位，680名已确认床位；
-- - 一个ROOM活动批次、一个BED活动批次，房间范围完全互斥；
-- - BED批次只包含床位映射完整的寝室；
-- - 160名学生尚未入住，可继续测试选寝、转学生入批次和容量预检；
-- - 300条学生通知和440份匹配特征，插入顺序使用互质步长打乱。
-- ============================================================================
SOURCE 1000_students_base.sql;

SET @questionnaire_id=(SELECT id FROM questionnaire_version WHERE version_status='PUBLISHED' ORDER BY id DESC LIMIT 1);
SET @scheme_id=(SELECT id FROM matching_weight_scheme WHERE enabled=1 ORDER BY id DESC LIMIT 1);
SET @rule_id=(SELECT id FROM batch_rule_template WHERE enabled=1 ORDER BY is_default DESC,id DESC LIMIT 1);
SET @subscription_revision_id=(SELECT id FROM service_subscription_revision WHERE is_current=1 LIMIT 1);

DROP PROCEDURE IF EXISTS assert_reference_data;
DELIMITER $$
CREATE PROCEDURE assert_reference_data()
BEGIN
    IF @questionnaire_id IS NULL OR @scheme_id IS NULL OR @rule_id IS NULL THEN
        SIGNAL SQLSTATE '45000'
          SET MESSAGE_TEXT='缺少已发布问卷、匹配方案或批次规则模板';
    END IF;
    IF @subscription_revision_id IS NULL THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='缺少当前服务订阅修订';
    END IF;
END$$
DELIMITER ;
CALL assert_reference_data();
DROP PROCEDURE assert_reference_data;

INSERT INTO selection_batch
(id,batch_code,batch_name,batch_status,selection_mode,separate_student_categories,
 questionnaire_version_id,matching_weight_scheme_id,rule_template_id,
 start_at,end_at,hold_duration_seconds,hold_renewal_limit,
 allow_team,team_min_size,team_max_size,allow_student_random,
 unselected_strategy,rule_version,created_by,published_at,version)
VALUES
(1,'REAL-ROOM-2026','2026级寝室选择活动','OPEN','ROOM',0,
 @questionnaire_id,@scheme_id,@rule_id,
 DATE_SUB(CURRENT_TIMESTAMP(3),INTERVAL 2 DAY),DATE_ADD(CURRENT_TIMESTAMP(3),INTERVAL 5 DAY),
 300,1,1,2,5,1,'ADMIN_ALLOCATION','realistic-room-v1',@admin_id,
 DATE_SUB(CURRENT_TIMESTAMP(3),INTERVAL 3 DAY),0),
(2,'REAL-BED-2026','2026级床位选择活动','OPEN','BED',1,
 @questionnaire_id,@scheme_id,@rule_id,
 DATE_SUB(CURRENT_TIMESTAMP(3),INTERVAL 1 DAY),DATE_ADD(CURRENT_TIMESTAMP(3),INTERVAL 4 DAY),
 300,1,1,2,5,1,'ADMIN_ALLOCATION','realistic-bed-v1',@admin_id,
 DATE_SUB(CURRENT_TIMESTAMP(3),INTERVAL 2 DAY),0);

-- ROOM批次只使用混住宿舍中的40间房；BED批次使用72间专用宿舍。
INSERT INTO batch_room_scope(batch_id,room_id)
SELECT 1,id FROM room WHERE id BETWEEN 181 AND 200 OR id BETWEEN 221 AND 240;
INSERT INTO batch_room_scope(batch_id,room_id)
SELECT 2,id FROM room
WHERE id BETWEEN 45 AND 70
   OR id BETWEEN 115 AND 140
   OR id BETWEEN 151 AND 160
   OR id BETWEEN 171 AND 180;
INSERT INTO batch_bed_scope(batch_id,bed_id)
SELECT 2,b.id FROM bed b JOIN batch_room_scope scope ON scope.room_id=b.room_id
WHERE scope.batch_id=2;

INSERT INTO active_batch_room_lock(room_id,batch_id,selection_mode)
SELECT room_id,batch_id,IF(batch_id=1,'ROOM','BED') FROM batch_room_scope;

-- ROOM批次：160名已经选寝室，另有80名仍在选择。
INSERT INTO batch_student_eligibility
(batch_id,student_id,eligibility_status,reason_code,source_type,added_by,added_at)
SELECT 1,id,'ELIGIBLE','REALISTIC_ROOM_SCOPE',
       IF(enrollment_source='TRANSFER_MANUAL','TRANSFER_MANUAL','INITIAL'),
       IF(enrollment_source='TRANSFER_MANUAL',@admin_id,NULL),
       IF(enrollment_source='TRANSFER_MANUAL',CURRENT_TIMESTAMP(3),NULL)
FROM student
WHERE id BETWEEN 1 AND 80
   OR id BETWEEN 361 AND 400
   OR id BETWEEN 501 AND 580
   OR id BETWEEN 861 AND 900;

-- BED批次：120名已完成选床，80名仍在选择。
INSERT INTO batch_student_eligibility
(batch_id,student_id,eligibility_status,reason_code,source_type,added_by,added_at)
SELECT 2,id,'ELIGIBLE','REALISTIC_BED_SCOPE',
       IF(enrollment_source='TRANSFER_MANUAL','TRANSFER_MANUAL','INITIAL'),
       IF(enrollment_source='TRANSFER_MANUAL',@admin_id,NULL),
       IF(enrollment_source='TRANSFER_MANUAL',CURRENT_TIMESTAMP(3),NULL)
FROM student
WHERE id BETWEEN 401 AND 500 OR id BETWEEN 901 AND 1000;

INSERT INTO active_batch_student_lock(student_id,batch_id)
SELECT student_id,batch_id FROM batch_student_eligibility WHERE eligibility_status='ELIGIBLE';

-- ROOM模式寝室归属：160人，具体床位为空。
DROP PROCEDURE IF EXISTS seed_room_mode_residencies;
DELIMITER $$
CREATE PROCEDURE seed_room_mode_residencies()
BEGIN
    DECLARE offset_value INT DEFAULT 0;
    DECLARE male_student INT;
    DECLARE female_student INT;
    DECLARE male_room INT;
    DECLARE female_room INT;
    WHILE offset_value<80 DO
        SET male_student=1+MOD(offset_value*37,80);
        SET female_student=501+MOD(offset_value*37,80);
        SET male_room=181+FLOOR(offset_value/5);
        SET female_room=221+FLOOR(offset_value/5);
        INSERT INTO room_assignment
        (batch_id,student_id,room_id,bed_id,team_id,source_selection_mode,
         assignment_method,assignment_status,assigned_by,assigned_at,bed_confirmed_at)
        VALUES
        (1,male_student,male_room,NULL,NULL,'ROOM','ROOM_SELECT','ACTIVE',
         (SELECT id FROM app_user WHERE student_id=male_student),
         DATE_SUB(CURRENT_TIMESTAMP(3),INTERVAL MOD(offset_value,48) HOUR),NULL),
        (1,female_student,female_room,NULL,NULL,'ROOM','ROOM_SELECT','ACTIVE',
         (SELECT id FROM app_user WHERE student_id=female_student),
         DATE_SUB(CURRENT_TIMESTAMP(3),INTERVAL MOD(offset_value+13,48) HOUR),NULL);
        SET offset_value=offset_value+1;
    END WHILE;
END$$
DELIMITER ;
CALL seed_room_mode_residencies();
DROP PROCEDURE seed_room_mode_residencies;

-- 560名历史在住学生，全部已确认真实床位；按类别进入对应专用宿舍。
DROP PROCEDURE IF EXISTS seed_historical_residencies;
DELIMITER $$
CREATE PROCEDURE seed_historical_residencies()
BEGIN
    DECLARE student_value INT DEFAULT 81;
    DECLARE gender_value CHAR(1);
    DECLARE category_value VARCHAR(24);
    DECLARE bed_value BIGINT;
    DECLARE room_value BIGINT;
    historical_loop: WHILE student_value<=860 DO
        IF student_value=361 THEN SET student_value=581; END IF;
        SET gender_value=(SELECT gender FROM student WHERE id=student_value);
        SET category_value=(SELECT student_category FROM student WHERE id=student_value);
        SELECT b.id,b.room_id INTO bed_value,room_value
        FROM bed b JOIN room r ON r.id=b.room_id
        WHERE b.operational_status='ENABLED'
          AND r.gender_restriction=gender_value
          AND r.resident_scope=IF(category_value='DOMESTIC','DOMESTIC_ONLY','INTERNATIONAL_ONLY')
          AND NOT EXISTS (
              SELECT 1 FROM room_assignment ra
              WHERE ra.bed_id=b.id AND ra.assignment_status='ACTIVE'
          )
        ORDER BY b.id LIMIT 1;
        INSERT INTO room_assignment
        (batch_id,student_id,room_id,bed_id,source_selection_mode,
         assignment_method,assignment_status,assigned_by,assigned_at,bed_confirmed_at)
        VALUES
        (NULL,student_value,room_value,bed_value,'DIRECT','DIRECT_BED','ACTIVE',@admin_id,
         DATE_SUB(CURRENT_TIMESTAMP(3),INTERVAL (30+MOD(student_value,210)) DAY),
         DATE_SUB(CURRENT_TIMESTAMP(3),INTERVAL (30+MOD(student_value,210)) DAY));
        SET student_value=student_value+1;
    END WHILE historical_loop;
END$$
DELIMITER ;
CALL seed_historical_residencies();
DROP PROCEDURE seed_historical_residencies;

-- BED活动批次：120名学生按类别和性别选择活动范围内真实空床。
DROP PROCEDURE IF EXISTS seed_bed_mode_assignments;
DELIMITER $$
CREATE PROCEDURE seed_bed_mode_assignments()
BEGIN
    DECLARE index_value INT DEFAULT 0;
    DECLARE student_value INT;
    DECLARE gender_value CHAR(1);
    DECLARE category_value VARCHAR(24);
    DECLARE bed_value BIGINT;
    DECLARE room_value BIGINT;
    WHILE index_value<120 DO
        SET student_value=IF(index_value<60,401+MOD(index_value*37,60),901+MOD((index_value-60)*37,60));
        SET gender_value=(SELECT gender FROM student WHERE id=student_value);
        SET category_value=(SELECT student_category FROM student WHERE id=student_value);
        SELECT b.id,b.room_id INTO bed_value,room_value
        FROM bed b
        JOIN room r ON r.id=b.room_id
        JOIN batch_bed_scope scope ON scope.batch_id=2 AND scope.bed_id=b.id
        WHERE b.operational_status='ENABLED'
          AND r.gender_restriction=gender_value
          AND r.resident_scope=IF(category_value='DOMESTIC','DOMESTIC_ONLY','INTERNATIONAL_ONLY')
          AND NOT EXISTS (
              SELECT 1 FROM room_assignment ra
              WHERE ra.bed_id=b.id AND ra.assignment_status='ACTIVE'
          )
        ORDER BY b.id LIMIT 1;
        INSERT INTO bed_assignment
        (batch_id,student_id,bed_id,team_id,assignment_method,assignment_status,
         assigned_by,assigned_at,version)
        VALUES
        (2,student_value,bed_value,NULL,'SELF_SELECT','ACTIVE',
         (SELECT id FROM app_user WHERE student_id=student_value),
         DATE_SUB(CURRENT_TIMESTAMP(3),INTERVAL MOD(index_value,20) HOUR),0);
        INSERT INTO room_assignment
        (batch_id,student_id,room_id,bed_id,team_id,source_selection_mode,
         assignment_method,assignment_status,assigned_by,assigned_at,bed_confirmed_at)
        VALUES
        (2,student_value,room_value,bed_value,NULL,'BED','BED_SELECT','ACTIVE',
         (SELECT id FROM app_user WHERE student_id=student_value),
         DATE_SUB(CURRENT_TIMESTAMP(3),INTERVAL MOD(index_value,20) HOUR),
         DATE_SUB(CURRENT_TIMESTAMP(3),INTERVAL MOD(index_value,20) HOUR));
        SET index_value=index_value+1;
    END WHILE;
END$$
DELIMITER ;
CALL seed_bed_mode_assignments();
DROP PROCEDURE seed_bed_mode_assignments;

INSERT INTO room_assignment_history
(room_assignment_id,student_id,room_id,bed_id,event_type,operator_user_id,
 reason,current_data,occurred_at)
SELECT ra.id,ra.student_id,ra.room_id,ra.bed_id,
       IF(ra.bed_id IS NULL,'ROOM_ASSIGNED','BED_ASSIGNED'),
       ra.assigned_by,
       CASE WHEN ra.bed_id IS NULL
            THEN '真实状态模拟：选寝室后待确认实际床位'
            ELSE '真实状态模拟：已确认实际床位' END,
       JSON_OBJECT('batchId',ra.batch_id,'roomId',ra.room_id,'bedId',ra.bed_id,
                   'selectionMode',ra.source_selection_mode),
       ra.assigned_at
FROM room_assignment ra;

-- 账号状态乱序分布；所有在住学生保持ACTIVE，未入住学生模拟待激活、锁定和禁用。
UPDATE app_user u JOIN student s ON s.id=u.student_id
LEFT JOIN room_assignment ra ON ra.student_id=s.id AND ra.assignment_status='ACTIVE'
SET u.account_status=CASE
        WHEN ra.id IS NOT NULL THEN 'ACTIVE'
        WHEN MOD(s.id*73,20)<11 THEN 'ACTIVE'
        WHEN MOD(s.id*73,20)<16 THEN 'PENDING'
        WHEN MOD(s.id*73,20)<19 THEN 'LOCKED'
        ELSE 'DISABLED' END,
    u.password_hash=CASE
        WHEN ra.id IS NOT NULL OR MOD(s.id*73,20)<11 THEN '{noop}Student@2026'
        ELSE NULL END,
    u.last_login_at=CASE
        WHEN ra.id IS NOT NULL OR MOD(s.id*73,20)<11
        THEN DATE_SUB(CURRENT_TIMESTAMP(3),INTERVAL MOD(s.id*29,60) DAY)
        ELSE NULL END;

-- 为全部活动批次学生生成乱序匹配特征。
INSERT INTO student_feature
(batch_id,student_id,algorithm_version,feature_vector_json,
 explanation_tags_json,calculated_at,source_answer_version)
SELECT e.batch_id,e.student_id,'realistic-v1',
       JSON_OBJECT(
         'sleep',MOD(e.student_id*17,5)+1,
         'cleanliness',MOD(e.student_id*31,5)+1,
         'study',MOD(e.student_id*43,5)+1,
         'social',MOD(e.student_id*59,5)+1,
         'temperature',MOD(e.student_id*71,5)+1),
       JSON_ARRAY(
         IF(MOD(e.student_id,2)=0,'作息接近','安静偏好'),
         IF(MOD(e.student_id,3)=0,'卫生习惯接近','学习氛围接近')),
       DATE_SUB(CURRENT_TIMESTAMP(3),INTERVAL MOD(e.student_id*11,72) HOUR),1
FROM batch_student_eligibility e
ORDER BY MOD(e.student_id*73,1009);

-- 300条通知，覆盖已读和未读状态。
INSERT INTO student_notification
(student_id,notification_type,title,message,parameters_json,read_at,created_at)
SELECT s.id,'TEAM_DISSOLVED','选寝状态提醒',
       CASE WHEN MOD(s.id,2)=0 THEN '你的选寝活动状态已更新，请及时查看。'
            ELSE '宿舍服务有新的安排，请进入系统核对。' END,
       JSON_OBJECT('source','REALISTIC_1000','batchId',IF(s.id<=500,1,2)),
       IF(MOD(s.id,4)=0,DATE_SUB(CURRENT_TIMESTAMP(3),INTERVAL MOD(s.id,48) HOUR),NULL),
       DATE_SUB(CURRENT_TIMESTAMP(3),INTERVAL MOD(s.id*13,120) HOUR)
FROM student s
WHERE MOD(s.id*37,10)<3
ORDER BY MOD(s.id*73,1009)
LIMIT 300;

INSERT INTO batch_entitlement_snapshot
(batch_id,subscription_revision_id,granted_features_json,quota_snapshot_json,snapshot_version,captured_at)
VALUES
(1,@subscription_revision_id,
 JSON_ARRAY('P1_BATCH_BASIC','P1_SELF_SELECTION','P1_TEAM_SELECTION','P1_RANDOM_RECOMMENDATION','P1_REALTIME_STATUS'),
 JSON_OBJECT('MAX_STUDENTS',100000,'MAX_BEDS',1000000,'MAX_CONCURRENT_ACTIVE_BATCHES',100),
 'ENTITLEMENT_V1',DATE_SUB(CURRENT_TIMESTAMP(3),INTERVAL 3 DAY)),
(2,@subscription_revision_id,
 JSON_ARRAY('P1_BATCH_BASIC','P1_SELF_SELECTION','P1_TEAM_SELECTION','P1_RANDOM_RECOMMENDATION','P1_REALTIME_STATUS','P2_BED_SELECTION_MODE'),
 JSON_OBJECT('MAX_STUDENTS',100000,'MAX_BEDS',1000000,'MAX_CONCURRENT_ACTIVE_BATCHES',100),
 'ENTITLEMENT_V1',DATE_SUB(CURRENT_TIMESTAMP(3),INTERVAL 2 DAY));

DROP PROCEDURE IF EXISTS assert_realistic_1000_data;
DELIMITER $$
CREATE PROCEDURE assert_realistic_1000_data()
BEGIN
    IF (SELECT COUNT(*) FROM student)<>1000 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='学生数量不是1000';
    END IF;
    IF (SELECT COUNT(*) FROM room)<>260 OR (SELECT COUNT(*) FROM bed)<>1300 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='宿舍资源数量不正确';
    END IF;
    IF (SELECT COUNT(*) FROM room_assignment WHERE assignment_status='ACTIVE')<>840 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='有效在住人数不是840';
    END IF;
    IF (SELECT COUNT(*) FROM room_assignment WHERE assignment_status='ACTIVE' AND bed_id IS NULL)<>160 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='待确认床位人数不是160';
    END IF;
    IF EXISTS (
        SELECT room_id FROM room_assignment WHERE assignment_status='ACTIVE'
        GROUP BY room_id HAVING COUNT(*)>(SELECT capacity FROM room WHERE id=room_id)
    ) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='存在寝室超容量';
    END IF;
    IF EXISTS (
        SELECT bed_id FROM room_assignment
        WHERE assignment_status='ACTIVE' AND bed_id IS NOT NULL
        GROUP BY bed_id HAVING COUNT(*)>1
    ) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='存在实际床位重复占用';
    END IF;
    IF EXISTS (
        SELECT 1 FROM room_assignment ra
        JOIN student s ON s.id=ra.student_id
        JOIN room r ON r.id=ra.room_id
        WHERE ra.assignment_status='ACTIVE'
          AND ((r.resident_scope='DOMESTIC_ONLY' AND s.student_category<>'DOMESTIC')
            OR (r.resident_scope='INTERNATIONAL_ONLY' AND s.student_category<>'INTERNATIONAL'))
    ) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='存在学生类别与宿舍属性冲突';
    END IF;
    IF EXISTS (
        SELECT l.room_id FROM active_batch_room_lock l
        GROUP BY l.room_id HAVING COUNT(*)>1
    ) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='存在活动批次房间重复锁定';
    END IF;
    IF EXISTS (
        SELECT 1 FROM active_batch_room_lock l
        JOIN room_assignment ra ON ra.room_id=l.room_id
        WHERE l.selection_mode='BED' AND ra.assignment_status='ACTIVE' AND ra.bed_id IS NULL
    ) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='BED批次包含未确认实际床位的寝室';
    END IF;
END$$
DELIMITER ;
CALL assert_realistic_1000_data();
DROP PROCEDURE assert_realistic_1000_data;

SELECT 'REALISTIC_1000_READY' AS status,
       (SELECT COUNT(*) FROM student) AS students,
       (SELECT COUNT(*) FROM student WHERE enrollment_source='TRANSFER_MANUAL') AS transfer_students,
       (SELECT COUNT(*) FROM app_user WHERE user_type='STUDENT' AND account_status='ACTIVE') AS active_accounts,
       (SELECT COUNT(*) FROM app_user WHERE user_type='STUDENT' AND account_status='PENDING') AS pending_accounts,
       (SELECT COUNT(*) FROM app_user WHERE user_type='STUDENT' AND account_status='LOCKED') AS locked_accounts,
       (SELECT COUNT(*) FROM app_user WHERE user_type='STUDENT' AND account_status='DISABLED') AS disabled_accounts,
       (SELECT COUNT(*) FROM room_assignment WHERE assignment_status='ACTIVE') AS active_residencies,
       (SELECT COUNT(*) FROM room_assignment WHERE assignment_status='ACTIVE' AND bed_id IS NULL) AS unknown_beds,
       (SELECT COUNT(*) FROM bed_assignment WHERE assignment_status='ACTIVE') AS active_bed_assignments,
       (SELECT COUNT(*) FROM selection_batch WHERE batch_status='OPEN') AS open_batches,
       (SELECT COUNT(*) FROM active_batch_room_lock) AS active_room_locks,
       (SELECT COUNT(*) FROM student_notification) AS notifications,
       (SELECT COUNT(*) FROM student_feature) AS student_features,
       1300-(SELECT COUNT(*) FROM room_assignment WHERE assignment_status='ACTIVE' AND bed_id IS NOT NULL) AS physically_unoccupied_beds;
