-- ============================================================================
-- 武汉科技大学学生宿舍智能选择系统
-- Navicat 数据库完整性修复与检查
-- 数据库：wust_dormitory
-- 适用版本：V18
--
-- 先幂等恢复必要单例，再检查结构、参考数据、外键语义与业务不变量。
-- 任一检查失败会通过 SIGNAL 中止；全部通过输出 DB_INTEGRITY_OK。
-- ============================================================================

USE `wust_dormitory`;
SET NAMES utf8mb4;

-- 兼容旧测试库：恢复欢迎语并清除配置表孤儿创建人。
INSERT INTO system_setting
(setting_key,setting_value,version,updated_by)
SELECT
    'STUDENT_WELCOME_MESSAGE',
    JSON_OBJECT(
        'zh-CN','欢迎使用武汉科技大学学生宿舍智能选择系统。请先完成个人偏好，再选择合适的宿舍或床位。',
        'en-US','Welcome to the Wuhan University of Science and Technology dormitory selection system. Complete your personal preferences first, then choose a suitable room or bed.'
    ),
    0,
    NULL
WHERE NOT EXISTS (
    SELECT 1 FROM system_setting WHERE setting_key='STUDENT_WELCOME_MESSAGE'
);

UPDATE system_setting setting
LEFT JOIN app_user user_record ON user_record.id=setting.updated_by
SET setting.updated_by=NULL
WHERE setting.updated_by IS NOT NULL AND user_record.id IS NULL;

UPDATE matching_weight_scheme scheme
LEFT JOIN app_user user_record ON user_record.id=scheme.created_by
SET scheme.created_by=NULL
WHERE scheme.created_by IS NOT NULL AND user_record.id IS NULL;

UPDATE batch_rule_template template
LEFT JOIN app_user user_record ON user_record.id=template.created_by
SET template.created_by=NULL
WHERE template.created_by IS NOT NULL AND user_record.id IS NULL;

INSERT INTO batch_rule_template
(rule_code,rule_name,revision,hold_duration_seconds,hold_renewal_limit,
 allow_team,team_min_size,team_max_size,allow_student_random,
 unselected_strategy,rule_version,enabled,is_default,created_by,change_reason)
SELECT
    'SYSTEM_DEFAULT','系统默认选寝规则',1,300,1,
    1,2,5,1,'ADMIN_ALLOCATION','phase2-rule-template-v1',
    1,CASE WHEN EXISTS(SELECT 1 FROM batch_rule_template WHERE is_default=1) THEN 0 ELSE 1 END,
    NULL,'Navicat完整性检查恢复缺失的系统默认规则模板'
WHERE NOT EXISTS (
    SELECT 1 FROM batch_rule_template
    WHERE rule_code='SYSTEM_DEFAULT' AND revision=1
);

DROP PROCEDURE IF EXISTS check_wust_dormitory_integrity;
DELIMITER $$
CREATE PROCEDURE check_wust_dormitory_integrity()
BEGIN
    DECLARE issue_count INT DEFAULT 0;
    DECLARE issue_detail VARCHAR(1000) DEFAULT '';
    DECLARE published_questionnaire_id BIGINT DEFAULT NULL;

    IF DATABASE()<>'wust_dormitory' THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT='当前数据库不是wust_dormitory，请重新选择数据库';
    END IF;

    -- 一、关键表。
    DROP TEMPORARY TABLE IF EXISTS required_integrity_table;
    CREATE TEMPORARY TABLE required_integrity_table(table_name VARCHAR(128) PRIMARY KEY);
    INSERT INTO required_integrity_table(table_name) VALUES
    ('flyway_schema_history'),('major'),('app_user'),('student'),('student_notification'),
    ('import_job'),('import_error'),('campus'),('dormitory_building'),('dormitory_floor'),
    ('room'),('bed_frame'),('bed'),('room_bed_layout'),
    ('questionnaire_version'),('questionnaire_question'),('questionnaire_option'),
    ('questionnaire_answer'),('student_feature'),('matching_weight_scheme'),
    ('batch_rule_template'),('selection_batch'),('batch_student_eligibility'),
    ('active_batch_student_lock'),('active_batch_room_lock'),
    ('batch_building_scope'),('batch_room_scope'),('batch_bed_scope'),
    ('selection_team'),('selection_team_member'),('team_invitation'),
    ('bed_assignment'),('assignment_history'),('allocation_run'),('allocation_run_result'),
    ('room_assignment'),('room_assignment_history'),('system_setting'),('audit_log'),
    ('feature_catalog'),('quota_catalog'),('subscription_plan'),
    ('subscription_plan_revision'),('plan_revision_feature'),('plan_revision_quota'),
    ('service_subscription'),('service_subscription_revision'),
    ('subscription_feature_override'),('subscription_quota_override'),
    ('service_quota_alert'),('batch_entitlement_snapshot'),('platform_audit_log');

    SELECT COUNT(*),COALESCE(GROUP_CONCAT(required.table_name ORDER BY required.table_name),'')
    INTO issue_count,issue_detail
    FROM required_integrity_table required
    LEFT JOIN information_schema.tables actual
      ON actual.table_schema=DATABASE()
     AND actual.table_name=required.table_name
     AND actual.table_type='BASE TABLE'
    WHERE actual.table_name IS NULL;
    IF issue_count>0 THEN
        SET issue_detail=LEFT(CONCAT('缺少数据表：',issue_detail),128);
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT=issue_detail;
    END IF;

    SELECT COUNT(*) INTO issue_count
    FROM flyway_schema_history WHERE version='18' AND success=1;
    IF issue_count<>1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT='Flyway基线或迁移历史未标记V18成功';
    END IF;

    -- 二、关键字段。
    DROP TEMPORARY TABLE IF EXISTS required_integrity_column;
    CREATE TEMPORARY TABLE required_integrity_column(
        table_name VARCHAR(128),column_name VARCHAR(128),PRIMARY KEY(table_name,column_name));
    INSERT INTO required_integrity_column(table_name,column_name) VALUES
    ('app_user','student_id'),('app_user','welcome_acknowledged_at'),
    ('app_user','password_change_required'),('app_user','system_admin_marker'),
    ('student','major_id'),('student','nationality_code'),
    ('student','student_category'),('student','enrollment_source'),
    ('room','resident_scope'),('selection_batch','selection_mode'),
    ('selection_batch','separate_student_categories'),
    ('batch_student_eligibility','source_type'),
    ('room_assignment','bed_id'),('room_assignment','source_selection_mode'),
    ('room_assignment','active_student_marker'),('room_assignment','active_bed_marker'),
    ('room_assignment','ended_at'),('system_setting','setting_key'),
    ('system_setting','setting_value'),('system_setting','version'),
    ('system_setting','updated_by'),('matching_weight_scheme','revision'),
    ('matching_weight_scheme','weights_json'),('batch_rule_template','revision');

    SELECT COUNT(*),COALESCE(GROUP_CONCAT(CONCAT(required.table_name,'.',required.column_name)
           ORDER BY required.table_name,required.column_name),'')
    INTO issue_count,issue_detail
    FROM required_integrity_column required
    LEFT JOIN information_schema.columns actual
      ON actual.table_schema=DATABASE()
     AND actual.table_name=required.table_name
     AND actual.column_name=required.column_name
    WHERE actual.column_name IS NULL;
    IF issue_count>0 THEN
        SET issue_detail=LEFT(CONCAT('缺少关键字段：',issue_detail),128);
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT=issue_detail;
    END IF;

    -- 三、关键唯一索引。
    DROP TEMPORARY TABLE IF EXISTS required_integrity_index;
    CREATE TEMPORARY TABLE required_integrity_index(
        table_name VARCHAR(128),index_name VARCHAR(128),PRIMARY KEY(table_name,index_name));
    INSERT INTO required_integrity_index(table_name,index_name) VALUES
    ('app_user','uk_single_system_admin'),('system_setting','uk_system_setting_key'),
    ('active_batch_student_lock','PRIMARY'),('active_batch_room_lock','PRIMARY'),
    ('room_assignment','uk_active_residency_student'),
    ('room_assignment','uk_active_residency_bed'),
    ('service_subscription_revision','uk_subscription_current'),
    ('matching_weight_scheme','uk_weight_scheme_revision');

    SELECT COUNT(*),COALESCE(GROUP_CONCAT(CONCAT(required.table_name,'.',required.index_name)
           ORDER BY required.table_name,required.index_name),'')
    INTO issue_count,issue_detail
    FROM required_integrity_index required
    LEFT JOIN information_schema.statistics actual
      ON actual.table_schema=DATABASE()
     AND actual.table_name=required.table_name
     AND actual.index_name=required.index_name
    WHERE actual.index_name IS NULL;
    IF issue_count>0 THEN
        SET issue_detail=LEFT(CONCAT('缺少关键索引：',issue_detail),128);
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT=issue_detail;
    END IF;

    -- 四、关键外键。
    DROP TEMPORARY TABLE IF EXISTS required_integrity_fk;
    CREATE TEMPORARY TABLE required_integrity_fk(constraint_name VARCHAR(128) PRIMARY KEY);
    INSERT INTO required_integrity_fk(constraint_name) VALUES
    ('fk_app_user_student'),('fk_student_major'),('fk_bed_room'),
    ('fk_room_bed_layout_bed'),('fk_system_setting_updated_by'),
    ('fk_active_batch_lock_student'),('fk_active_batch_lock_batch'),
    ('fk_active_batch_room_room'),('fk_active_batch_room_batch'),
    ('fk_room_assignment_student'),('fk_room_assignment_room'),('fk_room_assignment_bed'),
    ('fk_matching_weight_scheme_creator'),
    ('fk_subscription_revision_subscription'),('fk_subscription_revision_plan');

    SELECT COUNT(*),COALESCE(GROUP_CONCAT(required.constraint_name ORDER BY required.constraint_name),'')
    INTO issue_count,issue_detail
    FROM required_integrity_fk required
    LEFT JOIN information_schema.referential_constraints actual
      ON actual.constraint_schema=DATABASE()
     AND actual.constraint_name=required.constraint_name
    WHERE actual.constraint_name IS NULL;
    IF issue_count>0 THEN
        SET issue_detail=LEFT(CONCAT('缺少关键外键：',issue_detail),128);
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT=issue_detail;
    END IF;

    -- 五、运行必需单例与目录。
    SELECT COUNT(*) INTO issue_count FROM app_user WHERE user_type='SYSTEM_ADMIN';
    IF issue_count<>1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='SYSTEM_ADMIN数量必须恰好为1';
    END IF;

    SELECT COUNT(*) INTO issue_count
    FROM system_setting WHERE setting_key='STUDENT_WELCOME_MESSAGE';
    IF issue_count<>1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='学生欢迎语配置必须恰好为1条';
    END IF;
    IF EXISTS(
        SELECT 1 FROM system_setting
        WHERE setting_key='STUDENT_WELCOME_MESSAGE'
          AND CASE
              WHEN JSON_VALID(setting_value)=0 THEN 1
              WHEN JSON_TYPE(setting_value)<>'OBJECT' THEN 1
              WHEN COALESCE(JSON_UNQUOTE(JSON_EXTRACT(setting_value,'$."zh-CN"')),'')='' THEN 1
              WHEN COALESCE(JSON_UNQUOTE(JSON_EXTRACT(setting_value,'$."en-US"')),'')='' THEN 1
              ELSE 0 END=1
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT='学生欢迎语必须是包含zh-CN和en-US的JSON对象';
    END IF;

    IF NOT EXISTS(SELECT 1 FROM feature_catalog WHERE feature_code='P1_IDENTITY_BASIC' AND enabled_in_program=1)
       OR NOT EXISTS(SELECT 1 FROM feature_catalog WHERE feature_code='P1_DORMITORY_BASIC' AND enabled_in_program=1)
       OR NOT EXISTS(SELECT 1 FROM feature_catalog WHERE feature_code='P1_SELF_SELECTION' AND enabled_in_program=1)
       OR NOT EXISTS(SELECT 1 FROM feature_catalog WHERE feature_code='P2_BED_SELECTION_MODE' AND enabled_in_program=1) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='核心功能权限目录不完整';
    END IF;

    IF NOT EXISTS(SELECT 1 FROM quota_catalog WHERE quota_code='MAX_STUDENTS')
       OR NOT EXISTS(SELECT 1 FROM quota_catalog WHERE quota_code='MAX_ROOMS')
       OR NOT EXISTS(SELECT 1 FROM quota_catalog WHERE quota_code='MAX_BEDS')
       OR NOT EXISTS(SELECT 1 FROM quota_catalog WHERE quota_code='MAX_BATCHES_PER_YEAR') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='核心资源配额目录不完整';
    END IF;

    IF NOT EXISTS(SELECT 1 FROM subscription_plan WHERE plan_code='FULL_CURRENT') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='缺少FULL_CURRENT默认套餐';
    END IF;
    IF NOT EXISTS(SELECT 1 FROM service_subscription WHERE subscription_code='PRIMARY_SERVICE') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='缺少PRIMARY_SERVICE服务订阅';
    END IF;
    SELECT COUNT(*) INTO issue_count FROM service_subscription_revision WHERE is_current=1;
    IF issue_count<>1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='当前订阅修订必须恰好为1条';
    END IF;
    IF NOT EXISTS(
        SELECT 1 FROM batch_rule_template
        WHERE rule_code='SYSTEM_DEFAULT' AND revision=1 AND enabled=1
    ) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='缺少可用的系统默认批次规则模板';
    END IF;

    -- 六、批次创建所需业务参考数据。
    SET published_questionnaire_id=(
        SELECT id FROM questionnaire_version
        WHERE version_status='PUBLISHED'
        ORDER BY published_at DESC,id DESC LIMIT 1
    );
    IF published_questionnaire_id IS NULL THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='缺少已发布的个人偏好问卷';
    END IF;
    SELECT COUNT(*) INTO issue_count
    FROM questionnaire_question
    WHERE questionnaire_version_id=published_questionnaire_id AND enabled=1;
    IF issue_count=0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='已发布问卷没有可用题目';
    END IF;
    SELECT COUNT(*) INTO issue_count
    FROM questionnaire_question question
    WHERE question.questionnaire_version_id=published_questionnaire_id
      AND question.enabled=1
      AND question.question_type='SINGLE_CHOICE'
      AND NOT EXISTS(
          SELECT 1 FROM questionnaire_option option_record
          WHERE option_record.question_id=question.id AND option_record.enabled=1
      );
    IF issue_count>0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='已发布问卷存在没有选项的单选题';
    END IF;

    SELECT COUNT(*) INTO issue_count
    FROM matching_weight_scheme
    WHERE enabled=1
      AND JSON_VALID(weights_json)=1
      AND JSON_TYPE(weights_json)='OBJECT'
      AND JSON_VALID(conflict_rules_json)=1
      AND JSON_TYPE(conflict_rules_json)='OBJECT';
    IF issue_count=0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='缺少已启用的匹配权重方案';
    END IF;

    -- 七、孤儿引用与业务一致性。
    SELECT COUNT(*) INTO issue_count
    FROM app_user user_record
    LEFT JOIN student student_record ON student_record.id=user_record.student_id
    WHERE user_record.student_id IS NOT NULL AND student_record.id IS NULL;
    IF issue_count>0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='app_user存在无效student_id引用';
    END IF;

    SELECT COUNT(*) INTO issue_count
    FROM student student_record
    LEFT JOIN major major_record ON major_record.id=student_record.major_id
    WHERE major_record.id IS NULL;
    IF issue_count>0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='student存在无效major_id引用';
    END IF;

    SELECT COUNT(*) INTO issue_count
    FROM bed bed_record
    LEFT JOIN room room_record ON room_record.id=bed_record.room_id
    WHERE room_record.id IS NULL;
    IF issue_count>0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='bed存在无效room_id引用';
    END IF;

    SELECT COUNT(*) INTO issue_count
    FROM room_bed_layout layout_record
    LEFT JOIN bed bed_record ON bed_record.id=layout_record.bed_id
    LEFT JOIN app_user user_record ON user_record.id=layout_record.updated_by
    WHERE bed_record.id IS NULL OR user_record.id IS NULL;
    IF issue_count>0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='room_bed_layout存在无效床位或操作人引用';
    END IF;

    SELECT COUNT(*) INTO issue_count
    FROM room_assignment residency
    LEFT JOIN student student_record ON student_record.id=residency.student_id
    LEFT JOIN room room_record ON room_record.id=residency.room_id
    LEFT JOIN bed bed_record ON bed_record.id=residency.bed_id
    WHERE student_record.id IS NULL OR room_record.id IS NULL
       OR (residency.bed_id IS NOT NULL
           AND (bed_record.id IS NULL OR bed_record.room_id<>residency.room_id));
    IF issue_count>0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='room_assignment存在无效学生、寝室或床位引用';
    END IF;

    SELECT COUNT(*) INTO issue_count
    FROM system_setting setting
    LEFT JOIN app_user user_record ON user_record.id=setting.updated_by
    WHERE setting.updated_by IS NOT NULL AND user_record.id IS NULL;
    IF issue_count>0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='system_setting存在无效updated_by引用';
    END IF;

    SELECT COUNT(*) INTO issue_count
    FROM matching_weight_scheme scheme
    LEFT JOIN app_user user_record ON user_record.id=scheme.created_by
    WHERE scheme.created_by IS NOT NULL AND user_record.id IS NULL;
    IF issue_count>0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='matching_weight_scheme存在无效created_by引用';
    END IF;

    SELECT COUNT(*) INTO issue_count
    FROM batch_rule_template template
    LEFT JOIN app_user user_record ON user_record.id=template.created_by
    WHERE template.created_by IS NOT NULL AND user_record.id IS NULL;
    IF issue_count>0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='batch_rule_template存在无效created_by引用';
    END IF;

    SELECT COUNT(*) INTO issue_count
    FROM active_batch_student_lock student_lock
    JOIN selection_batch batch_record ON batch_record.id=student_lock.batch_id
    WHERE batch_record.batch_status NOT IN('PUBLISHED','OPEN','PAUSED');
    IF issue_count>0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='存在非活动批次的学生互斥锁';
    END IF;

    SELECT COUNT(*) INTO issue_count
    FROM active_batch_room_lock room_lock
    JOIN selection_batch batch_record ON batch_record.id=room_lock.batch_id
    WHERE batch_record.batch_status NOT IN('PUBLISHED','OPEN','PAUSED')
       OR batch_record.selection_mode<>room_lock.selection_mode;
    IF issue_count>0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='存在无效的活动寝室锁或模式不一致';
    END IF;

    SELECT COUNT(*) INTO issue_count
    FROM(
        SELECT student_id FROM room_assignment
        WHERE assignment_status='ACTIVE'
        GROUP BY student_id HAVING COUNT(*)>1
    ) duplicated_student;
    IF issue_count>0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='同一学生存在多条有效在住记录';
    END IF;

    SELECT COUNT(*) INTO issue_count
    FROM(
        SELECT bed_id FROM room_assignment
        WHERE assignment_status='ACTIVE' AND bed_id IS NOT NULL
        GROUP BY bed_id HAVING COUNT(*)>1
    ) duplicated_bed;
    IF issue_count>0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='同一现实床位存在多名有效住户';
    END IF;
END$$
DELIMITER ;

CALL check_wust_dormitory_integrity();
DROP PROCEDURE check_wust_dormitory_integrity;

SELECT DATABASE() AS current_database,
       'V18' AS expected_schema_version,
       (SELECT COUNT(*) FROM information_schema.tables
        WHERE table_schema=DATABASE() AND table_type='BASE TABLE') AS table_count,
       (SELECT COUNT(*) FROM system_setting
        WHERE setting_key='STUDENT_WELCOME_MESSAGE') AS welcome_setting_count,
       (SELECT COUNT(*) FROM questionnaire_version WHERE version_status='PUBLISHED') AS published_questionnaire_count,
       (SELECT COUNT(*) FROM matching_weight_scheme WHERE enabled=1) AS enabled_matching_scheme_count,
       (SELECT COUNT(*) FROM feature_catalog WHERE enabled_in_program=1) AS implemented_feature_count,
       (SELECT COUNT(*) FROM quota_catalog) AS quota_count,
       (SELECT COUNT(*) FROM service_subscription_revision WHERE is_current=1) AS current_subscription_count,
       'DB_INTEGRITY_OK' AS status;
