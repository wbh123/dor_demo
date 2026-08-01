# 选寝体验与批次规则优化实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 优化管理端宿舍资源操作、学生端选寝主流程、三态吸烟问卷、室友偏好展示和移动端床位可视化，同时收紧学生批次唯一性约束。

**Architecture:** 保持 OpenAPI 驱动和现有 REST 接口路径不变，优先通过现有通用响应扩展返回字段。数据库使用 Flyway V4 增量迁移，更新开发测试数据与固化 SQL。房间可视化使用 Vue 与 CSS 伪三维，不引入大型三维引擎。

**Tech Stack:** Java 21、Spring Boot 4、Flyway、MySQL 8.4、Redis 7.4、OpenAPI Generator、Vue 3、TypeScript、Vite、Python unittest。

## Global Constraints

- Controller 只实现 OpenAPI 生成的 `*Api` 接口，不手写路由。
- 学生端不显示批次编号、英文状态、数据库名称、状态版本和服务健康提示。
- `PUBLISHED` 允许查看和修改问卷，不允许临时占用或确认床位；`OPEN` 才允许选寝。
- 同一学生不得同时属于两个 `PUBLISHED`、`OPEN` 或 `PAUSED` 批次。
- 吸烟偏好固定为 `ACCEPT`、`REJECT`、`ANY` 三态。
- 房间卡片只显示匿名室友偏好，不显示姓名、学号或完整答案。
- 五人间靠窗右侧两个床位固定显示为上下铺。
- 数据库变更必须同时更新 Flyway、开发测试数据、固化 SQL、数据库字典和测试。

---

### Task 1: 数据库问卷三态与批次唯一性

**Files:**
- Create: `backend-java/server/src/main/resources/db/migration/V4__refine_questionnaire_and_active_batch_rules.sql`
- Modify: `backend-java/server/src/test/resources/db/dev-migration/R__development_test_data.sql`
- Modify: `backend-java/docs/sql/schema.sql`
- Modify: `backend-java/docs/database-dictionary.md`
- Modify: `scripts/db/test_database_baseline.py`
- Modify: `scripts/db/test_frozen_schema.py`

**Interfaces:**
- Produces: 问卷题目 `SMOKING_ACCEPTANCE`，类型 `SINGLE_CHOICE`，选项 `ACCEPT/REJECT/ANY`。
- Produces: 发布或开放批次前可检测同一学生的活动批次冲突。

- [ ] 写数据库失败测试，断言存在 V4、三态选项、固化 SQL 同步。
- [ ] 运行数据库测试并确认失败。
- [ ] 编写 V4，将已有吸烟答案从布尔值映射为字符串三态，并更新题型与选项。
- [ ] 更新开发数据生成和固化 SQL。
- [ ] 运行数据库测试并确认通过。

### Task 2: 后端当前选寝上下文与批次状态规则

**Files:**
- Modify: `backend-java/server/src/main/java/com/wust/dormitory/admin/AdminService.java`
- Modify: `backend-java/server/src/main/java/com/wust/dormitory/student/StudentService.java`
- Modify: `backend-java/server/src/main/java/com/wust/dormitory/matching/MatchingService.java`
- Modify: `scripts/backend/test_phase1_source.py`

**Interfaces:**
- Produces: `StudentService.batches(CurrentUser)` 最多返回一个当前活动批次并附带问卷摘要、最终分配信息。
- Produces: 房间列表字段 `roommatePreferenceTags` 和 `roommateCount`。
- Produces: `AdminService.changeBatchStatus` 在进入 `PUBLISHED` 或 `OPEN` 前检查学生活动批次冲突。

- [ ] 写后端失败测试，约束三态冲突规则、活动批次冲突校验和匿名偏好字段。
- [ ] 运行后端测试并确认失败。
- [ ] 实现批次冲突 SQL 校验和中文错误信息。
- [ ] 实现问卷摘要、最终分配聚合和室友偏好标签。
- [ ] 修改匹配算法，仅 `ACCEPT` 与 `REJECT` 产生吸烟冲突。
- [ ] 运行后端测试并确认通过。

### Task 3: 管理端宿舍和批次体验

**Files:**
- Modify: `frontend/src/views/admin/AdminDormitoryView.vue`
- Modify: `frontend/src/views/admin/AdminBatchView.vue`
- Modify: `frontend/src/style.css`
- Modify: `scripts/frontend/test_frontend_baseline.py`

**Interfaces:**
- Produces: `openRoomEditor(room)` 打开遮罩层；`closeRoomEditor()` 关闭。
- Consumes: 现有 `/api/v1/admin/rooms` 与批次状态接口。

- [ ] 写前端失败测试，要求遮罩层、即时筛选、中文状态操作。
- [ ] 运行前端测试并确认失败。
- [ ] 将房间编辑表单改成模态窗口。
- [ ] 使用 `watch([buildingId, gender], loadRooms)` 即时刷新。
- [ ] 批次状态和操作按钮改为中文业务名称并展示状态说明。
- [ ] 运行前端测试并确认通过。

### Task 4: 学生首页和问卷三态

**Files:**
- Modify: `frontend/src/views/student/StudentHomeView.vue`
- Modify: `frontend/src/views/student/QuestionnaireView.vue`
- Modify: `frontend/src/layouts/AppShell.vue`
- Modify: `scripts/frontend/test_frontend_baseline.py`

**Interfaces:**
- Consumes: 当前学生批次响应中的 `questionnaireSummary`、`assignment`、`questionnaire_started`。
- Produces: 学生首页只呈现一个当前流程、问卷摘要和住宿结果。

- [ ] 写前端失败测试，禁止技术词和批次详情，要求问卷摘要、住宿结果和三态吸烟单选。
- [ ] 运行前端测试并确认失败。
- [ ] 重构学生首页信息结构。
- [ ] 问卷优先使用服务端 `options` 渲染单选，吸烟题显示接受、不接受、均可。
- [ ] 删除顶部“系统服务正常”和学生规则中的数据库技术信息。
- [ ] 运行前端测试并确认通过。

### Task 5: 房间卡片、室友偏好和伪三维床位布局

**Files:**
- Modify: `frontend/src/views/student/RoomListView.vue`
- Modify: `frontend/src/views/student/RoomDetailView.vue`
- Modify: `frontend/src/style.css`
- Modify: `scripts/frontend/test_frontend_baseline.py`

**Interfaces:**
- Consumes: `roommatePreferenceTags`、`roommateCount`、床位 `bed_type` 与 `position_index`。
- Produces: `bedPlacement(bed)` 返回 `loft-left-*`、`loft-center-*`、`bunk-window-upper` 或 `bunk-window-lower`。

- [ ] 写前端失败测试，要求匿名偏好、移动端紧凑卡片、窗边上下铺和隐藏状态版本。
- [ ] 运行前端测试并确认失败。
- [ ] 缩小房间卡片并显示匿名室友偏好。
- [ ] 创建带入口、窗户、家具和透视效果的 CSS 房间场景。
- [ ] 将右侧上下铺按床位类型和位置固定布局。
- [ ] 保留按钮语义、状态文字和移动端可操作性。
- [ ] 运行前端测试并确认通过。

### Task 6: 文档、构建和真实验收

**Files:**
- Modify: `docs/03_开发阶段/01_第一阶段/04_前端页面与交互设计.md`
- Modify: `docs/02_系统设计/01_业务架构与核心规则.md`
- Modify: `docs/03_开发阶段/01_第一阶段/06_第一阶段实施记录.md`

**Interfaces:**
- Produces: 最新状态机、学生页面信息边界、三态问卷和可视化床位说明。

- [ ] 更新业务和前端文档，避免技术规则面向学生展示。
- [ ] 运行 Python 静态测试。
- [ ] 运行 Maven `clean verify`。
- [ ] 运行前端 `npm run build`。
- [ ] 使用 MySQL、Redis 和开发数据执行 HTTP 主流程。
- [ ] 创建拉取请求并使用 Squash 合并。
