-- ============================================================================
-- V16 千人测试公共基础：1000名学生、260间五人寝、1300张床。
-- 仅允许开发/测试数据库执行。
-- 推荐从 backend-java/docs/sql/test-data 目录执行。
-- ============================================================================
SET NAMES utf8mb4;
SET @database_name = DATABASE();

DROP PROCEDURE IF EXISTS assert_v16_schema;
DELIMITER $$
CREATE PROCEDURE assert_v16_schema()
BEGIN
    DECLARE required_columns INT DEFAULT 0;
    SELECT COUNT(*) INTO required_columns
    FROM information_schema.columns
    WHERE table_schema=DATABASE()
      AND (
        (table_name='student' AND column_name IN ('student_category','enrollment_source'))
        OR (table_name='room' AND column_name='resident_scope')
        OR (table_name='selection_batch' AND column_name IN ('selection_mode','separate_student_categories'))
        OR (table_name='room_assignment' AND column_name IN ('bed_id','ended_at','source_selection_mode'))
      );
    IF required_columns <> 8 THEN
        SIGNAL SQLSTATE '45000'
          SET MESSAGE_TEXT='数据库结构不是V16，请先导入backend-java/docs/sql/schema.sql';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM app_user WHERE user_type='SYSTEM_ADMIN') THEN
        SIGNAL SQLSTATE '45000'
          SET MESSAGE_TEXT='缺少V13初始化的SYSTEM_ADMIN，请先执行完整结构迁移';
    END IF;
END$$
DELIMITER ;
CALL assert_v16_schema();
DROP PROCEDURE assert_v16_schema;

-- 清理可变业务表，保留平台套餐、订阅、权限目录、问卷、匹配方案和规则模板。
DROP PROCEDURE IF EXISTS clear_1000_test_data;
DELIMITER $$
CREATE PROCEDURE clear_1000_test_data()
BEGIN
    DECLARE finished INT DEFAULT 0;
    DECLARE target_table VARCHAR(128);
    DECLARE table_cursor CURSOR FOR
        SELECT table_name
        FROM information_schema.tables
        WHERE table_schema=@database_name
          AND table_type='BASE TABLE'
          AND table_name NOT IN (
              'flyway_schema_history','app_user',
              'feature_catalog','quota_catalog',
              'subscription_plan','subscription_plan_revision',
              'plan_revision_feature','plan_revision_quota',
              'service_subscription','service_subscription_revision',
              'subscription_feature_override','subscription_quota_override',
              'service_quota_alert',
              'questionnaire_version','questionnaire_question','questionnaire_option',
              'matching_weight_scheme','batch_rule_template'
          );
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET finished=1;
    SET FOREIGN_KEY_CHECKS=0;
    OPEN table_cursor;
    table_loop: LOOP
        FETCH table_cursor INTO target_table;
        IF finished=1 THEN LEAVE table_loop; END IF;
        SET @delete_sql=CONCAT('DELETE FROM `',REPLACE(target_table,'`','``'),'`');
        PREPARE statement FROM @delete_sql;
        EXECUTE statement;
        DEALLOCATE PREPARE statement;
    END LOOP;
    CLOSE table_cursor;
    DELETE FROM app_user WHERE user_type<>'SYSTEM_ADMIN';
    DELETE FROM platform_audit_log;
    SET FOREIGN_KEY_CHECKS=1;
END$$
DELIMITER ;
CALL clear_1000_test_data();
DROP PROCEDURE clear_1000_test_data;

INSERT INTO app_user
(student_id,username,password_hash,user_type,account_status,display_name,password_change_required)
VALUES
(NULL,'admin','{noop}Dormitory@2026','ADMIN','ACTIVE','千人测试管理员',0);
SET @admin_id=(SELECT id FROM app_user WHERE username='admin' AND user_type='ADMIN' LIMIT 1);
SET @system_admin_id=(SELECT id FROM app_user WHERE user_type='SYSTEM_ADMIN' LIMIT 1);

INSERT INTO campus(id,campus_code,campus_name,address,enabled)
VALUES(1,'HQ','黄家湖校区','湖北省武汉市黄家湖西路',1);

INSERT INTO major(id,major_code,major_name,enabled) VALUES
(1,'M001','计算机科学与技术',1),(2,'M002','软件工程',1),
(3,'M003','土木工程',1),(4,'M004','机械工程',1),
(5,'M005','国际经济与贸易',1),(6,'M006','自动化',1),
(7,'M007','材料科学与工程',1),(8,'M008','临床医学',1),
(9,'M009','工商管理',1),(10,'M010','建筑学',1),
(11,'M011','人工智能',1),(12,'M012','电子信息工程',1);

-- 六栋宿舍覆盖国内男/女、国际男/女、混住男/女。
INSERT INTO dormitory_building
(id,campus_id,building_code,building_name,gender_restriction,enabled) VALUES
(1,1,'DM-A','国内男生一舍','M',1),
(2,1,'DF-A','国内女生一舍','F',1),
(3,1,'IM-A','国际男生公寓','M',1),
(4,1,'IF-A','国际女生公寓','F',1),
(5,1,'MM-A','男生混住宿舍','M',1),
(6,1,'MF-A','女生混住宿舍','F',1);

DROP PROCEDURE IF EXISTS seed_1000_rooms;
DELIMITER $$
CREATE PROCEDURE seed_1000_rooms()
BEGIN
    DECLARE building_index INT DEFAULT 1;
    DECLARE floor_index INT;
    DECLARE room_index INT;
    DECLARE rooms_per_floor INT;
    DECLARE floor_id_value INT;
    DECLARE room_id_value INT DEFAULT 0;
    DECLARE bed_base INT;
    DECLARE gender_value CHAR(1);
    DECLARE scope_value VARCHAR(32);
    WHILE building_index<=6 DO
        SET gender_value=IF(building_index IN (1,3,5),'M','F');
        SET scope_value=CASE
            WHEN building_index IN (1,2) THEN 'DOMESTIC_ONLY'
            WHEN building_index IN (3,4) THEN 'INTERNATIONAL_ONLY'
            ELSE 'MIXED' END;
        SET rooms_per_floor=CASE
            WHEN building_index IN (1,2) THEN 14
            WHEN building_index IN (3,4) THEN 4
            ELSE 8 END;
        SET floor_index=1;
        WHILE floor_index<=5 DO
            SET floor_id_value=(building_index-1)*5+floor_index;
            INSERT INTO dormitory_floor
            (id,building_id,floor_number,floor_name,enabled)
            VALUES(floor_id_value,building_index,floor_index,
                   CONCAT((SELECT building_name FROM dormitory_building WHERE id=building_index),floor_index,'层'),1);
            SET room_index=1;
            WHILE room_index<=rooms_per_floor DO
                SET room_id_value=room_id_value+1;
                SET bed_base=(room_id_value-1)*5;
                INSERT INTO room
                (id,floor_id,room_number,room_type,capacity,gender_restriction,
                 resident_scope,operational_status,state_version,remark,version)
                VALUES
                (room_id_value,floor_id_value,CONCAT(floor_index,LPAD(room_index,2,'0')),
                 'FIVE_PERSON',5,gender_value,scope_value,'ENABLED',0,
                 '三张上床下桌加一组上下铺',0);
                INSERT INTO bed_frame(id,room_id,frame_code,frame_type,enabled)
                VALUES(room_id_value,room_id_value,CONCAT('BF-',room_id_value,'-D'),'BUNK_FRAME',1);
                INSERT INTO bed
                (id,room_id,bed_frame_id,bed_code,bed_type,position_index,operational_status)
                VALUES
                (bed_base+1,room_id_value,NULL,'A','LOFT_BED_DESK',1,'ENABLED'),
                (bed_base+2,room_id_value,NULL,'B','LOFT_BED_DESK',2,'ENABLED'),
                (bed_base+3,room_id_value,NULL,'C','LOFT_BED_DESK',3,'ENABLED'),
                (bed_base+4,room_id_value,room_id_value,'D-UP','BUNK_UPPER',4,'ENABLED'),
                (bed_base+5,room_id_value,room_id_value,'D-LOW','BUNK_LOWER',5,'ENABLED');
                INSERT INTO room_bed_layout
                (bed_id,layout_x,layout_z,rotation_degrees,updated_by,version)
                VALUES
                (bed_base+1,-3.300,-1.700,90,@admin_id,0),
                (bed_base+2,0.000,-1.700,90,@admin_id,0),
                (bed_base+3,3.300,-1.700,90,@admin_id,0),
                (bed_base+4,-1.650,1.700,90,@admin_id,0),
                (bed_base+5,-1.650,1.700,90,@admin_id,0);
                SET room_index=room_index+1;
            END WHILE;
            SET floor_index=floor_index+1;
        END WHILE;
        SET building_index=building_index+1;
    END WHILE;
END$$
DELIMITER ;
CALL seed_1000_rooms();
DROP PROCEDURE seed_1000_rooms;

DROP PROCEDURE IF EXISTS seed_1000_students;
DELIMITER $$
CREATE PROCEDURE seed_1000_students()
BEGIN
    DECLARE i INT DEFAULT 1;
    DECLARE gender_value CHAR(1);
    DECLARE category_value VARCHAR(24);
    DECLARE nationality_value CHAR(2);
    DECLARE name_value VARCHAR(128);
    DECLARE source_value VARCHAR(32);
    WHILE i<=1000 DO
        SET gender_value=IF(i<=500,'M','F');
        SET category_value=IF(MOD(i-1,20)<17,'DOMESTIC','INTERNATIONAL');
        SET nationality_value=IF(category_value='DOMESTIC','CN',
            ELT(MOD(i,10)+1,'US','GB','JP','KR','FR','DE','IN','TH','EG','RU'));
        SET source_value=IF(MOD(i,20)=0,'TRANSFER_MANUAL','INITIAL_IMPORT');
        SET name_value=IF(category_value='DOMESTIC',
            CONCAT(IF(gender_value='M','男生','女生'),LPAD(i,4,'0')),
            CONCAT(ELT(MOD(i,10)+1,'Alex','Emma','Haruto','Minjun','Camille','Lukas','Ananya','Narin','Ahmed','Ivan'),' ',LPAD(i,4,'0')));
        INSERT INTO student
        (id,student_number,student_name,gender,major_id,nationality_code,
         student_category,enrollment_source,phone_number)
        VALUES
        (i,CONCAT('2026',LPAD(i,8,'0')),name_value,gender_value,
         1+MOD(i-1,12),nationality_value,category_value,source_value,
         IF(category_value='DOMESTIC',CONCAT('+86 13',MOD(i,10),' ',LPAD(10000000+i,8,'0')),
            CONCAT('+',20+MOD(i,70),' ',LPAD(10000000+i,8,'0'))));
        INSERT INTO app_user
        (student_id,username,password_hash,user_type,account_status,
         display_name,password_change_required,version)
        VALUES
        (i,CONCAT('2026',LPAD(i,8,'0')),NULL,'STUDENT','PENDING',name_value,0,0);
        SET i=i+1;
    END WHILE;
END$$
DELIMITER ;
CALL seed_1000_students();
DROP PROCEDURE seed_1000_students;

-- 让本地完整套餐明确包含选床模式权限。
INSERT INTO plan_revision_feature(plan_revision_id,feature_code)
SELECT pr.id,'P2_BED_SELECTION_MODE'
FROM subscription_plan_revision pr
JOIN subscription_plan p ON p.id=pr.plan_id
WHERE p.plan_code='FULL_CURRENT' AND pr.revision=1
ON DUPLICATE KEY UPDATE feature_code=VALUES(feature_code);

SELECT 'BASE_READY' AS status,
       (SELECT COUNT(*) FROM student) AS students,
       (SELECT COUNT(*) FROM room) AS rooms,
       (SELECT COUNT(*) FROM bed) AS beds,
       (SELECT COUNT(*) FROM room WHERE resident_scope='DOMESTIC_ONLY') AS domestic_rooms,
       (SELECT COUNT(*) FROM room WHERE resident_scope='INTERNATIONAL_ONLY') AS international_rooms,
       (SELECT COUNT(*) FROM room WHERE resident_scope='MIXED') AS mixed_rooms;
