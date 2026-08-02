# 匹配权重管理、冲突解释与推荐理由实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现可审计、不可变修订、按批次复现的匹配权重管理，并向学生提供安全的推荐理由与冲突提示。

**Architecture:** 使用Flyway V6扩展现有匹配方案表；OpenAPI新增管理接口；`MatchingSchemeService`管理方案修订，`MatchingService`按批次读取修订并计算，`StudentRoomRecommendationService`负责候选房间推荐；Vue新增管理页面。

**Tech Stack:** Spring Boot 4、Java 21、MySQL 8.4、OpenAPI Generator、Vue 3、TypeScript、Python回归测试。

## Global Constraints

- 对外接口必须先修改OpenAPI，再由Controller实现生成接口。
- 已执行Flyway迁移不得修改，只能新增V6。
- 历史批次必须继续引用原匹配方案修订。
- 学生响应不得包含室友完整问卷或可识别信息。
- 每个任务先写失败测试，再实现最小代码。

---

### Task 1: 数据库不可变修订

**Files:**
- Create: `backend-java/server/src/main/resources/db/migration/V6__version_matching_weight_schemes.sql`
- Modify: `backend-java/docs/sql/schema.sql`
- Test: `scripts/phase2/test_matching_operations.py`

- [ ] 写失败测试，要求V6新增`revision`、`created_by`、`change_reason`、`published_at`并将唯一约束改为`scheme_code, revision`。
- [ ] 运行测试并确认因V6不存在而失败。
- [ ] 编写V6迁移，旧数据保留为第1修订。
- [ ] 重新生成固化`schema.sql`。
- [ ] 运行数据库测试并提交。

### Task 2: OpenAPI管理契约

**Files:**
- Create: `backend-java/model/src/main/resources/admin/openapi-matching.yaml`
- Modify: `backend-java/model/src/main/resources/openapi-interface.yaml`
- Test: `scripts/phase2/test_matching_operations.py`

- [ ] 写失败测试，要求列表、创建和新修订三个接口及请求模型。
- [ ] 运行测试确认失败。
- [ ] 添加OpenAPI分片和主契约引用。
- [ ] 运行契约解析和生成测试。
- [ ] 提交。

### Task 3: 匹配方案服务

**Files:**
- Create: `backend-java/server/src/main/java/com/wust/dormitory/matching/MatchingSchemeService.java`
- Modify: `backend-java/server/src/main/java/com/wust/dormitory/admin/AdminController.java`
- Test: `scripts/phase2/test_matching_operations.py`

- [ ] 写失败测试，要求权重键校验、范围校验、不可变修订、唯一激活、乐观锁和审计。
- [ ] 运行测试确认失败。
- [ ] 实现`MatchingSchemeService`。
- [ ] 在`AdminController`实现生成接口。
- [ ] 运行Java构建和测试。
- [ ] 提交。

### Task 4: 按批次匹配计算

**Files:**
- Modify: `backend-java/server/src/main/java/com/wust/dormitory/matching/MatchingService.java`
- Create: `backend-java/server/src/main/java/com/wust/dormitory/student/StudentRoomRecommendationService.java`
- Modify: `backend-java/server/src/main/java/com/wust/dormitory/student/StudentController.java`
- Test: `scripts/phase2/test_matching_operations.py`

- [ ] 写失败测试，要求移除静态权重并按`matching_weight_scheme_id`读取配置。
- [ ] 运行测试确认失败。
- [ ] 实现配置解析、确定性评分和公开理由。
- [ ] 将候选房间查询拆入独立服务并由Controller调用。
- [ ] 验证响应无原始室友问卷。
- [ ] 提交。

### Task 5: 管理端匹配规则页面

**Files:**
- Create: `frontend/src/views/admin/AdminMatchingView.vue`
- Create: `frontend/src/matching-operations.css`
- Modify: `frontend/src/router/index.ts`
- Modify: `frontend/src/layouts/AppShell.vue`
- Modify: `frontend/src/main.ts`
- Test: `scripts/phase2/test_matching_operations.py`

- [ ] 写失败测试，要求导航、路由、中文权重表单、修改原因、新修订提示和移动端样式。
- [ ] 运行测试确认失败。
- [ ] 实现页面和样式。
- [ ] 运行TypeScript与Vite构建。
- [ ] 提交。

### Task 6: 真实接口验收与文档

**Files:**
- Create: `scripts/e2e/phase2_matching_operations_smoke.py`
- Modify: `.github/workflows/phase1-ci.yml`
- Modify: `docs/03_开发阶段/02_第二阶段/README.md`
- Create: `records/2026-08-02_第二阶段匹配运营.md`

- [ ] 写真实HTTP脚本，覆盖查询、创建修订、版本冲突、唯一激活、审计、旧批次稳定和学生推荐理由。
- [ ] 将静态与真实测试接入持续集成。
- [ ] 更新阶段进度和实施记录。
- [ ] 执行完整持续集成。
- [ ] 全绿后通过Squash合并到`main`。
