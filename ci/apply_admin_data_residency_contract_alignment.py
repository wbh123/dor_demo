from pathlib import Path

ROOT = Path('private-repo')
PATH = ROOT / 'scripts/ci/test_admin_scope_questionnaire_team_ux.py'
text = PATH.read_text(encoding='utf-8')

old = '''admin_data = read("frontend/src/views/admin/AdminDataView.vue")
require(admin_data, ("PhoneDialCodeSelect", "TransientNotice", "student-category-switch-top", "/api/v1/admin/students/${student.id}/direct-assignment", "'/api/v1/admin/residencies'", "master-data-card-body"), "administrator student page is missing required layout or placement behavior")
'''
new = '''admin_data = read("frontend/src/views/admin/AdminDataView.vue")
residency_selector = read("frontend/src/features/residency/useResidencyAdjustmentSelector.ts")
require(admin_data, ("PhoneDialCodeSelect", "TransientNotice", "student-category-switch-top", "useResidencyAdjustmentSelector", "'/api/v1/admin/residencies'", "master-data-card-body"), "administrator student page is missing required layout or placement behavior")
require(residency_selector, ("/api/v1/admin/students/${targetStudentId}/direct-assignment", "/direct-assignment/options/campuses", "/direct-assignment/options/buildings", "/direct-assignment/options/floors", "/direct-assignment/options/rooms"), "shared administrator residency selector is missing direct-assignment cascade behavior")
'''
if new not in text:
    if old not in text:
        raise RuntimeError('admin scope UX contract: legacy direct-assignment assertion missing')
    text = text.replace(old, new, 1)

PATH.write_text(text, encoding='utf-8')
print('admin student residency contract aligned with shared cascade selector')
