# Student Profile Welcome Filters Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 完整、紧凑地展示学生个人偏好和画像，精简选床页面，增加房间筛选，并实现管理员可配置、服务端持久化的首次登录欢迎浮窗。

**Architecture:** 使用 Flyway V7 增加欢迎确认字段与系统设置表；认证接口向前端提供欢迎状态并提供幂等确认接口；管理员接口维护欢迎文案。学生首页使用纯前端确定性画像生成，房间筛选继续使用已加载候选数据，避免改动匹配和选床事务。

**Tech Stack:** Java 21、Spring Boot、Spring Security、JdbcTemplate、MySQL 8.4、Flyway、OpenAPI Generator、Vue 3、TypeScript、Pinia、CSS、Python 静态与端到端测试。

## Global Constraints

- 面向学生的“问卷”相关文本统一改为“个人偏好”，但接口路径和数据库既有名称保持不变。
- 所有已保存偏好必须完整展示。
- 首次欢迎状态必须由服务端持久化，不能只依赖浏览器存储。
- 管理员修改欢迎语不能让已确认欢迎的老用户重复弹窗。
- 不修改现有匹配算法、床位占用事务和实时推送契约。
- 必须先写失败测试，再实现生产代码。

---

### Task 1: 建立回归测试门禁

**Files:**
- Modify: `scripts/ux/test_ux_refinement.py`
- Modify: `scripts/backend/test_phase1_source.py`
- Modify: `scripts/db/test_database_baseline.py`
- Modify: `scripts/api/test_openapi_contract.py`

**Interfaces:**
- Produces: 对 V7、欢迎接口、个人偏好大卡片、筛选控件、提示删除和学生顶栏精简的静态契约。

- [ ] 添加测试，断言首页不再使用 `slice(0, 8)`，存在 `preferenceProfileSummary`、`preferenceProfileTags`、`personal-preference-card` 和放大的 `student-primary-actions`。
- [ ] 添加测试，断言房间列表存在 `floorFilter`、`minimumAvailableBeds`，并在 `filteredRooms` 中同时判断楼层和剩余铺位。
- [ ] 添加测试，断言选床组件和详情页不再包含指定的六类提示，只保留四种状态图例。
- [ ] 添加测试，断言学生端顶栏条件隐藏，管理员端顶栏仍存在。
- [ ] 添加数据库、OpenAPI、后端源码测试，要求 V7、`welcome_acknowledged_at`、`system_setting`、欢迎确认接口和管理员配置接口存在。
- [ ] 推送测试提交并确认持续集成按预期失败，失败原因必须是功能尚未实现。

### Task 2: 实现欢迎状态数据与接口

**Files:**
- Create: `backend-java/server/src/main/resources/db/migration/V7__add_student_welcome_settings.sql`
- Modify: `backend-java/docs/sql/schema.sql`
- Modify: `backend-java/docs/database-dictionary.md`
- Modify: `backend-java/model/src/main/resources/auth/openapi-auth.yaml`
- Modify: `backend-java/model/src/main/resources/admin/openapi-admin.yaml`
- Modify: `backend-java/model/src/main/resources/openapi-interface.yaml`
- Modify: `backend-java/server/src/main/java/com/wust/dormitory/auth/AuthService.java`
- Modify: `backend-java/server/src/main/java/com/wust/dormitory/auth/AuthController.java`
- Create: `backend-java/server/src/main/java/com/wust/dormitory/admin/SystemSettingService.java`
- Modify: `backend-java/server/src/main/java/com/wust/dormitory/admin/AdminController.java`
- Modify: `backend-java/server/src/main/java/com/wust/dormitory/security/SecurityConfig.java`

**Interfaces:**
- Produces: `CurrentUserData.welcome`, `POST /api/v1/auth/welcome/acknowledge`, `GET/PUT /api/v1/admin/settings/student-welcome`。

- [ ] 新增 V7：给 `app_user` 增加 `welcome_acknowledged_at`，创建 `system_setting` 并插入默认 `STUDENT_WELCOME_MESSAGE`。
- [ ] 扩展认证 OpenAPI 模型 `WelcomeData`，并让登录和当前用户查询返回欢迎状态。
- [ ] 新增幂等欢迎确认接口；仅当前学生账号可写入确认时间。
- [ ] 登录查询在更新 `last_login_at` 前读取欢迎确认状态与当前欢迎文案；激活账号时清空欢迎确认时间。
- [ ] 新增管理员欢迎设置读取/更新契约，更新使用版本号、校验1至1000字符，并写审计记录。
- [ ] 重新生成固化数据库结构和字典内容。
- [ ] 执行 OpenAPI、数据库与后端测试，确认 Task 1 中相关测试转绿。

### Task 3: 实现首次登录欢迎浮窗

**Files:**
- Modify: `frontend/src/stores/auth.ts`
- Modify: `frontend/src/layouts/AppShell.vue`
- Modify: `frontend/src/style.css`
- Modify: `frontend/src/api/types.ts`（由 OpenAPI 生成流程更新）

**Interfaces:**
- Consumes: `CurrentUserData.welcome` 与欢迎确认接口。
- Produces: 学生首次登录欢迎遮罩层和确认交互。

- [ ] 在认证 Store 中增加欢迎计算状态和 `acknowledgeWelcome()`；接口成功后本地将 `required` 更新为 `false`。
- [ ] 在应用壳层仅对学生渲染欢迎遮罩，标题固定“新同学，欢迎你”，正文来自配置。
- [ ] 点击“开始使用”时调用确认接口，失败则保留浮窗并展示错误。
- [ ] 添加遮罩、浮窗、淡入上浮、光晕装饰动画和 `prefers-reduced-motion` 降级。
- [ ] 学生端隐藏独立大顶栏；管理员端保持管理控制台顶栏。
- [ ] 执行前端构建和静态测试。

### Task 4: 管理端欢迎语配置

**Files:**
- Modify: `frontend/src/views/admin/AdminDashboardView.vue`
- Modify: `frontend/src/style.css`

**Interfaces:**
- Consumes: `GET/PUT /api/v1/admin/settings/student-welcome`。
- Produces: 管理工作台“新生欢迎语”设置卡片。

- [ ] 管理工作台加载欢迎配置，避免单项失败阻断原有统计数据。
- [ ] 增加文本域、字符计数、版本号提交和保存状态。
- [ ] 保存成功后更新本地版本和修改时间，显示明确成功提示。
- [ ] 处理版本冲突和空文案错误。
- [ ] 执行前端构建与管理员页面静态测试。

### Task 5: 重构学生个人偏好首页

**Files:**
- Modify: `frontend/src/views/student/StudentHomeView.vue`
- Modify: `frontend/src/ux-refinement.css`

**Interfaces:**
- Consumes: 现有个人偏好问题和答案数据。
- Produces: `preferenceProfileSummary`、`preferenceProfileTags` 和完整偏好列表。

- [ ] 删除答案摘要的八项截断，保留全部已保存偏好。
- [ ] 根据时间、量表和选项答案确定性生成画像概述；无答案时显示引导文案。
- [ ] 生成最多六个画像标签，标签只归纳现有答案，不推断敏感信息。
- [ ] 将个人偏好改为单张大卡片：画像区、标签区、紧凑键值表。
- [ ] 将“选择宿舍和床位”“组队选寝”改为大尺寸主要操作按钮并调整首页网格。
- [ ] 将当前文件与关联学生页面中的面向用户“问卷”文本替换为“个人偏好”。
- [ ] 执行静态测试和前端构建。

### Task 6: 精简选床页面并增加房间筛选

**Files:**
- Modify: `frontend/src/views/student/RoomListView.vue`
- Modify: `frontend/src/views/student/RoomDetailView.vue`
- Modify: `frontend/src/components/student/RoomBedScene3D.vue`
- Modify: `frontend/src/ux-refinement.css`
- Modify: `frontend/src/room-selection-refinement.css`

**Interfaces:**
- Produces: 动态楼层筛选、最少剩余铺位筛选和精简的选床状态界面。

- [ ] 在房间列表加入 `floorFilter` 和 `minimumAvailableBeds`，楼层选项从候选数据排序去重生成。
- [ ] 筛选顺序为组队容量、楼层、最低剩余铺位、关键字，确保所有条件同时生效。
- [ ] 删除三维组件方向提示容器。
- [ ] 删除详情页“窗户正对入口”“床位变化会自动更新”“同一前排”和重复需求提示。
- [ ] 图例只渲染四种床位状态；保留团队选择计数、当前选择和倒计时。
- [ ] 调整筛选栏和移动端布局，保证控件可点击。
- [ ] 执行静态测试和前端构建。

### Task 7: 完整验收与发布

**Files:**
- Modify: `scripts/e2e/phase1_smoke.py`
- Modify: `.github/workflows/phase1-ci.yml`（仅在需要注册新增测试文件时修改）

**Interfaces:**
- Consumes: 全部新接口和页面契约。

- [ ] 扩展真实接口流程：管理员读取并更新欢迎语、激活新学生、首次登录获得欢迎、确认欢迎、再次登录不再要求展示。
- [ ] 运行静态契约与数据库测试。
- [ ] 运行 `mvn -f backend-java/pom.xml clean verify`。
- [ ] 运行前端 `npm run build`。
- [ ] 运行 MySQL、Redis、Flyway、Spring Boot 和完整接口冒烟测试。
- [ ] 检查差异只包含本设计范围，更新拉取请求说明，压缩合并到 `main`。
