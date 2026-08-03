-- 每间宿舍必须具有固定的男寝或女寝属性。
-- 宿舍楼可以保留 ANY，用于未来同一楼栋内按房间分别配置性别；
-- 但房间本身不得为 ANY，学生与房间性别必须一致。

ALTER TABLE room
    DROP CHECK ck_room_gender,
    ADD CONSTRAINT ck_room_gender
        CHECK (gender_restriction IN ('M','F'));
