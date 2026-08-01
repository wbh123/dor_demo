# 逐房间床位布局配置实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 管理员可以逐房间配置床位坐标和朝向，学生Three.js场景读取同一布局，旧房间自动使用默认布局。

**Architecture:** 使用Flyway V5新增独立`room_bed_layout`表；`RoomLayoutService`负责默认布局、完整性、上下铺一致性、乐观锁与审计；OpenAPI生成管理端接口；Vue俯视编辑器维护布局；学生房间快照携带布局字段。

**Tech Stack:** MySQL 8.4、Flyway、Spring Boot 4、OpenAPI Generator、Vue 3、TypeScript、Three.js、Python回归测试。

## Global Constraints

- 不修改Flyway V1至V4。
- Controller只实现生成的AdminApi。
- 上下铺上下层共享平面坐标和朝向。
- 房间版本仅在前端内存保存，不显示。
- 保存必须填写原因并写审计。
- 没有自定义布局的房间必须保留默认回退。
- 所有持续集成门禁通过后才允许合并。

---

### Task 1: 数据库布局事实

**Files:**
- Create: `backend-java/server/src/main/resources/db/migration/V5__add_room_bed_layout.sql`
- Modify: `backend-java/docs/sql/schema.sql`
- Modify: `backend-java/docs/database-dictionary.md`
- Test: `scripts/phase2/test_room_layout.py`

**Interfaces:**
- Produces: `room_bed_layout(bed_id, layout_x, layout_z, rotation_degrees, updated_by, version, created_at, updated_at)`。

- [ ] 写失败测试，要求V5、主键、外键、坐标检查和角度检查存在。
- [ ] 运行`python -m unittest scripts/phase2/test_room_layout.py -v`并确认因V5缺失失败。
- [ ] 新增V5迁移和数据库字典。
- [ ] 运行固化脚本`python scripts/db/build_frozen_baseline.py`。
- [ ] 运行数据库测试并确认通过。

### Task 2: OpenAPI与后端布局服务

**Files:**
- Modify: `backend-java/model/src/main/resources/admin/openapi-admin.yaml`
- Modify: `backend-java/server/src/main/java/com/wust/dormitory/admin/AdminController.java`
- Create: `backend-java/server/src/main/java/com/wust/dormitory/admin/RoomLayoutService.java`
- Modify: `backend-java/server/src/main/java/com/wust/dormitory/student/StudentService.java`
- Test: `backend-java/server/src/test/java/com/wust/dormitory/admin/RoomLayoutServiceTest.java`
- Test: `scripts/phase2/test_room_layout.py`

**Interfaces:**
- Produces: `RoomLayoutService.getLayout(long)`、`RoomLayoutService.updateLayout(long, LayoutCommand, CurrentUser)`。
- Produces OpenAPI operations: `getRoomBedLayout`、`updateRoomBedLayout`。

- [ ] 写失败测试覆盖默认布局、床位集合、越界、角度、上下铺一致性、版本冲突和审计边界。
- [ ] 增加OpenAPI请求模型与两个接口。
- [ ] 实现`RoomLayoutService`和Controller转换。
- [ ] 在`StudentService.room()`查询中加入布局字段。
- [ ] 运行Maven全模块验证。

### Task 3: 管理端可视化编辑器

**Files:**
- Create: `frontend/src/components/admin/RoomLayoutEditor.vue`
- Create: `frontend/src/phase2-room-layout.css`
- Modify: `frontend/src/views/admin/AdminDormitoryView.vue`
- Modify: `frontend/src/main.ts`
- Test: `scripts/phase2/test_room_layout.py`

**Interfaces:**
- Consumes: GET/PUT `/api/v1/admin/rooms/{roomId}/bed-layout`。
- Produces: `saved`和`close`事件。

- [ ] 写失败测试要求布局按钮、拖拽画布、上下铺合并、旋转、数值输入、恢复默认和原因输入。
- [ ] 实现俯视编辑器，拖拽按0.25吸附。
- [ ] 将编辑器接入宿舍资源页面。
- [ ] 增加桌面和移动端样式。
- [ ] 运行前端生产构建。

### Task 4: 学生Three.js读取数据库布局

**Files:**
- Modify: `frontend/src/components/student/RoomBedScene3D.vue`
- Test: `scripts/phase2/test_room_layout.py`

**Interfaces:**
- Consumes: `layout_x`、`layout_z`、`rotation_degrees`。
- Preserves: 缺少字段时按`position_index`使用默认纵向布局。

- [ ] 写失败测试要求优先读取布局字段和默认回退。
- [ ] 实现自定义坐标、角度和共享上下铺床架锚点。
- [ ] 运行Vue类型检查与生产构建。

### Task 5: 真实运行验收与文档

**Files:**
- Create: `scripts/e2e/phase2_room_layout_smoke.py`
- Modify: `.github/workflows/phase1-ci.yml`
- Modify: `docs/03_开发阶段/02_第二阶段/README.md`
- Create: `records/2026-08-01_第二阶段床位布局配置.md`

**Interfaces:**
- 验证管理员读取布局、保存自定义布局、版本冲突、学生房间快照和审计。

- [ ] 写HTTP验收脚本并接入持续集成。
- [ ] 将运行断言升级到Flyway V5和`room_bed_layout`表。
- [ ] 执行全部静态、Java、前端和真实运行门禁。
- [ ] 更新第二阶段实施记录。
- [ ] 通过Squash合并到`main`。
