-- ============================================================
-- 第一阶段学生与专业结构优化
--
-- 目标：
-- 1. 学生业务信息只保留学号、姓名、性别、专业外键；
-- 2. 专业信息独立维护，避免在学生表重复保存专业名称；
-- 3. 账号关联迁移到 app_user.student_id；
-- 4. 删除班级、年级、校区、组织、全局住宿资格等冗余字段；
-- 5. 住宿资格继续由 batch_student_eligibility 按批次维护；
-- 6. 数据导入任务支持专业目录导入。
-- ============================================================

CREATE TABLE major (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '专业主键',
    major_code VARCHAR(32) NOT NULL COMMENT '专业编号',
    major_name VARCHAR(128) NOT NULL COMMENT '专业名称',
    enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    CONSTRAINT ck_major_enabled CHECK (enabled IN (0,1)),
    UNIQUE KEY uk_major_code (major_code),
    UNIQUE KEY uk_major_name (major_name),
    KEY idx_major_enabled (enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='专业基础信息';

-- 兼容已经执行过V1、V2且存在学生数据的开发库。
-- 旧表只有专业名称，没有专业编号，因此使用稳定哈希生成迁移编号；
-- 后续管理员导入正式专业目录时可以更新为学校真实专业编号。
INSERT INTO major (major_code, major_name, enabled)
SELECT
    CONCAT('LEGACY-', UPPER(SUBSTRING(SHA2(major_name, 256), 1, 16))),
    major_name,
    1
FROM student
GROUP BY major_name;

ALTER TABLE student
    ADD COLUMN major_id BIGINT NULL COMMENT '专业主键' AFTER gender;

UPDATE student s
JOIN major m ON m.major_name = s.major_name
SET s.major_id = m.id;

ALTER TABLE app_user
    ADD COLUMN student_id BIGINT NULL COMMENT '学生主键；管理员为空' AFTER id;

UPDATE app_user u
JOIN student s
  ON u.user_type = 'STUDENT'
 AND u.username = s.student_number
SET u.student_id = s.id;

ALTER TABLE student
    DROP FOREIGN KEY fk_student_user,
    DROP FOREIGN KEY fk_student_organization,
    DROP FOREIGN KEY fk_student_campus,
    DROP INDEX uk_student_user,
    DROP INDEX idx_student_org,
    DROP INDEX idx_student_campus_gender,
    DROP INDEX idx_student_eligibility,
    DROP CHECK ck_student_grade,
    DROP CHECK ck_student_eligibility,
    DROP CHECK ck_student_profile_status,
    DROP CHECK ck_student_data_source;

ALTER TABLE student
    MODIFY COLUMN major_id BIGINT NOT NULL COMMENT '专业主键',
    ADD CONSTRAINT fk_student_major
        FOREIGN KEY (major_id) REFERENCES major(id) ON DELETE RESTRICT,
    ADD KEY idx_student_major_gender (major_id, gender),
    DROP COLUMN user_id,
    DROP COLUMN organization_id,
    DROP COLUMN campus_id,
    DROP COLUMN grade_year,
    DROP COLUMN major_name,
    DROP COLUMN class_name,
    DROP COLUMN housing_eligibility,
    DROP COLUMN profile_status,
    DROP COLUMN data_source,
    DROP COLUMN version;

ALTER TABLE app_user
    ADD CONSTRAINT fk_app_user_student
        FOREIGN KEY (student_id) REFERENCES student(id) ON DELETE RESTRICT,
    ADD UNIQUE KEY uk_app_user_student (student_id);

ALTER TABLE import_job
    DROP CHECK ck_import_type,
    ADD CONSTRAINT ck_import_type
        CHECK (import_type IN ('MAJOR','STUDENT','DORMITORY'));

DROP TABLE organization;
