# 组队确认、国际化与床位拆分实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 完成组队邀请状态收口、简体中文与英语切换、多语言欢迎语，以及上床下桌拆分为上下铺床位。

**Architecture:** 组队规则集中在 `TeamService`，学生选寝入口只调用明确的准备接口；国际化由前端轻量全局模块和稳定翻译键驱动；欢迎语以 JSON 形式保存在现有系统设置；床位拆分在 `RoomLayoutService` 的单个数据库事务中完成。

**Tech Stack:** Vue 3、TypeScript、Spring Boot 4、OpenAPI 3、MySQL 8.4、Redis 7.4、GitHub Actions。

## Global Constraints

- 最多八人间。
- 小组最多五人，邀请发起人最多邀请四名队友。
- 待处理邀请不计入队伍选寝人数。
- 开始组队选寝时取消未确认邀请。
- 进入个人选寝前必须退队确认。
- 已分配床位不得退队或修改床位类型。
- 所有对外接口先修改 OpenAPI。

---

### Task 1: 写入失败回归测试

**Files:**
- Create: `scripts/phase2/test_team_i18n_layout_refinement.py`
- Modify: `.github/workflows/phase1-ci.yml`

- [x] 覆盖主页邀请弹窗、暂不确认按钮、组队页邀请保留、退队确认和语言下拉框。
- [x] 覆盖后端取消待处理邀请、只锁定已加入成员、退队和八人容量限制。
- [x] 覆盖编辑器使用 `BUNK` 单元类型和放大后尺寸。
- [x] 写入功能实现前的回归契约。

### Task 2: 扩展组队接口和状态机

**Files:**
- Modify: `backend-java/model/src/main/resources/student/openapi-student.yaml`
- Modify: `backend-java/model/src/main/resources/openapi-interface.yaml`
- Modify: `backend-java/server/src/main/java/com/wust/dormitory/student/StudentController.java`
- Modify: `backend-java/server/src/main/java/com/wust/dormitory/student/TeamService.java`

- [x] 新增退队接口和个人选寝准备接口。
- [x] 队伍列表返回已确认人数、待处理邀请数量和成员详情。
- [x] 锁定队伍时取消未确认邀请，并只锁定 `JOINED` 成员。
- [x] 个人选寝准备事务内退出普通成员或解散队长队伍。
- [x] 队长可移除已接受成员并生成系统通知。
- [x] 后端固定限制小组最多五人。

### Task 3: 完成前端组队交互

**Files:**
- Modify: `frontend/src/views/student/StudentHomeView.vue`
- Modify: `frontend/src/views/student/TeamView.vue`
- Modify: `frontend/src/views/student/RoomListView.vue`
- Create: `frontend/src/team-i18n-refinement.css`

- [x] 主页加载邀请并显示接受、拒绝、暂不确认弹窗。
- [x] 组队页保留待处理邀请列表。
- [x] 队长开始选寝前显示邀请失效确认。
- [x] 成员可主动退出，队长可移除队友。
- [x] 个人房间列表在加载前执行退队确认。
- [x] 小组使用五个近正方形成员卡片。

### Task 4: 建立全局国际化与学生资料

**Files:**
- Create: `frontend/src/i18n/index.ts`
- Modify: `frontend/src/main.ts`
- Modify: `frontend/src/layouts/AppShell.vue`
- Modify: `frontend/src/views/student/StudentHomeView.vue`
- Modify: `frontend/src/views/admin/AdminDataView.vue`
- Create: `backend-java/server/src/main/java/com/wust/dormitory/student/StudentProfileService.java`
- Create: `backend-java/server/src/main/java/com/wust/dormitory/admin/StudentAdminService.java`

- [x] 提供 `zh-CN` 和 `en-US` 字典、语言持久化、插值和错误代码翻译。
- [x] 应用外壳加入语言下拉框和矢量导航图标。
- [x] 未手动选择语言时按学生国籍选择，不支持的国家回落英语。
- [x] 中文界面显示英文副标题，外语界面显示中文副标题。
- [x] 学生资料增加国籍和手机号，手机号允许本人修改。
- [x] 外国学生姓名卡片显示国籍。
- [x] 删除侧栏账号头像。

### Task 5: 多语言新生欢迎语

**Files:**
- Modify: `backend-java/model/src/main/resources/admin/openapi-system-setting.yaml`
- Modify: `backend-java/model/src/main/resources/auth/openapi-auth.yaml`
- Modify: `backend-java/server/src/main/java/com/wust/dormitory/admin/SystemSettingService.java`
- Modify: `backend-java/server/src/main/java/com/wust/dormitory/admin/SystemSettingController.java`
- Modify: `backend-java/server/src/main/java/com/wust/dormitory/auth/StudentWelcomeService.java`
- Modify: `frontend/src/views/admin/AdminDashboardView.vue`
- Modify: `frontend/src/layouts/AppShell.vue`

- [x] 欢迎语设置请求改为语言映射。
- [x] 兼容旧纯文本设置并自动作为中文欢迎语读取。
- [x] 当前用户响应返回全部语言版本。
- [x] 管理员分别编辑中文和英语文本。

### Task 6: 上下铺拆分与编辑器视觉优化

**Files:**
- Modify: `backend-java/model/src/main/resources/admin/openapi-room-management.yaml`
- Modify: `backend-java/server/src/main/java/com/wust/dormitory/admin/AdminController.java`
- Modify: `backend-java/server/src/main/java/com/wust/dormitory/admin/RoomLayoutService.java`
- Modify: `frontend/src/components/admin/RoomLayoutEditor.vue`
- Modify: `frontend/src/phase2-room-layout.css`

- [x] 请求按床具单元提交 `LOFT_BED_DESK` 或 `BUNK`。
- [x] 空上床下桌切换为上下铺时创建床架和下铺床位。
- [x] 复制原床位批次范围并同步布局。
- [x] 容量达到八人时返回稳定冲突错误。
- [x] 编辑器床位矩形放大一点六倍，并使用不透明对话框背景。

### Task 7: 测试数据与完整验证

**Files:**
- Create: `backend-java/docs/sql/reset_and_seed_test_data.sql`
- Modify: `scripts/e2e/student_experience_smoke.py`
- Modify: `scripts/e2e/team_invite_smoke.py`
- Modify: `scripts/e2e/phase2_room_layout_smoke.py`
- Modify: `.github/workflows/phase1-ci.yml`

- [x] 创建保留管理员账号、清空业务表并重建测试数据的 SQL 脚本。
- [x] 测试数据包含国内外学生，全部使用十二位数字学号。
- [ ] 运行静态契约和新增回归测试。
- [ ] 运行 OpenAPI 生成与 Maven 完整构建。
- [ ] 运行 TypeScript 检查和前端生产构建。
- [ ] 在 MySQL 与 Redis 上验证邀请失效、退队、移除通知和床位拆分。
- [ ] 全部持续集成通过后合并。
