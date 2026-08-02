# Platform Feature GUI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the raw feature-code form with a graphical entitlement console using per-feature switches, grouping, search, filters, immediate changes, and transactional batch editing.

**Architecture:** Add a backend projection that returns plan defaults, current effective state, override source, implementation status, scope, phase, and risk for every catalog feature. Add idempotent single-state and batch-state commands that accept the desired final state, close prior active overrides, create only the required replacement override, and audit all changes. The Vue page consumes this projection, renders grouped switch cards, and supports immediate and batch workflows.

**Tech Stack:** Java 21, Spring Boot, Spring JDBC, MySQL, Vue 3, TypeScript, scoped CSS, Python unittest static contracts.

## Global Constraints

- Modify `main` directly as requested.
- Preserve single-client deployment and the unique `SYSTEM_ADMIN` model.
- Do not expose this page in the school business frontend.
- Future features with `enabled_in_program=0` remain visible only when requested and cannot be switched.
- Single changes require a reason and apply immediately.
- Batch changes use one reason, one transaction, and all-or-nothing behavior.
- The backend accepts desired final state; the frontend does not infer GRANT/REVOKE persistence details.
- No GitHub Actions evidence is used.

---

### Task 1: Backend entitlement projection and final-state commands

**Files:**
- Modify: `backend-java/server/src/main/java/com/wust/dormitory/subscription/EntitlementAdminService.java`
- Modify: `backend-java/server/src/main/java/com/wust/dormitory/platform/PlatformManagementController.java`
- Modify: `backend-java/model/src/main/resources/platform/openapi-platform.yaml`
- Test: `scripts/subscription/test_platform_feature_gui.py`

**Interfaces:**
- `List<FeatureEntitlementView> featureEntitlements(boolean includeFuture)`
- `FeatureEntitlementView setFeatureState(String featureCode, boolean enabled, String reason, CurrentUser operator)`
- `List<FeatureEntitlementView> setFeatureStates(List<FeatureStateChange> changes, String reason, CurrentUser operator)`
- `GET /api/v1/platform/features/entitlements`
- `PUT /api/v1/platform/features/{featureCode}/state`
- `POST /api/v1/platform/features/batch-state`

- [ ] Write static tests for the three endpoints, desired-state fields, transaction annotations, implemented-feature rejection, closing active overrides, and audit operations.
- [ ] Add a projection query joining the current plan revision, feature catalog, and latest active override.
- [ ] Implement idempotent final-state resolution: restore plan default by ending the active override; otherwise end the active override and insert the required `GRANT` or `REVOKE`.
- [ ] Implement batch mutation in one transaction and return refreshed projections.
- [ ] Add controller request and response records and update OpenAPI.

### Task 2: Platform API client and graphical entitlement console

**Files:**
- Modify: `frontend/src/platform/api.ts`
- Modify: `frontend/src/views/platform/PlatformFeaturesView.vue`
- Test: `scripts/subscription/test_platform_feature_gui.py`

**Interfaces:**
- `platformApi.featureEntitlements(includeFuture)`
- `platformApi.setFeatureState(featureCode, enabled, reason)`
- `platformApi.setFeatureStates(changes, reason)`

- [ ] Add typed feature-entitlement and state-change models.
- [ ] Replace raw JSON and manual code entry with search, filters, phase/module groups, state badges, and switch controls.
- [ ] Add immediate mode with a lightweight reason dialog and optimistic loading protection.
- [ ] Add batch mode with local draft state, fixed summary bar, preview counts, cancel, and one transactional save.
- [ ] Add group-level enable, disable, and restore-default actions.
- [ ] Keep future features disabled and visually distinguished.

### Task 3: Source verification and documentation

**Files:**
- Modify: `docs/04_接口设计/功能权限接口映射.md`
- Modify: `records/DAILY/2026-08-02_单客户订阅与功能授权开发.md`
- Test: `scripts/subscription/test_platform_feature_gui.py`

- [ ] Verify the final source contains switch controls, batch mode, filters, and no raw JSON entitlement editor.
- [ ] Document the final-state API semantics and GUI behavior.
- [ ] Record that runtime Maven, Vue, MySQL, and browser verification remain local validation tasks if not actually executed.
