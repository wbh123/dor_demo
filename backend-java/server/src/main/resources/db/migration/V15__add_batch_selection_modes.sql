-- V15: batch-level ROOM/BED selection modes.
-- ROOM mode records room membership only; students arrange concrete beds themselves.

ALTER TABLE selection_batch
    ADD COLUMN selection_mode VARCHAR(16) NOT NULL DEFAULT 'BED'
        COMMENT 'ROOM=选择寝室；BED=选择具体床位' AFTER batch_status,
    ADD CONSTRAINT ck_batch_selection_mode
        CHECK (selection_mode IN ('ROOM','BED'));

CREATE TABLE active_batch_room_lock (
    room_id BIGINT NOT NULL COMMENT '活动批次独占的房间',
    batch_id BIGINT NOT NULL COMMENT '占用该房间的批次',
    selection_mode VARCHAR(16) NOT NULL COMMENT '加锁时批次选择模式快照',
    locked_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (room_id),
    UNIQUE KEY uk_active_batch_room (batch_id, room_id),
    KEY idx_active_batch_room_batch (batch_id),
    CONSTRAINT fk_active_batch_room_room
        FOREIGN KEY (room_id) REFERENCES room(id) ON DELETE RESTRICT,
    CONSTRAINT fk_active_batch_room_batch
        FOREIGN KEY (batch_id) REFERENCES selection_batch(id) ON DELETE CASCADE,
    CONSTRAINT ck_active_batch_room_mode
        CHECK (selection_mode IN ('ROOM','BED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='活动批次房间互斥锁；批次结束后释放';

CREATE TABLE room_assignment (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '当前有效寝室归属主键',
    batch_id BIGINT NOT NULL COMMENT '选寝批次',
    student_id BIGINT NOT NULL COMMENT '学生',
    room_id BIGINT NOT NULL COMMENT '学生选择的寝室',
    team_id BIGINT NULL COMMENT '通过队伍选择时的队伍',
    assignment_method VARCHAR(32) NOT NULL COMMENT 'ROOM_SELECT/TEAM_ROOM_SELECT',
    assignment_status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    assigned_by BIGINT NULL COMMENT '执行选择的登录用户；个人选择为本人，队伍选择为队长',
    assigned_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    version INT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_room_assignment_batch_student (batch_id, student_id),
    KEY idx_room_assignment_batch_room (batch_id, room_id),
    KEY idx_room_assignment_team (team_id),
    CONSTRAINT fk_room_assignment_batch
        FOREIGN KEY (batch_id) REFERENCES selection_batch(id) ON DELETE RESTRICT,
    CONSTRAINT fk_room_assignment_student
        FOREIGN KEY (student_id) REFERENCES student(id) ON DELETE RESTRICT,
    CONSTRAINT fk_room_assignment_room
        FOREIGN KEY (room_id) REFERENCES room(id) ON DELETE RESTRICT,
    CONSTRAINT fk_room_assignment_team
        FOREIGN KEY (team_id) REFERENCES selection_team(id) ON DELETE RESTRICT,
    CONSTRAINT fk_room_assignment_operator
        FOREIGN KEY (assigned_by) REFERENCES app_user(id) ON DELETE RESTRICT,
    CONSTRAINT ck_room_assignment_method
        CHECK (assignment_method IN ('ROOM_SELECT','TEAM_ROOM_SELECT')),
    CONSTRAINT ck_room_assignment_status
        CHECK (assignment_status IN ('ACTIVE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='ROOM模式寝室归属；不分配具体床位';

INSERT INTO feature_catalog
(feature_code, feature_name, phase, scope, granularity, action_type,
 risk_level, enabled_in_program, sort_order)
VALUES
('P2_BED_SELECTION_MODE', '学生选择具体床位模式', 'PHASE2', 'SHARED',
 'OPERATION', 'EXECUTE', 'MEDIUM', 1, 305);

-- Preserve immutable plan/subscription history: create a new current plan revision
-- containing all existing entitlements plus the BED selection mode.
SET @current_subscription_revision_id := (
    SELECT id FROM service_subscription_revision WHERE is_current=1 LIMIT 1
);
SET @current_subscription_id := (
    SELECT subscription_id FROM service_subscription_revision
    WHERE id=@current_subscription_revision_id
);
SET @source_plan_revision_id := (
    SELECT plan_revision_id FROM service_subscription_revision
    WHERE id=@current_subscription_revision_id
);
SET @source_plan_id := (
    SELECT plan_id FROM subscription_plan_revision WHERE id=@source_plan_revision_id
);
SET @next_plan_revision := (
    SELECT COALESCE(MAX(revision),0)+1 FROM subscription_plan_revision
    WHERE plan_id=@source_plan_id
);
SET @system_admin_id := (
    SELECT id FROM app_user WHERE user_type='SYSTEM_ADMIN' LIMIT 1
);

INSERT INTO subscription_plan_revision
(plan_id, revision, revision_name, description, enabled,
 change_reason, created_by)
SELECT @source_plan_id,
       @next_plan_revision,
       CONCAT(revision_name, ' · 双模式'),
       CONCAT(COALESCE(description, ''), '；新增批次选择寝室/选择床位双模式。'),
       1,
       '系统升级：新增批次选择模式与床位模式细粒度授权',
       @system_admin_id
FROM subscription_plan_revision
WHERE id=@source_plan_revision_id;

SET @new_plan_revision_id := LAST_INSERT_ID();

INSERT INTO plan_revision_feature (plan_revision_id, feature_code)
SELECT @new_plan_revision_id, feature_code
FROM plan_revision_feature
WHERE plan_revision_id=@source_plan_revision_id;

INSERT INTO plan_revision_feature (plan_revision_id, feature_code)
VALUES (@new_plan_revision_id, 'P2_BED_SELECTION_MODE');

INSERT INTO plan_revision_quota (plan_revision_id, quota_code, quota_value)
SELECT @new_plan_revision_id, quota_code, quota_value
FROM plan_revision_quota
WHERE plan_revision_id=@source_plan_revision_id;

SET @next_subscription_revision := (
    SELECT revision + 1 FROM service_subscription_revision
    WHERE id=@current_subscription_revision_id
);

UPDATE service_subscription_revision
SET is_current=0
WHERE id=@current_subscription_revision_id;

INSERT INTO service_subscription_revision
(subscription_id, revision, plan_revision_id, subscription_type, service_status,
 contract_number, start_at, end_at, signed_at, emergency_stopped,
 change_reason, remark, is_current, created_by)
SELECT subscription_id,
       @next_subscription_revision,
       @new_plan_revision_id,
       subscription_type,
       service_status,
       contract_number,
       start_at,
       end_at,
       signed_at,
       emergency_stopped,
       '系统升级：保留原权限并开放选择床位模式',
       remark,
       1,
       @system_admin_id
FROM service_subscription_revision
WHERE id=@current_subscription_revision_id;

INSERT INTO platform_audit_log
(operation_type, operator_user_id, target_type, target_id, change_reason,
 before_json, after_json, success)
VALUES
('SUBSCRIPTION_UPGRADE', @system_admin_id, 'SERVICE_SUBSCRIPTION',
 CAST(@current_subscription_id AS CHAR),
 '系统升级：新增选择寝室/选择床位双模式',
 JSON_OBJECT('planRevisionId', @source_plan_revision_id),
 JSON_OBJECT('planRevisionId', @new_plan_revision_id,
             'addedFeature', 'P2_BED_SELECTION_MODE'),
 1);
