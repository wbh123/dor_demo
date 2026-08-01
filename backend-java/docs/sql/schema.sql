-- ============================================================
-- 可复用框架数据库结构模板
-- 数据库：MySQL 8.0
-- 字符集：utf8mb4
-- ============================================================

SET NAMES utf8mb4;

CREATE DATABASE IF NOT EXISTS framework_template
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE framework_template;

CREATE TABLE IF NOT EXISTS example_table (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    example_code VARCHAR(64) NOT NULL COMMENT '示例编码',
    example_name VARCHAR(128) NOT NULL COMMENT '显示名称',
    enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用：1 启用，0 禁用',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_example_code (example_code),
    KEY idx_enabled (enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MyBatis Generator 模板示例表';
