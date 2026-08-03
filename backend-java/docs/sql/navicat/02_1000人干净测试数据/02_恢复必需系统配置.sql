-- 1000人干净数据导入后的系统配置恢复
USE `wust_dormitory`;
SET NAMES utf8mb4;

-- 配额告警是由当前业务数据推导的状态，重置数据后必须清空并重新计算。
DELETE FROM service_quota_alert;

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

SELECT DATABASE() AS current_database,
       (SELECT COUNT(*) FROM system_setting
        WHERE setting_key='STUDENT_WELCOME_MESSAGE') AS welcome_setting_count,
       (SELECT COUNT(*) FROM service_quota_alert) AS quota_alert_count,
       'SYSTEM_CONFIGURATION_READY' AS status;
