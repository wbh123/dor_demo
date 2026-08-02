-- ============================================================================
-- 1000人干净测试数据
-- 前置：数据库已通过 backend-java/docs/sql/schema.sql 初始化到V16。
-- 执行目录：backend-java/docs/sql/test-data
-- 命令：mysql -u<user> -p <database> < 1000_students_clean.sql
--
-- 数据状态：
-- - 1000名完整学生档案与待激活账号；
-- - 260间五人寝、1300张床；
-- - 国内生/国际生、男女生和混住宿舍均有充足容量；
-- - 无批次、无组队、无在住记录、无床位分配、无通知与个人偏好结果。
-- ============================================================================
SOURCE 1000_students_base.sql;

DROP PROCEDURE IF EXISTS assert_clean_1000_data;
DELIMITER $$
CREATE PROCEDURE assert_clean_1000_data()
BEGIN
    IF (SELECT COUNT(*) FROM student) <> 1000 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='干净数据学生数量不是1000';
    END IF;
    IF (SELECT COUNT(*) FROM room) <> 260 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='干净数据房间数量不是260';
    END IF;
    IF (SELECT COUNT(*) FROM bed) <> 1300 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='干净数据床位数量不是1300';
    END IF;
    IF (SELECT COUNT(*) FROM selection_batch) <> 0
       OR (SELECT COUNT(*) FROM room_assignment) <> 0
       OR (SELECT COUNT(*) FROM bed_assignment) <> 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='干净数据不应包含批次或住宿分配';
    END IF;
    IF (SELECT COUNT(*) FROM student WHERE student_category='INTERNATIONAL') <> 150 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='国际生数量不是150';
    END IF;
END$$
DELIMITER ;
CALL assert_clean_1000_data();
DROP PROCEDURE assert_clean_1000_data;

SELECT 'CLEAN_1000_READY' AS status,
       (SELECT COUNT(*) FROM student) AS students,
       (SELECT COUNT(*) FROM app_user WHERE user_type='STUDENT' AND account_status='PENDING') AS pending_accounts,
       (SELECT COUNT(*) FROM student WHERE student_category='DOMESTIC') AS domestic_students,
       (SELECT COUNT(*) FROM student WHERE student_category='INTERNATIONAL') AS international_students,
       (SELECT COUNT(*) FROM room) AS rooms,
       (SELECT COUNT(*) FROM bed) AS beds,
       (SELECT SUM(capacity) FROM room) AS total_capacity,
       (SELECT COUNT(*) FROM room_assignment) AS active_residencies;
