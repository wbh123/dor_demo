# 管理端选寝批次列表 MyBatis 重构实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将管理端选寝批次列表从 `AdminService` 内嵌 JDBC 与 `SELECT sb.*` 迁移为类型化 MyBatis 查询，同时保持全部既有响应字段、统计口径和排序行为。

**Architecture:** 新增独立 `BatchCatalogMapper`、显式 XML 列表和 `BatchCatalogRow`。主查询只读取 `selection_batch` 的明确字段，资格人数、床位分配人数、寝室归属人数、活动房间锁和待确认床位人数分别在子查询中按批次聚合后关联，避免逐批次相关子查询。`AdminService.batches()` 只负责调用 Mapper 与转换响应。

**Tech Stack:** Java 21、Spring Boot 4、MyBatis XML、MySQL 8.4、JUnit 5、Mockito、Testcontainers、Python 静态契约、GitHub Actions。

## Global Constraints

- 不改变 `/api/v1/admin/batches` 的路径、权限、响应外壳或列表结构。
- 不修改数据库结构，不新增 Flyway 迁移。
- 返回字段必须覆盖当前 `selection_batch` 的所有正式字段及现有前端统计字段。
- 不得在 Mapper XML 中使用 `SELECT *`。
- `AdminService.batches()` 不得保留 JDBC、SQL 或动态查询拼接。
- 排序必须为 `created_at DESC, id DESC`，确保相同创建时间下结果稳定。
- `eligible_count` 仅统计 `eligibility_status='ELIGIBLE'`。
- `assigned_count` 与 `bed_assigned_count` 保持旧实现口径：统计该批次全部 `bed_assignment` 记录。
- `room_assigned_count` 统计该批次全部 `room_assignment` 记录。
- `locked_room_count` 统计 `active_batch_room_lock` 中该批次的房间数。
- `unconfirmed_bed_resident_count` 仅统计该批次中 `assignment_status='ACTIVE' AND bed_id IS NULL` 的在住记录。
- 公开仓库五项门禁与 MySQL 8.4 集成测试通过后，才允许迁移并合并私有 `main`。
- 私有迁移必须基于最新 `main`，保留另一名开发人员的并行提交。

---

### Task 1: 锁定字段与旧行为

**Files:**
- Create: `scripts/ci/test_admin_batch_catalog_mybatis.py`
- Create: `backend-java/server/src/test/java/com/wust/dormitory/admin/BatchCatalogQueryServiceTest.java`
- Modify: `scripts/ci/run_contracts.sh`

**Interfaces:**
- Consumes: 现有 `AdminService.batches(): List<Map<String,Object>>`。
- Produces: 对 Mapper 委托、字段清单、排序、统计口径和禁止 `SELECT *` 的失败测试。

- [ ] **Step 1: 写失败静态契约**

检查以下条件：

```python
assert "batchCatalogMapper.findBatches" in admin_service
assert "SELECT sb.*" not in admin_service
assert "SELECT *" not in mapper_xml
assert "ORDER BY sb.created_at DESC, sb.id DESC" in mapper_xml
```

并显式检查 `id`、`batch_code`、`batch_name`、`batch_status`、`selection_mode`、`separate_student_categories`、`rule_template_id`、`questionnaire_version_id`、`matching_weight_scheme_id`、时间字段、规则快照字段、版本字段和五类统计字段。

- [ ] **Step 2: 运行契约并确认红灯**

Run:

```bash
python scripts/ci/test_admin_batch_catalog_mybatis.py
```

Expected: 因缺少 `BatchCatalogMapper`、XML 和类型化结果而失败。

- [ ] **Step 3: 写服务委托失败测试**

测试 Mapper 返回两条记录时，`AdminService.batches()` 保持 snake_case 字段、空时间字段和统计字段，不调用 JDBC。

- [ ] **Step 4: 提交红灯测试**

```bash
git add scripts/ci/test_admin_batch_catalog_mybatis.py \
  backend-java/server/src/test/java/com/wust/dormitory/admin/BatchCatalogQueryServiceTest.java \
  scripts/ci/run_contracts.sh
git commit -m "test: lock admin batch catalog query contract"
```

### Task 2: 实现类型化批次查询

**Files:**
- Create: `backend-java/server/src/main/java/com/wust/dormitory/admin/model/persistence/BatchCatalogRow.java`
- Create: `backend-java/server/src/main/java/com/wust/dormitory/admin/mapper/BatchCatalogMapper.java`
- Create: `backend-java/server/src/main/resources/mapper/admin/BatchCatalogMapper.xml`
- Modify: `backend-java/server/src/main/java/com/wust/dormitory/admin/AdminService.java`

**Interfaces:**
- Produces: `List<BatchCatalogRow> BatchCatalogMapper.findBatches()`。
- `BatchCatalogRow.asResponseMap()` 返回既有 snake_case 字段和统计字段。

- [ ] **Step 1: 创建类型化结果**

使用 `record BatchCatalogRow(...)` 覆盖最终批次表全部字段：

```text
id, batchCode, batchName, batchStatus, selectionMode,
separateStudentCategories, questionnaireVersionId,
matchingWeightSchemeId, ruleTemplateId, startAt, endAt,
holdDurationSeconds, holdRenewalLimit, allowTeam,
teamMinSize, teamMaxSize, allowStudentRandom,
unselectedStrategy, ruleVersion, createdBy, publishedAt,
finishedAt, version, createdAt, updatedAt,
eligibleCount, assignedCount, bedAssignedCount,
roomAssignedCount, lockedRoomCount, unconfirmedBedResidentCount
```

`asResponseMap()` 使用 `LinkedHashMap`，允许可空时间字段。

- [ ] **Step 2: 创建 Mapper 接口**

```java
@Mapper
public interface BatchCatalogMapper {
    List<BatchCatalogRow> findBatches();
}
```

- [ ] **Step 3: 创建显式 XML 查询**

主查询显式选择每一列。五类统计采用按 `batch_id` 聚合的派生表，并通过 `LEFT JOIN` 关联；所有计数使用 `COALESCE(...,0)`。

- [ ] **Step 4: 修改服务层**

构造器增加 `BatchCatalogMapper`，`batches()` 仅执行：

```java
return batchCatalogMapper.findBatches().stream()
        .map(BatchCatalogRow::asResponseMap)
        .toList();
```

- [ ] **Step 5: 运行静态契约与单元测试**

```bash
python scripts/ci/test_admin_batch_catalog_mybatis.py
mvn -f backend-java/pom.xml -pl server -am test \
  -Dtest=BatchCatalogQueryServiceTest -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: PASS。

### Task 3: MySQL 8.4 真实查询验证

**Files:**
- Create: `backend-java/server/src/test/java/com/wust/dormitory/mapper/BatchCatalogMapperMySqlIntegrationTest.java`

**Interfaces:**
- Consumes: `BatchCatalogMapper.findBatches()`。
- Produces: 对最终 SQL、映射、空值和统计口径的数据库级证据。

- [ ] **Step 1: 创建最小 MySQL 8.4 表结构**

测试结构包含 `selection_batch`、`batch_student_eligibility`、`bed_assignment`、`room_assignment` 和 `active_batch_room_lock`，并建立两个相同 `created_at` 的批次。

- [ ] **Step 2: 插入覆盖性数据**

数据必须覆盖：非合格资格记录、寝室归属、床位归属、活动房间锁、已确认和待确认床位在住记录，以及 `published_at`、`finished_at` 为空的草稿批次。

- [ ] **Step 3: 断言真实结果**

断言：

```java
assertEquals(List.of(2L, 1L), rows.stream().map(BatchCatalogRow::id).toList());
assertEquals(2L, row.eligibleCount());
assertEquals(row.assignedCount(), row.bedAssignedCount());
assertEquals(1L, row.roomAssignedCount());
assertEquals(1L, row.lockedRoomCount());
assertEquals(1L, row.unconfirmedBedResidentCount());
```

- [ ] **Step 4: 执行 Java 21 测试**

```bash
mvn -f backend-java/pom.xml -pl server -am test \
  -Dtest=BatchCatalogMapperMySqlIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: MySQL 8.4 容器实际启动，测试跳过数为 0。

### Task 4: 全量验证与私有主线迁移

**Files:**
- Modify only as required by latest-main synchronization.

**Interfaces:**
- Produces: 公开绿色提交和私有 `main` 压缩提交。

- [ ] **Step 1: 运行公开五项门禁**

```bash
bash scripts/ci/run_policy.sh
bash scripts/ci/run_contracts.sh
bash scripts/ci/run_redis.sh
bash scripts/ci/run_backend.sh
bash scripts/ci/run_frontend.sh
```

Expected: 五项全部通过；Java 日志确认新增单元测试和 MySQL 8.4 测试实际执行。

- [ ] **Step 2: 压缩合并公开验证拉取请求**

公开分支最终树不得包含临时迁移器或一次性工作流。

- [ ] **Step 3: 从私有最新 `main` 建立迁移分支**

迁移公开验证后的正式文件，逐文件核对 Blob 摘要；同步 `run_contracts.sh` 时保留并行新增门禁。

- [ ] **Step 4: 检查并行开发状态**

```bash
git rev-list --left-right --count main...HEAD
```

Expected: 分支落后主线 0；若主线前进，先合并最新主线并重新验证文件范围。

- [ ] **Step 5: 压缩合并私有拉取请求**

合并说明必须记录公开运行编号、测试总数、MySQL 8.4 测试未跳过和私有运行器状态。
