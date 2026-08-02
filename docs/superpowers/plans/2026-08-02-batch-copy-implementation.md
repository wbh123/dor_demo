# 批次复制 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现管理员复制完整批次配置模板，并确保运行数据不被复制。

**Architecture:** 新增OpenAPI复制接口和独立`BatchCopyService`。服务在单个MySQL事务内锁定源批次、校验模板与资源状态、创建草稿副本、复制三类宿舍范围并写审计。管理端批次页通过遮罩表单收集新编码、名称、时间和原因。

**Tech Stack:** OpenAPI 3、Spring Boot 4、NamedParameterJdbcTemplate、MySQL 8.4、Vue 3、TypeScript、Python unittest、GitHub Actions。

## Global Constraints

- Controller只实现生成的`AdminApi`，不手写对外路由。
- 不修改已执行的Flyway迁移；本功能不新增数据库结构。
- 新批次始终为`DRAFT`。
- 不复制学生资格、活动锁、队伍、邀请、占用、分配和分配运行。
- 已取消批次禁止复制。
- 异常资源必须整体阻止复制，不允许静默跳过。
- 所有复制写入和审计必须处于同一事务。

---

### Task 1: OpenAPI契约与专项红灯测试

**Files:**
- Modify: `backend-java/model/src/main/resources/admin/openapi-admin.yaml`
- Modify: `backend-java/model/src/main/resources/openapi-interface.yaml`
- Create: `scripts/phase2/test_batch_copy.py`
- Modify: `.github/workflows/phase1-ci.yml`

**Interfaces:**
- Produces: `copyBatch(Long batchId, BatchCopyRequest request)`。
- Produces DTO: `BatchCopyRequest(batchCode, batchName, startAt, endAt, reason)`。

- [ ] 添加专项测试，断言接口路径、操作编号、请求字段、服务类、前端入口和运行验收存在。
- [ ] 将测试接入静态门禁并运行，确认旧代码失败。
- [ ] 添加OpenAPI接口和主契约引用。
- [ ] 运行OpenAPI契约测试，确认接口可生成。

### Task 2: 事务化批次复制服务

**Files:**
- Create: `backend-java/server/src/main/java/com/wust/dormitory/admin/BatchCopyService.java`
- Modify: `backend-java/server/src/main/java/com/wust/dormitory/admin/AdminController.java`

**Interfaces:**
- Consumes: `BatchCopyRequest`。
- Produces: `BatchCopyService.CopyCommand`和包含`id/sourceBatchId/buildingScopeCount/roomScopeCount/bedScopeCount`的结果。

- [ ] 编写源码测试，约束源批次行锁、取消状态拒绝、时间校验、资源异常查询、三类范围复制和审计动作。
- [ ] 实现`BatchCopyService.copy(long, CopyCommand, CurrentUser)`。
- [ ] 使用`GeneratedKeyHolder`创建新批次，显式复制所有配置字段并固定状态为`DRAFT`。
- [ ] 使用`INSERT ... SELECT`复制楼栋、房间和床位范围。
- [ ] 将控制器接入生成接口并完成日期转换。
- [ ] 运行Java全模块生成与编译。

### Task 3: 管理端复制遮罩表单

**Files:**
- Modify: `frontend/src/views/admin/AdminBatchView.vue`
- Create: `frontend/src/batch-copy.css`
- Modify: `frontend/src/main.ts`

**Interfaces:**
- Consumes: `POST /api/v1/admin/batches/{batchId}/copy`。
- Produces: 批次卡片“复制配置”按钮和移动端可用遮罩表单。

- [ ] 添加前端静态断言，约束非取消批次入口、必填新时间、复制原因、遮罩层和移动端样式。
- [ ] 实现复制表单状态、打开/关闭、提交、防重复点击和错误反馈。
- [ ] 成功后刷新批次列表并显示复制范围数量。
- [ ] 运行TypeScript、Vue和Vite生产构建。

### Task 4: 真实HTTP验收与文档收口

**Files:**
- Create: `scripts/e2e/phase2_batch_copy_smoke.py`
- Modify: `.github/workflows/phase1-ci.yml`
- Modify: `README.md`
- Modify: `AGENTS.md`
- Modify: `docs/03_开发阶段/02_第二阶段/README.md`

**Interfaces:**
- Produces: 可重复执行的管理员批次复制真实验收。

- [ ] 验收成功复制源批次配置和范围，新批次状态为草稿。
- [ ] 断言新批次没有学生资格、队伍、分配或活动锁。
- [ ] 临时将源范围床位设为维护状态，断言复制返回`BATCH_COPY_RESOURCE_UNAVAILABLE`且未创建副本。
- [ ] 断言已取消批次、重复编码和错误时间被拒绝。
- [ ] 断言审计日志包含`BATCH_COPY`。
- [ ] 更新第二阶段状态，将批次复制标记为已完成，下一项调整为规则模板与复杂组队异常处理。
- [ ] 执行静态测试、Java构建、前端构建和MySQL/Redis真实流程。
- [ ] 更新拉取请求并通过Squash合并到`main`。
