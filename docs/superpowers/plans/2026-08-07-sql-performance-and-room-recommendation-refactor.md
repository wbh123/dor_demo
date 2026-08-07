# SQL Performance and Room Recommendation Refactor Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在不改变现有 OpenAPI、推荐算法语义、住宿资格规则和最终床位事实来源的前提下，建立数据库性能开发硬约束，消除学生候选寝室查询的 N+1 数据库访问，并仅依据 MySQL 8.4 `EXPLAIN ANALYZE` 结果增加必要索引。

**Architecture:** 推荐服务保持应用层编排和匹配打分职责，数据读取迁移到按学生推荐域组织的 MyBatis Mapper。候选房间、入住统计、室友特征、可用床型与可选床位采用集合化批量查询，使数据库查询次数不随候选房间数量线性增长；索引变更通过新的 Flyway 迁移实施，并同步 Navicat 生成链、数据库字典与数据库测试。

**Tech Stack:** Java 21、Spring Boot 4、MyBatis-Plus 3.5.17、MyBatis XML、MySQL 8.4、Flyway、JUnit 5、Testcontainers、Python 契约测试。

## Global Constraints

- 保持现有对外 OpenAPI 路径、请求体、响应体、错误码和推荐算法版本语义不变。
- 不修改住宿调整、换寝、组队、房间管理等正在并行开发的业务文件。
- Controller 不新增数据库访问；业务 SQL 继续只允许存在于 `src/main/resources/mapper/**.xml`。
- 高频查询禁止在 Java 循环中逐房间、逐床位或逐学生访问数据库。
- 优先使用集合化 JOIN、批量聚合和一次性分组查询；相关子查询原则上消除，非相关派生表、CTE、`EXISTS` / `NOT EXISTS` 只有在执行计划证明更优时保留。
- 索引只基于真实查询、代表性数据和 MySQL 8.4 `EXPLAIN ANALYZE` 结果增加；不得按字段名称盲目堆叠索引。
- 新索引不得与已有主键、唯一索引或联合索引前缀重复；写热点表必须评估二级索引写放大。
- 已执行 Flyway 迁移不得修改；确需索引变更时新增 V34，并同步 Navicat 唯一生成链、数据库字典、完整性检查和相关测试。
- 每个任务遵循红灯测试 → 最小实现 → 绿灯验证 → 独立提交。

---

### Task 1: 固化 SQL 性能开发规范与门禁

**Files:**
- Modify: `AGENTS.md`
- Create: `docs/05_代码治理/2026-08-07_SQL查询与索引性能规范.md`
- Create: `scripts/ci/test_sql_performance_governance.py`
- Modify: `scripts/ci/run_contracts.sh`

**Interfaces:**
- Consumes: 当前 MyBatis、Java 内嵌 SQL、模块化治理门禁。
- Produces: 长期 SQL 性能硬约束和可自动检查的最低规则。

- [ ] **Step 1: 写失败契约**

契约至少检查：

```python
assert "禁止在 Java 循环中" in agents
assert "EXPLAIN ANALYZE" in agents
assert "相关子查询" in agents
assert "SELECT *" in agents
assert "Keyset" in agents
assert "SQL 查询与索引性能" in governance_doc
```

并检查推荐服务最终不得再出现 `NamedParameterJdbcTemplate`、`jdbc.query`、`jdbc.queryForList`、`for (Long roomId : policy.roomIdsForBatch(batchId))`。

- [ ] **Step 2: 运行契约确认红灯**

Run:

```bash
python scripts/ci/test_sql_performance_governance.py
```

Expected: 因规范文档和推荐服务批量化尚未完成而失败。

- [ ] **Step 3: 更新开发要求文档**

在 `AGENTS.md` 数据库规则中加入：N+1 禁止、集合化查询、相关子查询原则上消除、`SELECT *` 限制、索引设计、索引函数化条件限制、深分页 Keyset、结果等价测试、`EXPLAIN ANALYZE` 证据要求、Redis 仅作为可重建性能层。

- [ ] **Step 4: 新增完整 SQL 性能治理文档**

文档给出 JOIN、批量聚合、联合索引列序、低基数字段、函数导致索引失效、深分页、覆盖索引、写放大、执行计划评审模板和推荐服务 N+1 示例。

- [ ] **Step 5: 接入全仓契约入口**

在 `scripts/ci/run_contracts.sh` 中调用新契约。

- [ ] **Step 6: 提交文档和门禁**

Commit message:

```text
chore: 建立SQL查询与索引性能治理规则
```

---

### Task 2: 为候选寝室建立 MyBatis 批量查询模型

**Files:**
- Create: `backend-java/server/src/main/java/com/wust/dormitory/student/mapper/StudentRoomRecommendationMapper.java`
- Create: `backend-java/server/src/main/java/com/wust/dormitory/student/model/persistence/RoomRecommendationCandidateRow.java`
- Create: `backend-java/server/src/main/java/com/wust/dormitory/student/model/persistence/RoommateFeatureRow.java`
- Create: `backend-java/server/src/main/java/com/wust/dormitory/student/model/persistence/AvailableBedTypeRow.java`
- Create: `backend-java/server/src/main/java/com/wust/dormitory/student/model/persistence/AvailableBedRow.java`
- Create: `backend-java/server/src/main/resources/mapper/student/StudentRoomRecommendationMapper.xml`
- Create: `scripts/ci/test_student_room_recommendation_mybatis.py`
- Modify: `scripts/ci/run_contracts.sh`

**Interfaces:**
- Produces mapper methods:

```java
boolean isBatchAccessible(long batchId, long studentId);
String findBatchFeature(long batchId, long studentId);
List<RoomRecommendationCandidateRow> findCandidateRooms(long batchId);
List<RoommateFeatureRow> findRoommateFeatures(long batchId, List<Long> roomIds);
List<AvailableBedTypeRow> findAvailableBedTypes(long batchId, List<Long> roomIds);
List<AvailableBedRow> findAvailableBeds(long batchId, long roomId);
```

- [ ] **Step 1: 写 MyBatis 契约红灯**

检查 Mapper/XML 存在、namespace 一致、无 SQL 注解、候选查询一次返回房间静态信息和入住/床位统计、室友和床型查询按 `room_id IN (...)` 批量执行。

- [ ] **Step 2: 运行契约确认红灯**

```bash
python scripts/ci/test_student_room_recommendation_mybatis.py
```

Expected: Mapper/XML 尚不存在。

- [ ] **Step 3: 建立类型化持久化记录**

候选行必须包含：房间编号、容量、性别、resident scope、运行状态、state version、楼层、楼栋、是否被当前批次锁定、activeResidentCount、unknownBedResidentCount、availableBedCount。

- [ ] **Step 4: 实现集合化 SQL**

候选房间查询先构造当前批次可选房间集合，再 JOIN 房间/楼栋并 LEFT JOIN 预聚合入住统计和可用床位统计。不得在候选结果的每一行再次执行相关子查询。

室友特征查询：

```sql
WHERE ra.room_id IN (...)
  AND ra.assignment_status = 'ACTIVE'
ORDER BY ra.room_id, ra.assigned_at, ra.id
```

床型统计查询：

```sql
GROUP BY bed.room_id, bed.bed_type
```

- [ ] **Step 5: 运行 Mapper 契约**

Expected: PASS。

- [ ] **Step 6: 提交 Mapper 基础设施**

Commit message:

```text
refactor: 增加候选寝室批量查询Mapper
```

---

### Task 3: 将推荐服务改为固定次数数据库读取

**Files:**
- Modify: `backend-java/server/src/main/java/com/wust/dormitory/student/StudentRoomRecommendationService.java`
- Test: `backend-java/server/src/test/java/com/wust/dormitory/student/StudentRoomRecommendationServiceTest.java`
- Test: `scripts/ci/test_sql_performance_governance.py`

**Interfaces:**
- Consumes: Task 2 的 `StudentRoomRecommendationMapper`。
- Produces: 与现有 `rooms`、`room`、`recommend`、`randomRecommendation` 相同的公开行为。

- [ ] **Step 1: 增加行为和调用次数测试**

Mock Mapper 返回 1 个和 100 个候选房间时，验证：

```text
findCandidateRooms       恰好1次
findRoommateFeatures     最多1次
findAvailableBedTypes    最多1次
preferenceService.completed 恰好1次
featureAccessService.has(P2_ROOM_RECOMMENDATION) 恰好1次
```

候选数量增加不得增加 Mapper 调用次数。

- [ ] **Step 2: 运行测试确认旧实现失败**

旧实现因没有 Mapper 且循环查询而失败。

- [ ] **Step 3: 改造 `rooms()`**

流程固定为：批次访问检查 → 读取 batch/student/feature → 一次获取候选行 → 在 Java 内存使用 `requireStudentEligibleForRoom` 过滤 → 一次获取全部室友特征 → 一次获取全部床型统计 → 匹配打分与排序。

不得在候选房间循环中调用任何 Mapper/JDBC/`policy.room()`/`activeResidentCount()`/`unknownBedResidentCount()`/`availableBedCount()`/`availableCapacity()`。

- [ ] **Step 4: 优化 `room()`**

单寝室详情不得通过 `rooms(batchId, user)` 计算完整候选列表后再过滤；增加内部按目标房间过滤的批量查询入口或从候选 Mapper 支持可选 roomId 条件，使单房间读取成本与全量房间数无关。

- [ ] **Step 5: 优化 `selectBed()`**

通过 Mapper 的 `findAvailableBeds` 执行一次集合化查询；保持 TRUE_RANDOM 随机选择和其他策略选首床位的现有行为。

- [ ] **Step 6: 运行 Java 和静态契约测试**

```bash
cd backend-java && mvn -pl server -am test
python scripts/ci/test_sql_performance_governance.py
python scripts/ci/test_student_room_recommendation_mybatis.py
```

Expected: PASS。

- [ ] **Step 7: 更新后端模块化基线**

若 `StudentRoomRecommendationService` 已低于 300 行，则从大型 Java 基线中移除；否则只能收缩其允许行数，禁止增长。

- [ ] **Step 8: 提交推荐服务重构**

Commit message:

```text
refactor: 消除候选寝室推荐N+1查询
```

---

### Task 4: MySQL 8.4 真实执行计划与索引决策

**Files:**
- Create or Modify: `backend-java/server/src/test/java/com/wust/dormitory/student/StudentRoomRecommendationMapperMySqlIntegrationTest.java`
- Create: `docs/05_代码治理/2026-08-07_候选寝室SQL执行计划记录.md`
- Conditionally Create: `backend-java/server/src/main/resources/db/migration/V34__optimize_selection_query_indexes.sql`
- Conditionally Modify: Navicat 生成版本契约、数据库字典、数据库测试。

**Interfaces:**
- Consumes: Task 2/3 最终 SQL。
- Produces: 有证据的索引决策；允许结论为“现有索引足够，不新增 V34”。

- [ ] **Step 1: 建立 MySQL 8.4 Testcontainers 集成测试**

加载 Flyway 和代表性数据，执行 Mapper 查询并验证结果语义。

- [ ] **Step 2: 对热点 SQL 执行 `EXPLAIN ANALYZE`**

至少记录：候选房间、室友特征、床型聚合、单房间可用床位。记录 actual rows、loops、table scan/index lookup、sort/temporary table 和总耗时。

- [ ] **Step 3: 审计已有索引**

重点验证 `room_assignment(room_id, assignment_status, ...)`、`room_assignment(bed_id, assignment_status)`、批次范围表 `(batch_id, target_id)`、`student_feature(batch_id, student_id)` 等是否已被已有唯一/联合索引覆盖。

- [ ] **Step 4: 仅在执行计划证明确有收益时新增 V34**

新增前后分别执行相同 `EXPLAIN ANALYZE`，必须看到扫描行数、loops 或执行耗时明显改善；若无改善则不新增该索引。

- [ ] **Step 5: 若新增 V34，同步数据库链路**

更新 Flyway latest contract、Navicat 唯一生成链、数据库字典、完整性检查和生成器测试；同时处理私有仓库尚未合并的 V33 版本锁分支，避免版本漂移。

- [ ] **Step 6: 提交执行计划和索引决策**

Commit message:

```text
perf: 基于执行计划优化选寝查询索引
```

---

### Task 5: 全量验证与公开仓库合并

**Files:**
- No new production files unless tests expose regressions.

- [ ] **Step 1: 执行全量契约**

```bash
bash scripts/ci/run_contracts.sh
```

- [ ] **Step 2: 执行后端全量验证**

```bash
cd backend-java && mvn verify
```

- [ ] **Step 3: 执行前端生产构建**

```bash
cd frontend && npm ci && npm run build
```

- [ ] **Step 4: 执行数据库生成检查（仅发生数据库变化时）**

```bash
python scripts/db/generate_navicat_sql.py
python scripts/db/generate_navicat_sql.py --check
```

- [ ] **Step 5: 检查改动范围与并行冲突**

确认未修改住宿调整、换寝、组队等并行热点文件；若主线前进，重新基于最新 `main` 做增量适配。

- [ ] **Step 6: 公开仓库拉取请求全绿后合并**

要求 Java 21、Node 22、Cross-layer contracts、Public content、Redis safety 全部成功。

- [ ] **Step 7: 增量同步私有 `main`**

保留私有学校名称、数据库文档和私有专有契约，不整文件覆盖私有差异；私有 Actions 无运行结果时不得宣称私有持续集成已通过。
