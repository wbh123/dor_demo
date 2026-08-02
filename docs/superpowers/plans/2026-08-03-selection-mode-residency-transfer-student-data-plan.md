# Selection Mode, Residency, Transfer Student and Test Data Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver batch-level room/bed selection modes, international/domestic separation, cross-batch residency truth, manual transfer-student onboarding and assignment, graphical administration, and deterministic 1000-student database scripts.

**Architecture:** Keep the existing single-instance Spring Boot/Vue/MySQL/Redis architecture. Add a forward-only V16 migration that promotes `room_assignment` into the cross-batch residency truth, adds student category and room resident scope, and introduces batch category separation and eligibility source metadata. Extend existing OpenAPI contracts and generated controller interfaces rather than creating a second API stack. Selection-mode decisions and capacity checks live in services; Vue pages consume structured view models and never edit JSON.

**Tech Stack:** Java 21, Spring Boot 4, Spring JDBC, MySQL 8.4, Redis 7.4, OpenAPI 3.0, Vue 3, TypeScript, Vite, Python static tests and SQL generators.

## Global Constraints

- Work directly on `main` as explicitly requested.
- Do not modify V1-V15; add V16 and later only.
- `ROOM` mode assigns a room only and leaves `bed_id=NULL` until an actual bed is confirmed.
- A room with active residents whose bed is unknown cannot be opened in `BED` mode.
- Active batch room locks prevent a room from belonging to two active batches at the same time, regardless of mode.
- Batch completion releases activity locks but does not end residency.
- `P2_BED_SELECTION_MODE` controls whether administrators may create/publish `BED` batches.
- `resident_scope` restrictions always apply; optional batch separation further excludes `MIXED` rooms.
- Transfer students may be profile-only, directly assigned, or added to an existing batch after capacity/category/gender checks.
- Existing open batches may accept a transfer student only when the system can prove enough eligible capacity remains.
- Controllers implement generated OpenAPI interfaces; business logic belongs in services.
- Frontend forms and status displays must be graphical; no raw JSON editing or raw JSON dumps.
- SQL test datasets must be deterministic, idempotent for a clean schema, and contain enough rooms and beds for 1000 students.
- Do not claim Maven, Vite, MySQL, Redis or browser validation passed unless actually executed locally.

---

### Task 1: V16 residency and category data model

**Files:**
- Create: `backend-java/server/src/main/resources/db/migration/V16__add_residency_student_category_and_transfer_support.sql`
- Modify: `backend-java/docs/database-dictionary.md`
- Test: `scripts/selection_mode/test_residency_transfer_schema.py`

**Produces:** `student.student_category`, `student.enrollment_source`, `room.resident_scope`, `selection_batch.separate_student_categories`, cross-batch `room_assignment` fields and uniqueness constraints, `batch_student_eligibility.source_type`, and supporting indexes.

- [ ] Write static schema contract tests.
- [ ] Add V16 without modifying V15.
- [ ] Backfill student category from nationality and room assignments from historical bed assignments.
- [ ] Add active-residency and confirmed-bed generated marker uniqueness constraints.
- [ ] Update data dictionary.

### Task 2: OpenAPI contracts

**Files:**
- Modify: `backend-java/model/src/main/resources/admin/openapi-student-management.yaml`
- Modify: `backend-java/model/src/main/resources/admin/openapi-admin.yaml`
- Modify: `backend-java/model/src/main/resources/admin/openapi-room-management.yaml`
- Modify: `backend-java/model/src/main/resources/student/openapi-student.yaml`
- Modify: `backend-java/model/src/main/resources/openapi-interface.yaml`
- Test: `scripts/selection_mode/test_selection_transfer_openapi.py`

**Produces:** extended student, room and batch DTOs; transfer-student onboarding, direct room/bed assignment, batch enrollment/capacity preview, room selection, team room selection, residency lookup and bed confirmation endpoints.

- [ ] Add DTO enums and structured response schemas.
- [ ] Add unique operation IDs and root references.
- [ ] Ensure no JSON configuration fields are exposed for these operations.

### Task 3: Residency and category policy services

**Files:**
- Create: `backend-java/server/src/main/java/com/wust/dormitory/residency/ResidencyService.java`
- Create: `backend-java/server/src/main/java/com/wust/dormitory/residency/ResidencyPolicyService.java`
- Create: `backend-java/server/src/main/java/com/wust/dormitory/residency/BatchRoomLockService.java`
- Create: `backend-java/server/src/main/java/com/wust/dormitory/residency/BatchCapacityService.java`
- Test: `scripts/selection_mode/test_residency_services.py`

**Produces:** room occupancy summaries, gender/category eligibility, bed-mapping completeness, active room lock acquisition/release, and student/batch capacity preview.

- [ ] Implement active residency as the capacity truth.
- [ ] Implement `BED` publication blocking with `ROOM_BED_MAPPING_REQUIRED`.
- [ ] Implement category rules and mixed-team rules.
- [ ] Implement deterministic capacity preview with structured blocking reasons.

### Task 4: Batch creation/lifecycle and selection modes

**Files:**
- Modify: `backend-java/server/src/main/java/com/wust/dormitory/admin/BatchCreationService.java`
- Modify: `backend-java/server/src/main/java/com/wust/dormitory/admin/BatchLifecycleService.java`
- Modify: `backend-java/server/src/main/java/com/wust/dormitory/admin/BatchCopyService.java`
- Modify: `backend-java/server/src/main/java/com/wust/dormitory/admin/AdminService.java`
- Modify: `backend-java/server/src/main/java/com/wust/dormitory/admin/AdminController.java`
- Test: `scripts/selection_mode/test_batch_mode_integration.py`

**Produces:** batch selection mode/category-separation persistence, BED permission checks, room conflict preview, lock lifecycle and copied settings.

- [ ] Persist `selection_mode` and `separate_student_categories`.
- [ ] Recheck BED feature permission on create and publish.
- [ ] Acquire locks in the same publication transaction and release on terminal states.
- [ ] Return mode/category status in batch listings and preparation views.

### Task 5: Student room selection and bed confirmation

**Files:**
- Create: `backend-java/server/src/main/java/com/wust/dormitory/residency/RoomSelectionService.java`
- Modify: `backend-java/server/src/main/java/com/wust/dormitory/student/StudentController.java`
- Modify: `backend-java/server/src/main/java/com/wust/dormitory/student/StudentService.java`
- Test: `scripts/selection_mode/test_room_selection_contract.py`

**Produces:** personal/team room selection transactions, room-only assignment records, residency query and actual-bed confirmation.

- [ ] Personal room selection writes an active residency with no bed.
- [ ] Team room selection atomically writes all active member residencies.
- [ ] Capacity, gender, category, eligibility, active batch and room lock checks run under database locks.
- [ ] Bed confirmation updates only a resident of that room and prevents duplicate active bed occupancy.

### Task 6: Manual transfer-student workflows

**Files:**
- Create: `backend-java/server/src/main/java/com/wust/dormitory/admin/TransferStudentService.java`
- Modify: `backend-java/server/src/main/java/com/wust/dormitory/admin/StudentAdminService.java`
- Modify: `backend-java/server/src/main/java/com/wust/dormitory/admin/AdminController.java`
- Test: `scripts/selection_mode/test_transfer_student_workflow.py`

**Produces:** profile-only creation, direct assignment, capacity preview and batch enrollment commands.

- [ ] Add `enrollment_source=TRANSFER_MANUAL` and student category.
- [ ] Direct room-only assignment creates active residency; direct bed assignment also sets `bed_id`.
- [ ] Existing batch enrollment requires capacity proof for gender/category/scope and rejects active-batch conflicts.
- [ ] Draft/prepared batches can accept directly; published/open batches require remaining eligible capacity.
- [ ] All workflows create pending student accounts and business audit records.

### Task 7: Graphical admin and student frontend

**Files:**
- Modify: `frontend/src/views/platform/PlatformFeaturesView.vue`
- Modify: `frontend/src/views/admin/AdminBatchView.vue`
- Modify: existing student-management view under `frontend/src/views/admin/`
- Modify: existing room-management view under `frontend/src/views/admin/`
- Create: `frontend/src/views/admin/AdminResidencyView.vue`
- Modify: student selection view and router/API types.
- Test: `scripts/selection_mode/test_selection_mode_frontend.py`

**Produces:** compact permission cards, batch mode cards, category-separation switch, resident-scope segmented controls, transfer-student wizard, capacity preview, residency/bed mapping GUI, room-only student selection and actual-bed confirmation.

- [ ] Use cards, switches, segmented controls, tables, badges, progress bars and dialogs.
- [ ] Remove raw JSON entry/dumps from touched admin flows.
- [ ] BED mode card is disabled when the permission is absent.
- [ ] Transfer wizard supports profile-only, direct assignment and batch enrollment.

### Task 8: Frozen schema and two deterministic 1000-student datasets

**Files:**
- Modify: `backend-java/docs/sql/schema.sql`
- Create: `backend-java/docs/sql/test-data/1000_students_clean.sql`
- Create: `backend-java/docs/sql/test-data/1000_students_realistic_mixed_state.sql`
- Create: `scripts/db/generate_1000_student_sql.py`
- Create: `backend-java/docs/sql/test-data/README.md`
- Test: `scripts/db/test_1000_student_sql.py`

**Produces:** latest structure import SQL and two standalone deterministic data scripts.

- [ ] Regenerate schema from V1-V16 markers.
- [ ] Clean dataset: 1000 unassigned students, balanced majors/categories/genders, at least 1200 enabled beds.
- [ ] Realistic dataset: shuffled students, active/pending/disabled accounts, mixed preferences, room/bed residencies, unknown-bed residents, active ROOM/BED batches, transfer students, notifications, holds-compatible empty capacity, and no invariant violations.
- [ ] Include verification queries with expected counts.

### Task 9: Documentation and report

**Files:**
- Modify: `AGENTS.md`
- Modify: `backend-java/AGENTS.md`
- Modify: `README.md`
- Create: `records/DAILY/2026-08-03_双模式选寝国际生隔离转学生与千人数据开发.md`

- [ ] Record autonomous decisions, migrations, APIs, UI, scripts and known validation gaps.
- [ ] Clearly distinguish source completion from runtime verification.
