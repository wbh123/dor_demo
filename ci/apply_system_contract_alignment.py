from pathlib import Path

ROOT = Path('private-repo')
PATH = ROOT / 'scripts/ci/validate_system_contracts.py'
text = PATH.read_text(encoding='utf-8')

# Batch publication now delegates the full readiness sequence to BatchPublishPreflightService.
old = '''    lifecycle = read(
        "backend-java/server/src/main/java/com/wust/dormitory/admin/BatchLifecycleService.java"
    )
    controller = read('''
new = '''    lifecycle = read(
        "backend-java/server/src/main/java/com/wust/dormitory/admin/BatchLifecycleService.java"
    )
    preflight = read(
        "backend-java/server/src/main/java/com/wust/dormitory/admin/BatchPublishPreflightService.java"
    )
    controller = read('''
if new not in text:
    if old not in text:
        raise RuntimeError('validate_system_contracts: batch lifecycle read marker missing')
    text = text.replace(old, new, 1)

old = '''    require(
        "batchScopeService.requireReady(batchId)" in lifecycle,
        "batch publication does not validate selected students and rooms before preflight",
        errors,
    )'''
new = '''    require(
        "batchPublishPreflightService.requirePublishable(batchId)" in lifecycle
        and "batchScopeService.requireReady(batchId)" in preflight
        and preflight.index("batchScopeService.requireReady(batchId)") < preflight.index("roomLockService.preview(batchId)"),
        "batch publication does not validate selected students and rooms before room preflight",
        errors,
    )'''
if new not in text:
    if old not in text:
        raise RuntimeError('validate_system_contracts: stale batch scope assertion missing')
    text = text.replace(old, new, 1)

# Questionnaire bypass/reselection controls were intentionally moved out of matching rules
# into the dedicated preference-policy page. Keep backend policy + dedicated UI coverage.
old = '''    matching_view = read("frontend/src/views/admin/AdminMatchingView.vue")
    room_list = read("frontend/src/views/student/RoomListView.vue")'''
new = '''    matching_view = read("frontend/src/views/admin/AdminMatchingView.vue")
    preference_policy_view = read("frontend/src/views/admin/AdminPreferencePolicyView.vue")
    room_list = read("frontend/src/views/student/RoomListView.vue")'''
if new not in text:
    if old not in text:
        raise RuntimeError('validate_system_contracts: matching view read marker missing')
    text = text.replace(old, new, 1)

old = '''    require(
        "ALLOW_SELECTION_WITHOUT_QUESTIONNAIRE" in policy_service
        and "ALLOW_STUDENT_RESELECT" in policy_service
        and "allowWithoutQuestionnaire" in matching_view
        and "allowStudentReselect" in matching_view,
        "administrator questionnaire-bypass or reselect policy is incomplete",
        errors,
    )'''
new = '''    require(
        "ALLOW_SELECTION_WITHOUT_QUESTIONNAIRE" in policy_service
        and "ALLOW_STUDENT_RESELECT" in policy_service
        and "allowWithoutQuestionnaire" in preference_policy_view
        and "allowStudentReselect" in preference_policy_view
        and "allowWithoutQuestionnaire" not in matching_view
        and "allowStudentReselect" not in matching_view,
        "administrator questionnaire-bypass or reselect policy is not isolated in preference policy",
        errors,
    )'''
if new not in text:
    if old not in text:
        raise RuntimeError('validate_system_contracts: stale preference-policy assertion missing')
    text = text.replace(old, new, 1)

PATH.write_text(text, encoding='utf-8')

# AdminDataView now delegates the lazy direct-assignment read cascade to a shared residency composable.
ux_path = ROOT / 'scripts/ci/test_admin_scope_questionnaire_team_ux.py'
ux = ux_path.read_text(encoding='utf-8')
old = '''admin_data = read("frontend/src/views/admin/AdminDataView.vue")
require(admin_data, ("PhoneDialCodeSelect", "TransientNotice", "student-category-switch-top", "/api/v1/admin/students/${student.id}/direct-assignment", "'/api/v1/admin/residencies'", "master-data-card-body"), "administrator student page is missing required layout or placement behavior")
'''
new = '''admin_data = read("frontend/src/views/admin/AdminDataView.vue")
residency_selector = read("frontend/src/features/residency/useResidencyAdjustmentSelector.ts")
require(admin_data, ("PhoneDialCodeSelect", "TransientNotice", "student-category-switch-top", "useResidencyAdjustmentSelector", "'/api/v1/admin/residencies'", "master-data-card-body"), "administrator student page is missing required layout or placement behavior")
require(residency_selector, ("/api/v1/admin/students/${targetStudentId}/direct-assignment", "/direct-assignment/options/campuses", "/direct-assignment/options/buildings", "/direct-assignment/options/floors", "/direct-assignment/options/rooms"), "shared administrator residency selector is missing direct-assignment cascade behavior")
'''
if new not in ux:
    if old not in ux:
        raise RuntimeError('admin scope UX contract: legacy direct-assignment assertion missing')
    ux = ux.replace(old, new, 1)
ux_path.write_text(ux, encoding='utf-8')

print('system contracts aligned with refactored policy, preflight and residency-selector boundaries')
