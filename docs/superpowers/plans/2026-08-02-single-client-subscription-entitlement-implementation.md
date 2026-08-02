# Single-Client Subscription and Entitlement Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a single system administrator, immutable plan/subscription revisions, feature entitlements, quotas, running-batch snapshots, and a separate platform console without introducing multi-tenancy.

**Architecture:** Keep the existing Spring Boot monolith and OpenAPI-first workflow. Add a platform domain with immutable revisions and centralized `FeatureAccessService`/`QuotaService`, then gate existing business endpoints through explicit feature codes. Business users receive only effective feature codes and friendly service-state messages; platform details stay under `/api/v1/platform/**` and `/platform/**`.

**Tech Stack:** Java 21, Spring Boot 4, Spring Security, Spring JDBC, MySQL 8.4, Redis 7.4, OpenAPI Generator, Vue 3, TypeScript, Vite, Python unittest smoke/static tests.

## Global Constraints

- Work from `dev`; do not modify `main` directly.
- Do not modify Flyway V1-V11; add V12 and later migrations only.
- Do not introduce `tenant`, `tenant_id`, tenant context, subdomains, or cross-tenant behavior.
- Only one `SYSTEM_ADMIN` may exist.
- Initial platform credential is `system_admin` / `Dormitory@2026`; store only BCrypt hash and force first-login password change.
- Long-term subscriptions use `end_at=NULL` and never expire automatically.
- Upgrade and downgrade take effect immediately; active feature/quota overrides remain attached to the stable subscription.
- First-stage features are module-level; second-stage implemented features are operation-level; third-stage entries remain `enabled_in_program=0`.
- GitHub Actions must not be used as evidence. Only local commands actually run may be reported as passing.
- Generated OpenAPI Java and TypeScript artifacts are regenerated locally during final validation.
- Platform UI is separate and business UI must not expose SaaS, plan, contract, subscription revision, or permission-code terminology.

---

### Task 1: Database schema, immutable catalogs, and seed data

**Files:**
- Create: `backend-java/server/src/main/resources/db/migration/V12__add_single_client_subscription_entitlements.sql`
- Create: `backend-java/server/src/main/resources/db/migration/V13__seed_feature_quota_catalog_and_system_admin.sql`
- Modify: `backend-java/docs/database-dictionary.md`
- Test: `scripts/subscription/test_subscription_schema.py`

**Interfaces:**
- Produces tables `feature_catalog`, `quota_catalog`, `subscription_plan`, `subscription_plan_revision`, `plan_revision_feature`, `plan_revision_quota`, `service_subscription`, `service_subscription_revision`, `subscription_feature_override`, `subscription_quota_override`, `service_quota_alert`, `batch_entitlement_snapshot`, and `platform_audit_log`.
- Produces `app_user.password_change_required` and `SYSTEM_ADMIN` user type.

- [ ] Write static tests asserting V12/V13 filenames, all tables, immutable revision keys, one-current-revision marker, long-term end-date check, unique system-admin marker, default plan, default long-term subscription, and feature/quota seed codes.
- [ ] Verify tests fail because V12/V13 do not exist.
- [ ] Implement V12 with tables, checks, foreign keys, generated-marker unique indexes, and `app_user` extension.
- [ ] Implement V13 with the single BCrypt system admin, complete P1/P2/P3 catalog, quota catalog, default plan/revision, default long-term subscription revision, and permissive initial quotas.
- [ ] Update the database dictionary to V13.
- [ ] Re-run the static tests and leave MySQL runtime migration for local Codex validation.

### Task 2: OpenAPI platform contract and business entitlement projection

**Files:**
- Create: `backend-java/model/src/main/resources/platform/openapi-platform-auth.yaml`
- Create: `backend-java/model/src/main/resources/platform/openapi-platform-plan.yaml`
- Create: `backend-java/model/src/main/resources/platform/openapi-platform-subscription.yaml`
- Create: `backend-java/model/src/main/resources/platform/openapi-platform-entitlement.yaml`
- Create: `backend-java/model/src/main/resources/platform/openapi-platform-audit.yaml`
- Modify: `backend-java/model/src/main/resources/openapi-interface.yaml`
- Modify: `backend-java/model/src/main/resources/auth/openapi-auth.yaml`
- Test: `scripts/subscription/test_subscription_openapi.py`

**Interfaces:**
- Produces platform operations for login/logout/password change, plan/revision CRUD, subscription current/history/preview/change/status, overrides, quotas, and audit listing.
- Extends authenticated-user response with `passwordChangeRequired`, `features`, `serviceOperationAllowed`, `serviceMessage`, and `quotaAlerts`.

- [ ] Write contract tests for path references, unique operation IDs, required schemas, and the absence of tenant fields.
- [ ] Verify tests fail before platform YAML files are present.
- [ ] Add platform OpenAPI fragments and reference them from the interface root.
- [ ] Extend auth schemas without exposing contract number, plan IDs, or platform audit to business users.
- [ ] Re-run contract tests; generated source compilation remains a local validation step.

### Task 3: Identity isolation and first-login password change

**Files:**
- Modify: `backend-java/server/src/main/java/com/wust/dormitory/security/CurrentUser.java`
- Modify: `backend-java/server/src/main/java/com/wust/dormitory/security/SecurityUsers.java`
- Modify: `backend-java/server/src/main/java/com/wust/dormitory/auth/AuthService.java`
- Create: `backend-java/server/src/main/java/com/wust/dormitory/platform/PlatformAuthController.java`
- Create: `backend-java/server/src/main/java/com/wust/dormitory/platform/PlatformAuthService.java`
- Modify: `backend-java/server/src/main/java/com/wust/dormitory/security/AuthTokenService.java`
- Test: `scripts/subscription/test_platform_identity.py`

**Interfaces:**
- `CurrentUser.isSystemAdmin()` and `passwordChangeRequired()`.
- `SecurityUsers.requireSystemAdmin()` and `requireBusinessUser()`.
- `PlatformAuthService.login`, `changePassword`, and token revocation.

- [ ] Write tests asserting system-admin helpers, platform/business bidirectional guards, password-change gate, and token revocation calls.
- [ ] Verify tests fail on the current two-role implementation.
- [ ] Extend current-user/token serialization and login queries with `password_change_required`.
- [ ] Implement separate platform login and password-change service/controller.
- [ ] Ensure regular `/api/v1/auth/login` rejects `SYSTEM_ADMIN` and platform login rejects `ADMIN`/`STUDENT`.
- [ ] Re-run static tests.

### Task 4: Centralized feature access, subscription revision, and platform audit services

**Files:**
- Create: `backend-java/server/src/main/java/com/wust/dormitory/subscription/FeatureCodes.java`
- Create: `backend-java/server/src/main/java/com/wust/dormitory/subscription/FeatureAccessService.java`
- Create: `backend-java/server/src/main/java/com/wust/dormitory/subscription/SubscriptionService.java`
- Create: `backend-java/server/src/main/java/com/wust/dormitory/subscription/PlatformAuditService.java`
- Create: `backend-java/server/src/main/java/com/wust/dormitory/subscription/AccessMode.java`
- Test: `scripts/subscription/test_feature_access_service.py`

**Interfaces:**
- `require(String featureCode, AccessMode mode, Long batchId)`.
- `has(String featureCode)` and `currentFeatures()`.
- `currentSubscription()`, `previewChange(long planRevisionId)`, and immutable revision transitions.

- [ ] Write static behavior tests covering package boundaries, stable error codes, formula `plan + grant - revoke`, implemented-feature filtering, fixed-term time expiry, long-term no-expiry, and immutable transition SQL.
- [ ] Verify tests fail.
- [ ] Implement current subscription lookup, effective feature calculation, access-mode handling, and audit recording.
- [ ] Implement upgrade, downgrade, renew, suspend, resume, terminate, emergency stop, and emergency resume as new subscription revisions.
- [ ] Re-run tests.

### Task 5: Platform plan, subscription, entitlement, quota, and audit controllers

**Files:**
- Create: `backend-java/server/src/main/java/com/wust/dormitory/platform/PlatformPlanController.java`
- Create: `backend-java/server/src/main/java/com/wust/dormitory/platform/PlatformSubscriptionController.java`
- Create: `backend-java/server/src/main/java/com/wust/dormitory/platform/PlatformEntitlementController.java`
- Create: `backend-java/server/src/main/java/com/wust/dormitory/platform/PlatformAuditController.java`
- Create: `backend-java/server/src/main/java/com/wust/dormitory/subscription/PlanService.java`
- Test: `scripts/subscription/test_platform_controllers.py`

**Interfaces:**
- Implements all generated platform APIs.
- Uses only `SecurityUsers.requireSystemAdmin()` and rejects password-change-required users before platform operations.

- [ ] Write tests for generated-interface names, controller methods, system-admin gate, reason/version validation, and audit calls.
- [ ] Verify tests fail.
- [ ] Implement plan creation and immutable revision creation.
- [ ] Implement subscription queries, previews, transitions, feature overrides, quota overrides, usage summaries, and audit queries.
- [ ] Re-run static tests.

### Task 6: Quota service and current resource counters

**Files:**
- Create: `backend-java/server/src/main/java/com/wust/dormitory/subscription/QuotaCodes.java`
- Create: `backend-java/server/src/main/java/com/wust/dormitory/subscription/QuotaService.java`
- Create: `backend-java/server/src/main/java/com/wust/dormitory/subscription/QuotaUsageRepository.java`
- Test: `scripts/subscription/test_quota_service.py`

**Interfaces:**
- `requireAvailable(String quotaCode, long increment)`.
- `usageSummary()` and deduplicated warning state.

- [ ] Write tests for counters covering administrators, students, campuses, buildings, rooms, beds, annual batches, active batches, import rows, and export rows.
- [ ] Verify tests fail.
- [ ] Implement effective quota lookup, usage query map, 80% warning persistence, 100% rejection, and recovery of alert state.
- [ ] Expose stable error details containing quota code, limit, used, and requested.
- [ ] Re-run tests.

### Task 7: Batch entitlement snapshots and running-batch continuation

**Files:**
- Create: `backend-java/server/src/main/java/com/wust/dormitory/subscription/EntitlementSnapshotService.java`
- Modify: the existing batch state-transition service/controller identified by `prepare/publish/open/pause/close/complete` operations.
- Modify: student selection/team/allocation services that continue an existing batch.
- Test: `scripts/subscription/test_batch_entitlement_snapshot.py`

**Interfaces:**
- `captureForBatch(long batchId)` in the same transaction as first running-state transition.
- `allowsBatchContinuation(long batchId, String featureCode)`.

- [ ] Write tests requiring one snapshot per batch, same-transaction capture, no cross-batch reuse, service-state continuation, and emergency-stop rejection.
- [ ] Verify tests fail.
- [ ] Implement snapshot capture and continuation checks.
- [ ] Integrate snapshot-aware checks into personal selection, team selection, confirmation, allocation completion, and result access while keeping new batch/configuration actions on current entitlement.
- [ ] Re-run tests.

### Task 8: Existing P1/P2 business endpoint entitlement and quota mapping

**Files:**
- Modify: existing admin and student controllers/services under `backend-java/server/src/main/java/com/wust/dormitory/**`.
- Create: `docs/04_接口设计/功能权限接口映射.md`
- Test: `scripts/subscription/test_business_feature_mapping.py`

**Interfaces:**
- Every existing operation maps to exactly one P1 module or P2 operation code.
- Resource creation paths call `QuotaService` before writes.

- [ ] Build a machine-readable/static mapping of every current OpenAPI operation ID to a feature code and access mode.
- [ ] Verify the coverage test fails on unmapped operations.
- [ ] Add `FeatureAccessService.require` calls to current controllers/services, using current entitlement for new work and snapshot continuation for running batches.
- [ ] Add quota calls to administrator/student creation, campus/building/room/bed creation, batch creation/activation, import, and export paths.
- [ ] Document mapping and re-run coverage tests until no current business operation is unmapped.

### Task 9: Business auth projection and frontend feature gates

**Files:**
- Modify: `frontend/src/api/types.ts`
- Modify: `frontend/src/router/index.ts`
- Modify: existing authentication/session store under `frontend/src`.
- Create: `frontend/src/composables/useFeatureAccess.ts`
- Create: `frontend/src/components/FeatureGate.vue`
- Modify: existing admin/student navigation components.
- Test: `scripts/subscription/test_business_frontend_feature_gates.py`

**Interfaces:**
- `hasFeature(code: string): boolean`.
- Route metadata `requiredFeature`.
- Friendly service-state and quota messages without platform terminology.

- [ ] Write static tests for feature-gate utility, route guard, menu filtering, and prohibited SaaS terminology in business views.
- [ ] Verify tests fail.
- [ ] Extend session state from auth projection.
- [ ] Add route, menu, and button gates for current P1/P2 functionality.
- [ ] Map backend subscription/quota errors to business-friendly messages.
- [ ] Re-run static tests.

### Task 10: Separate platform Vue console

**Files:**
- Create: `frontend/src/platform/api.ts`
- Create: `frontend/src/platform/session.ts`
- Create: `frontend/src/layouts/PlatformLayout.vue`
- Create: `frontend/src/views/platform/PlatformLoginView.vue`
- Create: `frontend/src/views/platform/PlatformDashboardView.vue`
- Create: `frontend/src/views/platform/PlatformPlansView.vue`
- Create: `frontend/src/views/platform/PlatformSubscriptionView.vue`
- Create: `frontend/src/views/platform/PlatformFeaturesView.vue`
- Create: `frontend/src/views/platform/PlatformQuotasView.vue`
- Create: `frontend/src/views/platform/PlatformAuditView.vue`
- Create: `frontend/src/views/platform/PlatformPasswordView.vue`
- Modify: `frontend/src/router/index.ts`
- Test: `scripts/subscription/test_platform_frontend.py`

**Interfaces:**
- Separate platform session and route guard.
- First-login redirect to `/platform/profile/password`.

- [ ] Write tests for platform routes, independent login, identity rejection, forced-password redirect, plan/subscription/override/quota/audit pages, and absence from business navigation.
- [ ] Verify tests fail.
- [ ] Implement the separate layout, login, dashboard, and management pages using generated API types.
- [ ] Ensure platform identity never enters business layouts and business users never enter platform routes.
- [ ] Re-run static tests.

### Task 11: Local system-admin password reset tool

**Files:**
- Create: `scripts/admin/reset_system_admin_password.py`
- Create: `scripts/admin/README.md`
- Test: `scripts/subscription/test_system_admin_reset_script.py`

**Interfaces:**
- CLI accepts `--password` or hidden interactive input.
- Uses environment database/Redis settings, BCrypt, unique system-admin update, password-change reset, token cleanup, and audit insert.

- [ ] Write tests for no plaintext logging, password policy, environment usage, unique-admin assertion, token cleanup, and audit write.
- [ ] Verify tests fail.
- [ ] Implement the script and usage documentation.
- [ ] Re-run tests.

### Task 12: Documentation, frozen artifacts, and local end-to-end verification handoff

**Files:**
- Modify: `AGENTS.md`
- Modify: `README.md`
- Modify: `docs/03_开发阶段/02_第二阶段/README.md`
- Modify: `backend-java/docs/database-dictionary.md`
- Create: `records/DAILY/2026-08-02_单客户订阅与功能授权开发.md`
- Create: `scripts/e2e/platform_subscription_entitlement_smoke.py`

**Interfaces:**
- Local commands regenerate `backend-java/docs/sql/schema.sql` and `frontend/src/api/schema.ts`.

- [ ] Add end-to-end smoke scenarios for platform login/change-password, identity isolation, plan revision, long-term subscription, upgrade/downgrade, retained overrides, quota warning/rejection, and batch continuation.
- [ ] Update project rules and phase status without claiming unrun commands passed.
- [ ] Record every source-level validation performed and all local-runtime validation still required.
- [ ] In the local Codex handoff, require frozen schema generation, OpenAPI Java/TypeScript generation, Python static tests, Maven `clean verify`, frontend production build, MySQL V1-V13 migration, Redis/Spring Boot startup, and all business/platform smoke scripts.
- [ ] Commit implementation in no more than two thematic commits and merge the feature PR into `dev` only after source review; clearly label runtime verification as pending.
