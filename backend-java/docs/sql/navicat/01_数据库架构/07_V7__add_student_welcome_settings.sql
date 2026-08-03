-- ============================================================
-- V7：学生首次登录欢迎确认与系统级欢迎语配置
-- ============================================================

ALTER TABLE app_user
    ADD COLUMN welcome_acknowledged_at DATETIME(3) NULL
        COMMENT '学生首次欢迎浮窗确认时间' AFTER last_login_at;

CREATE TABLE system_setting (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '系统设置主键',
    setting_key VARCHAR(64) NOT NULL COMMENT '设置键',
    setting_value VARCHAR(1000) NOT NULL COMMENT '设置值',
    version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    updated_by BIGINT NULL COMMENT '最后修改管理员',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    CONSTRAINT fk_system_setting_updated_by
        FOREIGN KEY (updated_by) REFERENCES app_user(id) ON DELETE RESTRICT,
    CONSTRAINT ck_system_setting_value_not_blank
        CHECK (CHAR_LENGTH(TRIM(setting_value)) BETWEEN 1 AND 1000),
    UNIQUE KEY uk_system_setting_key (setting_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='系统级可配置文本与参数';

INSERT INTO system_setting
(setting_key, setting_value, version, updated_by)
VALUES
('STUDENT_WELCOME_MESSAGE',
 '欢迎加入武汉科技大学宿舍智能选择系统。请先完善个人偏好，再根据楼层、剩余铺位和室友匹配情况选择合适的宿舍与床位。',
 0, NULL);
