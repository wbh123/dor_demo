-- ============================================================
-- Navicat 测试数据导入入口
-- 统一数据库：wust_dormitory
-- 后续主数据脚本会清空学校业务数据并重新生成测试数据。
-- ============================================================
SET NAMES utf8mb4;
USE `wust_dormitory`;
SET @database_name = DATABASE();
SELECT DATABASE() AS current_database, 'TEST_DATA_IMPORT_READY' AS status;
