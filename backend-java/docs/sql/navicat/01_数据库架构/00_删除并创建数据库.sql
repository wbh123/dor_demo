-- ============================================================
-- Navicat 数据库架构初始化
-- 统一数据库：wust_dormitory
-- 警告：执行后会删除同名数据库及其中全部数据。
-- ============================================================
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;
DROP DATABASE IF EXISTS `wust_dormitory`;
CREATE DATABASE `wust_dormitory`
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;
USE `wust_dormitory`;
SET FOREIGN_KEY_CHECKS = 1;
SELECT DATABASE() AS current_database, 'DATABASE_RECREATED' AS status;
