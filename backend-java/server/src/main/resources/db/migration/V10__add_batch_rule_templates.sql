-- V10: add batch rule templates

CREATE TABLE batch_rule_template (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '批次规则模板修订主键',
    rule_code VARCHAR(32) NOT NULL COMMENT '规则模板编码',
    rule_name VARCHAR(128) NOT NULL COMMENT '规则模板名称',
    revision INT NOT NULL COMMENT '不可变修订号',
    hold_duration_seconds INT NOT NULL COMMENT '临时占用秒数',
    hold_renewal_limit SMALLINT NOT NULL COMMENT '最大续期次数',
    allow_team TINYINT NOT NULL COMMENT '是否允许组队',
    team_min_size SMALLINT NOT NULL COMMENT '队伍最小人数',
    team_max_size SMALLINT NOT NULL COMMENT '队伍最大人数',
    allow_student_random TINYINT NOT NULL COMMENT '是否允许学生随机推荐',
    unselected_strategy VARCHAR(32) NOT NULL COMMENT '未选学生处理策略',
    rule_version VARCHAR(32) NOT NULL COMMENT '规则执行版本',
    enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否可供新批次选择',
    is_default TINYINT NOT NULL DEFAULT 0 COMMENT '是否为默认修订',
    default_marker TINYINT GENERATED ALWAYS AS
        (CASE WHEN is_default = 1 THEN 1 ELSE NULL END) STORED,
    created_by BIGINT NULL COMMENT '创建管理员；系统迁移可为空',
    change_reason VARCHAR(500) NOT NULL COMMENT '创建或修订原因',
    version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    CONSTRAINT fk_batch_rule_template_creator
        FOREIGN KEY (created_by) REFERENCES app_user(id) ON DELETE SET NULL,
    CONSTRAINT ck_batch_rule_template_revision CHECK (revision >= 1),
    CONSTRAINT ck_batch_rule_template_hold CHECK
        (hold_duration_seconds BETWEEN 30 AND 3600),
    CONSTRAINT ck_batch_rule_template_renewal CHECK
        (hold_renewal_limit BETWEEN 0 AND 20),
    CONSTRAINT ck_batch_rule_template_allow_team CHECK (allow_team IN (0,1)),
    CONSTRAINT ck_batch_rule_template_team_size CHECK (
        (allow_team = 0 AND team_min_size = 1 AND team_max_size = 1)
        OR
        (allow_team = 1
         AND team_min_size BETWEEN 2 AND team_max_size
         AND (
             (enabled = 1 AND team_max_size <= 5)
             OR (enabled = 0 AND team_max_size <= 20)
         ))
    ),
    CONSTRAINT ck_batch_rule_template_allow_random CHECK
        (allow_student_random IN (0,1)),
    CONSTRAINT ck_batch_rule_template_strategy CHECK
        (unselected_strategy IN ('NONE','ADMIN_ALLOCATION')),
    CONSTRAINT ck_batch_rule_template_enabled CHECK (enabled IN (0,1)),
    CONSTRAINT ck_batch_rule_template_default CHECK (is_default IN (0,1)),
    UNIQUE KEY uk_batch_rule_template_revision (rule_code, revision),
    UNIQUE KEY uk_batch_rule_template_default (default_marker),
    KEY idx_batch_rule_template_available (enabled, is_default, rule_code, revision)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='批次规则模板不可变修订';

INSERT INTO batch_rule_template
(rule_code, rule_name, revision,
 hold_duration_seconds, hold_renewal_limit,
 allow_team, team_min_size, team_max_size,
 allow_student_random, unselected_strategy, rule_version,
 enabled, is_default, created_by, change_reason)
VALUES
('SYSTEM_DEFAULT', '系统默认选寝规则', 1,
 300, 1, 1, 2, 5, 1, 'ADMIN_ALLOCATION', 'phase2-rule-template-v1',
 1, 1, NULL, 'Flyway V10创建系统默认规则模板');

INSERT INTO batch_rule_template
(rule_code, rule_name, revision,
 hold_duration_seconds, hold_renewal_limit,
 allow_team, team_min_size, team_max_size,
 allow_student_random, unselected_strategy, rule_version,
 enabled, is_default, created_by, change_reason)
SELECT CONCAT('MIGRATED_', LPAD(ranked.row_number_value, 4, '0')),
       CONCAT('历史批次规则 ', LPAD(ranked.row_number_value, 4, '0')),
       1,
       ranked.hold_duration_seconds,
       ranked.hold_renewal_limit,
       ranked.allow_team,
       ranked.team_min_size,
       ranked.team_max_size,
       ranked.allow_student_random,
       ranked.unselected_strategy,
       ranked.rule_version,
       0,
       0,
       NULL,
       'Flyway V10从历史批次规则快照迁移'
FROM (
    SELECT distinct_rules.*,
           ROW_NUMBER() OVER (
               ORDER BY hold_duration_seconds, hold_renewal_limit,
                        allow_team, team_min_size, team_max_size,
                        allow_student_random, unselected_strategy, rule_version
           ) AS row_number_value
    FROM (
        SELECT DISTINCT
               hold_duration_seconds,
               hold_renewal_limit,
               allow_team,
               team_min_size,
               team_max_size,
               allow_student_random,
               unselected_strategy,
               rule_version
        FROM selection_batch
        WHERE NOT (
            hold_duration_seconds = 300
            AND hold_renewal_limit = 1
            AND allow_team = 1
            AND team_min_size = 2
            AND team_max_size = 5
            AND allow_student_random = 1
            AND unselected_strategy = 'ADMIN_ALLOCATION'
            AND rule_version = 'phase2-rule-template-v1'
        )
    ) distinct_rules
) ranked;

ALTER TABLE selection_batch
    ADD COLUMN rule_template_id BIGINT NULL
        COMMENT '批次规则模板修订主键' AFTER matching_weight_scheme_id;

UPDATE selection_batch sb
JOIN batch_rule_template template
  ON template.hold_duration_seconds = sb.hold_duration_seconds
 AND template.hold_renewal_limit = sb.hold_renewal_limit
 AND template.allow_team = sb.allow_team
 AND template.team_min_size = sb.team_min_size
 AND template.team_max_size = sb.team_max_size
 AND template.allow_student_random = sb.allow_student_random
 AND template.unselected_strategy = sb.unselected_strategy
 AND template.rule_version = sb.rule_version
SET sb.rule_template_id = template.id
WHERE sb.rule_template_id IS NULL;

ALTER TABLE selection_batch
    ADD CONSTRAINT fk_selection_batch_rule_template
        FOREIGN KEY (rule_template_id)
        REFERENCES batch_rule_template(id)
        ON DELETE RESTRICT,
    ADD KEY idx_selection_batch_rule_template (rule_template_id);
