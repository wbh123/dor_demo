-- V17：恢复运行必需的系统配置，并修复测试数据清理可能造成的配置创建人孤儿引用。
-- 已执行迁移保持不可变，本迁移只做幂等修复。

INSERT INTO system_setting
(setting_key, setting_value, version, updated_by)
SELECT
    'STUDENT_WELCOME_MESSAGE',
    JSON_OBJECT(
        'zh-CN', '欢迎使用武汉科技大学学生宿舍智能选择系统。请先完成个人偏好，再选择合适的宿舍或床位。',
        'en-US', 'Welcome to the Wuhan University of Science and Technology dormitory selection system. Complete your personal preferences first, then choose a suitable room or bed.'
    ),
    0,
    NULL
WHERE NOT EXISTS (
    SELECT 1
    FROM system_setting
    WHERE setting_key = 'STUDENT_WELCOME_MESSAGE'
);

UPDATE system_setting setting
LEFT JOIN app_user user_record ON user_record.id = setting.updated_by
SET setting.updated_by = NULL
WHERE setting.updated_by IS NOT NULL
  AND user_record.id IS NULL;

UPDATE matching_weight_scheme scheme
LEFT JOIN app_user user_record ON user_record.id = scheme.created_by
SET scheme.created_by = NULL
WHERE scheme.created_by IS NOT NULL
  AND user_record.id IS NULL;

UPDATE batch_rule_template template
LEFT JOIN app_user user_record ON user_record.id = template.created_by
SET template.created_by = NULL
WHERE template.created_by IS NOT NULL
  AND user_record.id IS NULL;

INSERT INTO batch_rule_template
(rule_code, rule_name, revision,
 hold_duration_seconds, hold_renewal_limit,
 allow_team, team_min_size, team_max_size,
 allow_student_random, unselected_strategy, rule_version,
 enabled, is_default, created_by, change_reason)
SELECT
    'SYSTEM_DEFAULT', '系统默认选寝规则', 1,
    300, 1,
    1, 2, 5,
    1, 'ADMIN_ALLOCATION', 'phase2-rule-template-v1',
    1,
    CASE
        WHEN EXISTS (SELECT 1 FROM batch_rule_template WHERE is_default = 1) THEN 0
        ELSE 1
    END,
    NULL,
    'Flyway V17恢复缺失的系统默认规则模板'
WHERE NOT EXISTS (
    SELECT 1
    FROM batch_rule_template
    WHERE rule_code = 'SYSTEM_DEFAULT' AND revision = 1
);
