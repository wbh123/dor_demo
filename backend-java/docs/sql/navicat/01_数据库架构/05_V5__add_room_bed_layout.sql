CREATE TABLE room_bed_layout (
    bed_id BIGINT NOT NULL COMMENT '床位主键，同时作为布局主键',
    layout_x DECIMAL(6,3) NOT NULL COMMENT '房间局部X坐标',
    layout_z DECIMAL(6,3) NOT NULL COMMENT '房间局部Z坐标',
    rotation_degrees SMALLINT NOT NULL DEFAULT 90 COMMENT '平面旋转角度',
    updated_by BIGINT NOT NULL COMMENT '最后修改管理员',
    version INT NOT NULL DEFAULT 0 COMMENT '布局记录版本',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (bed_id),
    CONSTRAINT fk_room_bed_layout_bed
        FOREIGN KEY (bed_id) REFERENCES bed(id) ON DELETE CASCADE,
    CONSTRAINT fk_room_bed_layout_operator
        FOREIGN KEY (updated_by) REFERENCES app_user(id) ON DELETE RESTRICT,
    CONSTRAINT ck_room_bed_layout_x CHECK (layout_x BETWEEN -5.200 AND 5.200),
    CONSTRAINT ck_room_bed_layout_z CHECK (layout_z BETWEEN -3.500 AND 3.500),
    CONSTRAINT ck_room_bed_layout_rotation CHECK (rotation_degrees IN (0, 90, 180, 270)),
    KEY idx_room_bed_layout_operator (updated_by)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='逐床位房间可视化布局';
