-- ============================================================
-- Navicat 全量建库后的 Flyway V17 基线
-- 统一数据库：wust_dormitory
-- 后续新增 V18 及更高版本时，应用可继续正常迁移。
-- ============================================================
USE `wust_dormitory`;
DROP TABLE IF EXISTS flyway_schema_history;
CREATE TABLE flyway_schema_history (
    installed_rank INT NOT NULL,
    version VARCHAR(50) NULL,
    description VARCHAR(200) NOT NULL,
    type VARCHAR(20) NOT NULL,
    script VARCHAR(1000) NOT NULL,
    checksum INT NULL,
    installed_by VARCHAR(100) NOT NULL,
    installed_on TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    execution_time INT NOT NULL,
    success TINYINT(1) NOT NULL,
    PRIMARY KEY (installed_rank),
    INDEX flyway_schema_history_s_idx (success)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
INSERT INTO flyway_schema_history
(installed_rank,version,description,type,script,checksum,installed_by,execution_time,success)
VALUES
(1,'17','Navicat full schema baseline','BASELINE','<< Flyway Baseline >>',NULL,
 SUBSTRING_INDEX(CURRENT_USER(),'@',1),0,1);
SELECT DATABASE() AS current_database,
       'V17' AS schema_version,
       'FLYWAY_BASELINE_READY' AS status;
