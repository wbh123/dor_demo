-- ============================================================================
-- 武汉科技大学学生宿舍智能选择系统：V16全量测试数据入口
--
-- 必须从仓库根目录执行：
--   mysql --binary-mode=1 -u<user> -p <database> \
--     < backend-java/docs/sql/reset_and_seed_test_data.sql
--
-- 本入口先执行500人核心测试数据脚本，再补充V15/V16的选寝模式、
-- 国内生/国际生隔离、活动房间锁、跨批次在住和最新套餐授权快照。
-- 禁止在生产数据库执行。
-- ============================================================================

SET NAMES utf8mb4;

SOURCE backend-java/docs/sql/reset_and_seed_test_data_core.sql

-- ----------------------------------------------------------------------------
-- V16结构校验。
-- ----------------------------------------------------------------------------
DROP PROCEDURE IF EXISTS assert_v16_schema;
DELIMITER $$
CREATE PROCEDURE assert_v16_schema()
BEGIN
    DECLARE student_column_count INT;
    DECLARE room_scope_column_count INT;
    DECLARE batch_column_count INT;
    DECLARE residency_table_count INT;
    DECLARE latest_version INT;

    SELECT COUNT(*) INTO student_column_count
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'student'
      AND column_name IN ('student_category', 'enrollment_source');

    SELECT COUNT(*) INTO room_scope_column_count
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'room'
      AND column_name = 'resident_scope';

    SELECT COUNT(*) INTO batch_column_count
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'selection_batch'
      AND column_name IN ('selection_mode', 'separate_student_categories');

    SELECT COUNT(*) INTO residency_table_count
    FROM information_schema.tables
    WHERE table_schema = DATABASE()
      AND table_name IN (
        'active_batch_room_lock', 'room_assignment', 'room_assignment_history'
      );

    IF EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = DATABASE() AND table_name = 'flyway_schema_history'
    ) THEN
        SELECT MAX(CAST(version AS UNSIGNED)) INTO latest_version
        FROM flyway_schema_history
        WHERE success = 1 AND version IS NOT NULL;
        IF latest_version < 16 THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = '数据库Flyway版本低于V16，请先执行正式迁移';
        END IF;
    END IF;

    IF student_column_count <> 2
       OR room_scope_column_count <> 1
       OR batch_column_count <> 2
       OR residency_table_count <> 3 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '数据库结构不是V16，请先导入最新schema.sql或执行Flyway V1-V16';
    END IF;
END$$
DELIMITER ;
CALL assert_v16_schema();
DROP PROCEDURE assert_v16_schema;

-- ----------------------------------------------------------------------------
-- 将国际学生均匀分布到男女学生中：
-- 男生国际生201～250，女生国际生451～500；其余400人为国内生。
-- ----------------------------------------------------------------------------
UPDATE student
SET nationality_code = ELT(
        MOD(id - 201, 10) + 1,
        'US','GB','JP','KR','FR','DE','RU','IN','EG','TH'
    ),
    student_name = CONCAT('International Male ', LPAD(id, 4, '0')),
    phone_number = CONCAT('+', 20 + MOD(id, 80), ' ', LPAD(10000000 + id, 8, '0'))
WHERE id BETWEEN 201 AND 250;

UPDATE student
SET nationality_code = 'CN',
    student_name = CONCAT('女生测试', LPAD(id, 4, '0')),
    phone_number = CONCAT('+86 13', MOD(id, 10), ' ', LPAD(id, 4, '0'), ' ', LPAD(id, 4, '0'))
WHERE id BETWEEN 401 AND 450;

UPDATE student
SET student_category = CASE
        WHEN nationality_code = 'CN' THEN 'DOMESTIC'
        ELSE 'INTERNATIONAL'
    END,
    enrollment_source = 'INITIAL_IMPORT';

UPDATE app_user user_record
JOIN student student_record ON student_record.id = user_record.student_id
SET user_record.display_name = student_record.student_name
WHERE user_record.user_type = 'STUDENT';

-- ----------------------------------------------------------------------------
-- 每栋宿舍40个国内生专用五人间、10个国际生专用五人间。
-- 两个性别各提供国内生床位200个、国际生床位50个。
-- ----------------------------------------------------------------------------
UPDATE room
SET resident_scope = CASE
        WHEN MOD(id - 1, 50) < 40 THEN 'DOMESTIC_ONLY'
        ELSE 'INTERNATIONAL_ONLY'
    END;

-- ----------------------------------------------------------------------------
-- 测试批次采用BED模式，并开启国内生/国际生专用寝室隔离。
-- 为100个批次房间建立活动互斥锁。
-- ----------------------------------------------------------------------------
UPDATE selection_batch
SET selection_mode = 'BED',
    separate_student_categories = 1
WHERE id = 1;

DELETE FROM active_batch_room_lock WHERE batch_id = 1;
INSERT INTO active_batch_room_lock
(room_id, batch_id, selection_mode, locked_at)
SELECT room_id, 1, 'BED', CURRENT_TIMESTAMP(3)
FROM batch_room_scope
WHERE batch_id = 1;

-- ----------------------------------------------------------------------------
-- 将当前服务订阅指向V15创建的最新套餐修订，重新生成批次授权快照。
-- ----------------------------------------------------------------------------
SET @latest_plan_revision_id = (
    SELECT revision_record.id
    FROM subscription_plan_revision revision_record
    JOIN subscription_plan plan_record ON plan_record.id = revision_record.plan_id
    WHERE plan_record.plan_code = 'FULL_CURRENT'
      AND revision_record.enabled = 1
    ORDER BY revision_record.revision DESC
    LIMIT 1
);
SET @current_service_revision_id = (
    SELECT id
    FROM service_subscription_revision
    WHERE is_current = 1
    ORDER BY revision DESC
    LIMIT 1
);

UPDATE service_subscription_revision
SET plan_revision_id = @latest_plan_revision_id,
    change_reason = 'V16测试数据初始化：启用双模式和分类隔离测试'
WHERE id = @current_service_revision_id;

DELETE FROM batch_entitlement_snapshot WHERE batch_id = 1;
INSERT INTO batch_entitlement_snapshot
(batch_id, subscription_revision_id, granted_features_json,
 quota_snapshot_json, snapshot_version)
SELECT
    1,
    current_revision.id,
    (
        SELECT JSON_ARRAYAGG(plan_feature.feature_code)
        FROM plan_revision_feature plan_feature
        WHERE plan_feature.plan_revision_id = current_revision.plan_revision_id
    ),
    (
        SELECT JSON_OBJECTAGG(plan_quota.quota_code, plan_quota.quota_value)
        FROM plan_revision_quota plan_quota
        WHERE plan_quota.plan_revision_id = current_revision.plan_revision_id
    ),
    'entitlement-v16-test'
FROM service_subscription_revision current_revision
WHERE current_revision.id = @current_service_revision_id;

-- ----------------------------------------------------------------------------
-- V16在住事实：将核心脚本预置的50条具体床位分配同步为跨批次在住记录。
-- 这些学生均为国际生，且床位位于对应性别的国际生专用寝室。
-- ----------------------------------------------------------------------------
DELETE FROM room_assignment_history;
DELETE FROM room_assignment;

INSERT INTO room_assignment
(batch_id, student_id, room_id, bed_id, team_id,
 source_selection_mode, assignment_method, assignment_status,
 assigned_by, assigned_at, bed_confirmed_at, version)
SELECT assignment.batch_id,
       assignment.student_id,
       bed_record.room_id,
       assignment.bed_id,
       assignment.team_id,
       'BED',
       CASE
           WHEN assignment.assignment_method = 'TEAM_SELECT' THEN 'TEAM_BED_SELECT'
           WHEN assignment.assignment_method = 'MANUAL_ADJUSTMENT' THEN 'MANUAL_ADJUSTMENT'
           ELSE 'BED_SELECT'
       END,
       'ACTIVE',
       assignment.assigned_by,
       assignment.assigned_at,
       assignment.assigned_at,
       0
FROM bed_assignment assignment
JOIN bed bed_record ON bed_record.id = assignment.bed_id
WHERE assignment.assignment_status = 'ACTIVE';

INSERT INTO room_assignment_history
(room_assignment_id, student_id, room_id, bed_id, event_type,
 operator_user_id, reason, current_data, occurred_at)
SELECT residency.id,
       residency.student_id,
       residency.room_id,
       residency.bed_id,
       'MIGRATED',
       residency.assigned_by,
       'V16测试数据：由已有具体床位分配建立在住事实',
       JSON_OBJECT(
           'batchId', residency.batch_id,
           'selectionMode', residency.source_selection_mode,
           'assignmentMethod', residency.assignment_method,
           'status', residency.assignment_status
       ),
       residency.assigned_at
FROM room_assignment residency;

-- ----------------------------------------------------------------------------
-- V16最终校验。
-- ----------------------------------------------------------------------------
DROP PROCEDURE IF EXISTS assert_v16_test_data;
DELIMITER $$
CREATE PROCEDURE assert_v16_test_data()
BEGIN
    DECLARE student_total INT;
    DECLARE domestic_total INT;
    DECLARE international_total INT;
    DECLARE male_international_total INT;
    DECLARE female_international_total INT;
    DECLARE domestic_room_total INT;
    DECLARE international_room_total INT;
    DECLARE bed_total INT;
    DECLARE room_lock_total INT;
    DECLARE residency_total INT;
    DECLARE residency_history_total INT;
    DECLARE invalid_residency_scope_total INT;
    DECLARE bed_mode_feature_total INT;
    DECLARE snapshot_feature_total INT;

    SELECT COUNT(*) INTO student_total FROM student;
    SELECT COUNT(*) INTO domestic_total
    FROM student WHERE student_category = 'DOMESTIC';
    SELECT COUNT(*) INTO international_total
    FROM student WHERE student_category = 'INTERNATIONAL';
    SELECT COUNT(*) INTO male_international_total
    FROM student WHERE gender = 'M' AND student_category = 'INTERNATIONAL';
    SELECT COUNT(*) INTO female_international_total
    FROM student WHERE gender = 'F' AND student_category = 'INTERNATIONAL';
    SELECT COUNT(*) INTO domestic_room_total
    FROM room WHERE resident_scope = 'DOMESTIC_ONLY';
    SELECT COUNT(*) INTO international_room_total
    FROM room WHERE resident_scope = 'INTERNATIONAL_ONLY';
    SELECT COUNT(*) INTO bed_total FROM bed;
    SELECT COUNT(*) INTO room_lock_total
    FROM active_batch_room_lock
    WHERE batch_id = 1 AND selection_mode = 'BED';
    SELECT COUNT(*) INTO residency_total
    FROM room_assignment WHERE assignment_status = 'ACTIVE';
    SELECT COUNT(*) INTO residency_history_total FROM room_assignment_history;
    SELECT COUNT(*) INTO invalid_residency_scope_total
    FROM room_assignment residency
    JOIN student student_record ON student_record.id = residency.student_id
    JOIN room room_record ON room_record.id = residency.room_id
    WHERE residency.assignment_status = 'ACTIVE'
      AND (
          (student_record.student_category = 'DOMESTIC'
           AND room_record.resident_scope <> 'DOMESTIC_ONLY')
          OR
          (student_record.student_category = 'INTERNATIONAL'
           AND room_record.resident_scope <> 'INTERNATIONAL_ONLY')
      );
    SELECT COUNT(*) INTO bed_mode_feature_total
    FROM plan_revision_feature
    WHERE plan_revision_id = @latest_plan_revision_id
      AND feature_code = 'P2_BED_SELECTION_MODE';
    SELECT COUNT(*) INTO snapshot_feature_total
    FROM batch_entitlement_snapshot
    WHERE batch_id = 1
      AND JSON_CONTAINS(granted_features_json, JSON_QUOTE('P2_BED_SELECTION_MODE'));

    IF student_total <> 500 OR domestic_total <> 400 OR international_total <> 100 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'V16测试学生总数或国内外学生数量异常';
    END IF;
    IF male_international_total <> 50 OR female_international_total <> 50 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'V16国际学生性别分布异常';
    END IF;
    IF domestic_room_total <> 80 OR international_room_total <> 20 OR bed_total <> 500 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'V16国内外专用寝室或床位数量异常';
    END IF;
    IF room_lock_total <> 100 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'V16活动房间锁数量异常';
    END IF;
    IF residency_total <> 50 OR residency_history_total <> 50
       OR invalid_residency_scope_total <> 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'V16在住事实、历史或分类寝室一致性校验失败';
    END IF;
    IF bed_mode_feature_total <> 1 OR snapshot_feature_total <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'V16选择床位模式授权或批次快照缺失';
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM selection_batch
        WHERE id = 1
          AND selection_mode = 'BED'
          AND separate_student_categories = 1
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '测试批次模式或学生分类隔离设置错误';
    END IF;
END$$
DELIMITER ;
CALL assert_v16_test_data();
DROP PROCEDURE assert_v16_test_data;

SELECT
    16 AS expected_schema_version,
    (SELECT selection_mode FROM selection_batch WHERE id=1) AS selection_mode,
    (SELECT separate_student_categories FROM selection_batch WHERE id=1) AS separate_student_categories,
    (SELECT COUNT(*) FROM student) AS student_count,
    (SELECT COUNT(*) FROM student WHERE student_category='DOMESTIC') AS domestic_student_count,
    (SELECT COUNT(*) FROM student WHERE student_category='INTERNATIONAL') AS international_student_count,
    (SELECT COUNT(*) FROM room WHERE resident_scope='DOMESTIC_ONLY') AS domestic_room_count,
    (SELECT COUNT(*) FROM room WHERE resident_scope='INTERNATIONAL_ONLY') AS international_room_count,
    (SELECT COUNT(*) FROM bed) AS bed_count,
    (SELECT COUNT(*) FROM active_batch_room_lock WHERE batch_id=1) AS active_room_lock_count,
    (SELECT COUNT(*) FROM bed_assignment) AS existing_bed_assignment_count,
    (SELECT COUNT(*) FROM room_assignment WHERE assignment_status='ACTIVE') AS active_residency_count;
