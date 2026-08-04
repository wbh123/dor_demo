# 管理员范围、问卷与组队体验改造 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复管理员学生录入、住宿调整、批次范围、问卷保存、组队邀请和严格导入模板的业务与交互缺陷。

**Architecture:** 使用复用前端组件统一手机号和三秒通知；通过专用管理员住宿调整接口替代前端遍历批次推断；批次范围接口直接输出活动锁冲突元数据；组队和问卷在服务层修复根因。严格表头保持不变，值级容错集中在映射器。

**Tech Stack:** Vue 3、TypeScript、Spring Boot、Java 21、OpenAPI Generator、MySQL、Apache POI、Python 静态契约。

## Global Constraints

- 先在公开仓库开发并通过全部持续集成，再迁移私有仓库。
- 公开仓库不得写入私有数据库迁移、数据库结构和内部文档。
- 不改变现有选寝状态机和 Redis 最终事实边界。
- 成功提示三秒自动关闭并支持手动关闭。
- 导入保持固定严格表头，容错只处理单元格值。

---

### Task 1: 建立失败契约

**Files:**
- Create: `scripts/ci/test_admin_scope_questionnaire_team_ux.py`
- Modify: `scripts/ci/run_contracts.sh`

- [ ] 编写静态契约，检查手机号组件、三秒通知、范围冲突标签、住宿调整接口、问卷布尔转换、邀请姓名与取消接口、模板说明和容错映射。
- [ ] 将契约加入统一门禁。
- [ ] 推送并确认跨层契约因功能尚未实现而失败。

### Task 2: 手机号与卡片布局

**Files:**
- Create: `frontend/src/components/common/PhoneDialCodeSelect.vue`
- Create: `frontend/src/components/common/TransientNotice.vue`
- Modify: `frontend/src/views/admin/AdminDataView.vue`
- Modify: `frontend/src/utils/phoneCodes.ts`

- [ ] 实现收起仅显示地区码、展开显示国家或地区与地区码的选择器。
- [ ] 将国内生/国际生切换移到学生表单顶部。
- [ ] 调整两张卡片为等高弹性填充布局。
- [ ] 接入三秒通知并验证前端构建。

### Task 3: 修复学生住宿调整

**Files:**
- Modify: `backend-java/model/src/main/resources/admin/openapi-admin.yaml`
- Modify: `backend-java/server/src/main/java/com/wust/dormitory/admin/AdminController.java`
- Modify: `backend-java/server/src/main/java/com/wust/dormitory/residency/ResidencyService.java`
- Modify: `frontend/src/views/admin/AdminDataView.vue`
- Test: `backend-java/server/src/test/java/com/wust/dormitory/residency/ResidencyServiceTest.java`

- [ ] 新增管理员学生住宿调整上下文和提交接口契约。
- [ ] 编写失败测试覆盖有效在住、目标床位筛选和跨寝室调整。
- [ ] 在事务中结束旧在住并建立新在住，写入审计。
- [ ] 学生列表按钮直接调用新接口，不再遍历批次分配。

### Task 4: 批次范围与通知

**Files:**
- Modify: `backend-java/server/src/main/java/com/wust/dormitory/admin/BatchScopeService.java`
- Modify: `frontend/src/views/admin/AdminBatchView.vue`
- Test: `backend-java/server/src/test/java/com/wust/dormitory/admin/BatchScopeServiceTest.java`

- [ ] 先写失败测试，要求范围接口返回其他活动批次锁、模式和禁选原因。
- [ ] 扩展房间查询并在更新范围时再次校验冲突。
- [ ] 页面显示冲突批次与模式标签并禁止勾选。
- [ ] 固定筛选输入高度；保留专业、年级、楼栋、楼层筛选和当前结果全选。
- [ ] 将批次成功提示改为三秒通知。

### Task 5: 修复问卷保存

**Files:**
- Modify: `backend-java/server/src/main/java/com/wust/dormitory/student/StudentPreferenceService.java`
- Test: `backend-java/server/src/test/java/com/wust/dormitory/student/StudentPreferenceServiceTest.java`

- [ ] 编写失败测试，使用 `Boolean.TRUE` 作为 `required_flag`。
- [ ] 增加统一必填标记转换函数。
- [ ] 验证布尔、数字和字符串三类数据库返回值。

### Task 6: 组队身份校验与取消邀请

**Files:**
- Modify: `backend-java/model/src/main/resources/student/openapi-student.yaml`
- Modify: `backend-java/server/src/main/java/com/wust/dormitory/student/StudentController.java`
- Modify: `backend-java/server/src/main/java/com/wust/dormitory/student/TeamService.java`
- Modify: `frontend/src/views/student/TeamView.vue`
- Test: `backend-java/server/src/test/java/com/wust/dormitory/student/TeamServiceTest.java`

- [ ] InviteRequest 增加必填 `studentName`。
- [ ] 测试学号姓名不匹配时拒绝且不泄露学生存在性。
- [ ] 测试首次邀请自动建立队伍。
- [ ] 新增并测试取消待处理邀请接口。
- [ ] 页面增加姓名输入、取消按钮和三秒通知。

### Task 7: 严格模板说明与值级容错

**Files:**
- Modify: `backend-java/server/src/main/java/com/wust/dormitory/admin/AdminSpreadsheetController.java`
- Modify: `backend-java/server/src/main/java/com/wust/dormitory/admin/SpreadsheetSupport.java`
- Modify: `backend-java/server/src/main/java/com/wust/dormitory/admin/StudentImportRowMapper.java`
- Modify: `backend-java/server/src/main/java/com/wust/dormitory/importworkflow/StrictImportHeaders.java`
- Test: `backend-java/server/src/test/java/com/wust/dormitory/admin/SpreadsheetSupportTest.java`
- Test: `backend-java/server/src/test/java/com/wust/dormitory/admin/StudentImportRowMapperTest.java`

- [ ] 增加字段枚举工作表和逐字段说明。
- [ ] 测试国家或地区代码、中文名、英文名映射。
- [ ] 测试年级、性别、学生类别、培养层次和手机号的安全规范化。
- [ ] 保持严格表头；不能唯一推断的输入进入预检错误。

### Task 8: 公开验证与私有迁移

- [ ] 运行公开仓库跨层契约、Java 后端验证、前端生产构建和 Redis 安全门禁。
- [ ] 合并公开拉取请求。
- [ ] 基于私有最新 `main` 创建迁移分支，逐文件处理私有差异。
- [ ] 补充私有契约门禁，确保 V24/V25 数据库和 Navicat 生成器不被回退。
- [ ] 验证差异后合并私有 `main`。