#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]

def read(path: str) -> str:
    return (ROOT / path).read_text(encoding='utf-8')

layout = read('frontend/src/components/admin/RoomLayoutEditor.vue')
layout_service = read('backend-java/server/src/main/java/com/wust/dormitory/admin/RoomLayoutService.java')
layout_planner = read('backend-java/server/src/main/java/com/wust/dormitory/admin/RoomLayoutPlanner.java')
layout_mapper = read('backend-java/server/src/main/resources/mapper/admin/RoomLayoutMapper.xml')
single_service = read('backend-java/server/src/main/java/com/wust/dormitory/admin/SingleBedRoomLayoutService.java')
dormitory = read('frontend/src/views/admin/AdminDormitoryView.vue')
room_service = read('backend-java/server/src/main/java/com/wust/dormitory/admin/RoomManagementService.java')
openapi = read('backend-java/model/src/main/resources/admin/openapi-admin.yaml')
shell = read('frontend/src/layouts/AppShell.vue')
login = read('frontend/src/views/LoginView.vue')
home = read('frontend/src/views/student/StudentHomeContent.vue')
questionnaire = read('frontend/src/views/student/QuestionnaireContent.vue')
en = read('frontend/src/i18n/locales/en-US.ts')

# Empty bunk units can collapse to either one-bed type, and capacity is reduced.
assert "BUNK_COLLAPSE_NOT_SUPPORTED" not in layout_service
assert "BUNK_TO_SINGLE_NOT_SUPPORTED" not in single_service
assert 'collapseBunkUnit' in layout_service
assert "operational_status = 'RETIRED'" in layout_mapper
assert 'SINGLE_BED' in (layout_service + layout_planner + single_service)
assert "unit.originalType==='BUNK'&&type!=='BUNK'" not in layout
assert 'collapsedBunks' in layout
assert 'width:220px' in layout
assert 'aspect-ratio:19/9' in layout

# Compact building overview and create actions.
assert 'building-compact-table' in dormitory
assert '添加宿舍楼' in dormitory
assert '添加宿舍' in dormitory
assert 'createBuilding' in dormitory
assert 'createRoom' in dormitory
assert 'post:' in openapi.split('/api/v1/admin/buildings:', 1)[1].split('/api/v1/admin/rooms:', 1)[0]
assert 'operationId: createBuilding' in openapi
assert 'operationId: createRoom' in openapi
assert 'educationLevelScope' in openapi
assert 'building_gender_restriction' in room_service
assert 'ROOM_BUILDING_SCOPE_CONFLICT' in room_service

# Menu order, naming, logo and optional compliance.
expected_menu = [
    "label:'工作台'", "label:'学生与专业'", "label:'宿舍资源'", "label:'数据导入'",
    "label:'匹配方案'", "label:'选寝规则'", "label:'选寝批次'", "label:'分配管理'",
    "label:'在住管理'", "label:'候补管理'", "label:'换寝与交换'", "label:'异常处理'",
    "label:'运营监控'", "label:'治理中心'",
]
positions = [shell.index(item) for item in expected_menu]
assert positions == sorted(positions)
assert 'showOperatorInfo' in shell and 'showIcpRecord' in shell
assert 'v-if="showOperatorInfo"' in shell and 'v-if="showIcpRecord"' in shell
assert 'width:60px' in shell
assert 'object-fit:contain' in shell

# Login/activation design and focus behavior.
assert 'hero-stats' not in login
assert 'VITE_LOGIN_HERO_TITLE' in login and 'VITE_LOGIN_HERO_DESCRIPTION' in login
assert 'loginFormHint' in login and 'activateFormHint' in login
assert 'AppModal' not in login
assert 'focusModePrimaryInput' in login
assert 'setSelectionRange' in login
assert 'VITE_SHOW_OPERATOR_INFO' in login and 'VITE_SHOW_ICP_RECORD' in login
assert 'auth-form-frame' in login

# Page subtitles removed from touched pages and phone dialog uses the shared modal.
assert '<AppModal' in home
assert 'phone-dialog-overlay' not in home
assert "t(String(option.option_text))" in home
assert '个人偏好概况' not in home or "local('个人偏好概况'" in home
assert 'useI18n' in questionnaire
assert '{{ questionTitle(question) }}' in questionnaire
assert '{{ questionDetail(question) }}' in questionnaire
assert 'PERSONAL PREFERENCES' not in questionnaire
assert 'Sleep time' in en and 'Light sleeper' in en

print('Dormitory, login and preference UI regression contract passed')
