-- 仅用于全新本地数据库在导入全量测试数据前建立管理员账号。
-- 如果admin账号已经存在，本脚本不会修改其主键、密码哈希或状态。
SET NAMES utf8mb4;

INSERT INTO app_user
(student_id, username, password_hash, user_type, account_status, display_name)
SELECT NULL, 'admin', '{noop}Dormitory@2026', 'ADMIN', 'ACTIVE', '测试管理员'
WHERE NOT EXISTS (
    SELECT 1 FROM app_user
    WHERE username='admin' AND user_type='ADMIN'
);
