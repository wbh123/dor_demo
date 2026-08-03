-- V16: cross-batch residency truth, domestic/international separation and transfer-student support.
-- V1-V15 remain immutable. ROOM mode keeps bed_id NULL until the real bed is confirmed.

ALTER TABLE student
    ADD COLUMN student_category VARCHAR(24) NOT NULL DEFAULT 'DOMESTIC'
        COMMENT 'DOMESTIC=国内生；INTERNATIONAL=国际生' AFTER nationality_code,
    ADD COLUMN enrollment_source VARCHAR(32) NOT NULL DEFAULT 'INITIAL_IMPORT'
        COMMENT 'INITIAL_IMPORT/TRANSFER_MANUAL/ADMIN_MANUAL/BATCH_IMPORT' AFTER student_category,
    ADD CONSTRAINT ck_student_category
        CHECK (student_category IN ('DOMESTIC','INTERNATIONAL')),
    ADD CONSTRAINT ck_student_enrollment_source
        CHECK (enrollment_source IN ('INITIAL_IMPORT','TRANSFER_MANUAL','ADMIN_MANUAL','BATCH_IMPORT')),
    ADD KEY idx_student_category_gender (student_category, gender),
    ADD KEY idx_student_enrollment_source (enrollment_source, created_at);

UPDATE student
SET student_category = CASE WHEN nationality_code='CN' THEN 'DOMESTIC' ELSE 'INTERNATIONAL' END;

ALTER TABLE room
    ADD COLUMN resident_scope VARCHAR(32) NOT NULL DEFAULT 'MIXED'
        COMMENT 'DOMESTIC_ONLY/INTERNATIONAL_ONLY/MIXED' AFTER gender_restriction,
    ADD CONSTRAINT ck_room_resident_scope
        CHECK (resident_scope IN ('DOMESTIC_ONLY','INTERNATIONAL_ONLY','MIXED')),
    ADD KEY idx_room_scope_status_gender (resident_scope, operational_status, gender_restriction);

ALTER TABLE selection_batch
    ADD COLUMN separate_student_categories TINYINT NOT NULL DEFAULT 0
        COMMENT '是否强制国内生与国际生使用专用寝室' AFTER selection_mode,
    ADD CONSTRAINT ck_batch_separate_student_categories
        CHECK (separate_student_categories IN (0,1)),
    ADD KEY idx_batch_mode_category_status
        (selection_mode, separate_student_categories, batch_status);

ALTER TABLE batch_student_eligibility
    ADD COLUMN source_type VARCHAR(32) NOT NULL DEFAULT 'INITIAL'
        COMMENT 'INITIAL/IMPORT/ADMIN_MANUAL/TRANSFER_MANUAL' AFTER reason_code,
    ADD COLUMN added_by BIGINT NULL COMMENT '人工加入批次的管理员' AFTER source_type,
    ADD COLUMN added_at DATETIME(3) NULL COMMENT '人工加入时间' AFTER added_by,
    ADD CONSTRAINT fk_eligibility_added_by
        FOREIGN KEY (added_by) REFERENCES app_user(id) ON DELETE SET NULL,
    ADD CONSTRAINT ck_eligibility_source_type
        CHECK (source_type IN ('INITIAL','IMPORT','ADMIN_MANUAL','TRANSFER_MANUAL')),
    ADD KEY idx_eligibility_source (batch_id, source_type, eligibility_status);

-- Promote V15 room_assignment from a per-batch ROOM result into the cross-batch residency truth.
ALTER TABLE room_assignment
    DROP INDEX uk_room_assignment_batch_student,
    DROP CHECK ck_room_assignment_method,
    DROP CHECK ck_room_assignment_status,
    MODIFY COLUMN batch_id BIGINT NULL COMMENT '来源批次；管理员直接分配时可为空',
    ADD COLUMN bed_id BIGINT NULL COMMENT '实际床位；ROOM模式初始为空' AFTER room_id,
    ADD COLUMN source_selection_mode VARCHAR(16) NOT NULL DEFAULT 'ROOM'
        COMMENT 'ROOM/BED/DIRECT' AFTER team_id,
    ADD COLUMN bed_confirmed_at DATETIME(3) NULL COMMENT '实际床位确认时间' AFTER assigned_at,
    ADD COLUMN ended_at DATETIME(3) NULL COMMENT '退宿、换寝或纠错结束时间' AFTER bed_confirmed_at,
    ADD COLUMN end_reason VARCHAR(500) NULL COMMENT '结束在住原因' AFTER ended_at,
    ADD COLUMN active_student_marker BIGINT GENERATED ALWAYS AS
        (CASE WHEN assignment_status='ACTIVE' THEN student_id ELSE NULL END) STORED,
    ADD COLUMN active_bed_marker BIGINT GENERATED ALWAYS AS
        (CASE WHEN assignment_status='ACTIVE' AND bed_id IS NOT NULL THEN bed_id ELSE NULL END) STORED,
    ADD CONSTRAINT fk_room_assignment_bed
        FOREIGN KEY (bed_id) REFERENCES bed(id) ON DELETE RESTRICT,
    ADD CONSTRAINT ck_room_assignment_source_mode
        CHECK (source_selection_mode IN ('ROOM','BED','DIRECT')),
    ADD CONSTRAINT ck_room_assignment_method
        CHECK (assignment_method IN (
            'ROOM_SELECT','TEAM_ROOM_SELECT','BED_SELECT','TEAM_BED_SELECT',
            'DIRECT_ROOM','DIRECT_BED','IMPORT_MIGRATION','MANUAL_ADJUSTMENT'
        )),
    ADD CONSTRAINT ck_room_assignment_status
        CHECK (assignment_status IN ('ACTIVE','ENDED')),
    ADD CONSTRAINT ck_room_assignment_end_state
        CHECK ((assignment_status='ACTIVE' AND ended_at IS NULL)
            OR (assignment_status='ENDED' AND ended_at IS NOT NULL)),
    ADD CONSTRAINT ck_room_assignment_bed_room_consistency
        CHECK (bed_id IS NULL OR bed_confirmed_at IS NOT NULL),
    ADD UNIQUE KEY uk_active_residency_student (active_student_marker),
    ADD UNIQUE KEY uk_active_residency_bed (active_bed_marker),
    ADD KEY idx_residency_room_status (room_id, assignment_status, bed_id),
    ADD KEY idx_residency_batch_status (batch_id, assignment_status),
    ADD KEY idx_residency_unknown_bed (room_id, assignment_status, bed_confirmed_at);

UPDATE room_assignment
SET source_selection_mode='ROOM',
    assignment_method=CASE
        WHEN team_id IS NULL THEN 'ROOM_SELECT'
        ELSE 'TEAM_ROOM_SELECT'
    END,
    assignment_status='ACTIVE'
WHERE assignment_status='ACTIVE';

-- Preserve historical BED-mode assignments as active residencies when the student has no active residency yet.
INSERT INTO room_assignment
(batch_id, student_id, room_id, bed_id, team_id, source_selection_mode,
 assignment_method, assignment_status, assigned_by, assigned_at,
 bed_confirmed_at, created_at, updated_at)
SELECT ba.batch_id,
       ba.student_id,
       b.room_id,
       ba.bed_id,
       ba.team_id,
       'BED',
       CASE
           WHEN ba.assignment_method='TEAM_SELECT' THEN 'TEAM_BED_SELECT'
           WHEN ba.assignment_method='MANUAL_ADJUSTMENT' THEN 'MANUAL_ADJUSTMENT'
           ELSE 'BED_SELECT'
       END,
       'ACTIVE',
       ba.assigned_by,
       ba.assigned_at,
       ba.assigned_at,
       ba.created_at,
       ba.updated_at
FROM bed_assignment ba
JOIN bed b ON b.id=ba.bed_id
WHERE ba.assignment_status='ACTIVE'
  AND NOT EXISTS (
      SELECT 1 FROM room_assignment ra
      WHERE ra.student_id=ba.student_id AND ra.assignment_status='ACTIVE'
  );

CREATE TABLE room_assignment_history (
    id BIGINT NOT NULL AUTO_INCREMENT,
    room_assignment_id BIGINT NULL,
    student_id BIGINT NOT NULL,
    room_id BIGINT NOT NULL,
    bed_id BIGINT NULL,
    event_type VARCHAR(32) NOT NULL,
    operator_user_id BIGINT NULL,
    reason VARCHAR(500) NULL,
    previous_data JSON NULL,
    current_data JSON NULL,
    occurred_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    CONSTRAINT fk_residency_history_assignment
        FOREIGN KEY (room_assignment_id) REFERENCES room_assignment(id) ON DELETE SET NULL,
    CONSTRAINT fk_residency_history_student
        FOREIGN KEY (student_id) REFERENCES student(id) ON DELETE RESTRICT,
    CONSTRAINT fk_residency_history_room
        FOREIGN KEY (room_id) REFERENCES room(id) ON DELETE RESTRICT,
    CONSTRAINT fk_residency_history_bed
        FOREIGN KEY (bed_id) REFERENCES bed(id) ON DELETE RESTRICT,
    CONSTRAINT fk_residency_history_operator
        FOREIGN KEY (operator_user_id) REFERENCES app_user(id) ON DELETE SET NULL,
    CONSTRAINT ck_residency_history_event
        CHECK (event_type IN (
            'ROOM_ASSIGNED','BED_ASSIGNED','BED_CONFIRMED','BED_CHANGED',
            'ROOM_CHANGED','RESIDENCY_ENDED','MIGRATED'
        )),
    KEY idx_residency_history_student (student_id, occurred_at),
    KEY idx_residency_history_room (room_id, occurred_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='跨批次在住与实际床位变更历史';

INSERT INTO room_assignment_history
(room_assignment_id, student_id, room_id, bed_id, event_type,
 operator_user_id, reason, current_data, occurred_at)
SELECT ra.id,
       ra.student_id,
       ra.room_id,
       ra.bed_id,
       'MIGRATED',
       ra.assigned_by,
       'V16迁移：建立跨批次在住事实',
       JSON_OBJECT(
           'batchId', ra.batch_id,
           'selectionMode', ra.source_selection_mode,
           'assignmentMethod', ra.assignment_method,
           'status', ra.assignment_status
       ),
       ra.assigned_at
FROM room_assignment ra;
