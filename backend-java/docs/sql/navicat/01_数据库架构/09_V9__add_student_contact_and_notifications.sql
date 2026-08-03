-- ============================================================
-- V9：国际学生资料、多语言欢迎语与学生系统通知
-- ============================================================

ALTER TABLE student
    ADD COLUMN nationality_code CHAR(2) NOT NULL DEFAULT 'CN'
        COMMENT 'ISO 3166-1 alpha-2国籍代码' AFTER major_id,
    ADD COLUMN phone_number VARCHAR(32) NULL
        COMMENT '学生本人可维护的手机号码' AFTER nationality_code,
    ADD CONSTRAINT ck_student_nationality_code
        CHECK (nationality_code REGEXP '^[A-Z]{2}$'),
    ADD CONSTRAINT ck_student_phone_number
        CHECK (phone_number IS NULL OR phone_number REGEXP '^\\+?[0-9][0-9 -]{5,30}$'),
    ADD KEY idx_student_nationality (nationality_code);

CREATE TABLE student_notification (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '学生系统通知主键',
    student_id BIGINT NOT NULL COMMENT '接收学生主键',
    notification_type VARCHAR(64) NOT NULL COMMENT '通知类型',
    title_key VARCHAR(128) NOT NULL COMMENT '前端国际化标题键',
    message_key VARCHAR(128) NOT NULL COMMENT '前端国际化正文键',
    parameters_json JSON NOT NULL COMMENT '消息插值参数',
    read_at DATETIME(3) NULL COMMENT '阅读时间',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    CONSTRAINT fk_student_notification_student
        FOREIGN KEY (student_id) REFERENCES student(id) ON DELETE CASCADE,
    CONSTRAINT ck_student_notification_type
        CHECK (notification_type IN ('TEAM_MEMBER_REMOVED','TEAM_DISSOLVED','TEAM_INVITATION_CANCELLED')),
    KEY idx_student_notification_unread (student_id, read_at, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='学生系统通知';

-- 兼容V7中的纯文本欢迎语，将其升级为中英双语JSON。
UPDATE system_setting
SET setting_value = JSON_OBJECT(
        'zh-CN', setting_value,
        'en-US', 'Welcome to the Wuhan University of Science and Technology dormitory selection system. Complete your personal preferences first, then choose a suitable room and bed.'
    ),
    version = version + 1
WHERE setting_key = 'STUDENT_WELCOME_MESSAGE'
  AND JSON_VALID(setting_value) = 0;
