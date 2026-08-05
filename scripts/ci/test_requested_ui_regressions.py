#!/usr/bin/env python3
from __future__ import annotations

# Regression coverage for the consolidated interface and workflow repairs.
import sys
from pathlib import Path

root = Path(sys.argv[1] if len(sys.argv) > 1 else Path(__file__).resolve().parents[2])
errors: list[str] = []


def read(path: str) -> str:
    target = root / path
    content = target.read_text(encoding='utf-8')
    if target.suffix == '.vue':
        for suffix in ('.logic.ts', '.template.html', '.css'):
            companion = target.with_name(f'{target.stem}{suffix}')
            if companion.exists():
                content += '\n' + companion.read_text(encoding='utf-8')
    return content


def require(condition: bool, message: str) -> None:
    if not condition:
        errors.append(message)

layout = read('frontend/src/components/admin/RoomLayoutEditor.vue')
require('P2_ROOM_LAYOUT_UPDATE' in layout and 'layout-boundary-label door' in layout
        and 'layout-boundary-label window' in layout and 'layout-relative-reference' not in layout,
        'room layout editor must keep door/window boundary labels without redundant reference bubbles')
require('v-if="canEdit"' in layout and 'rotate(unit)' in layout and 'setType(unit' in layout
        and 'nudgeUnit' not in layout,
        'room layout cards must expose only rotate and bed-type controls to authorized administrators')

admin_data = read('frontend/src/views/admin/AdminDataView.vue')
phone_codes = read('frontend/src/utils/phoneCodes.ts')
require('student-contact-fields span-2' in admin_data and 'formatPhoneDisplay' in admin_data,
        'student nationality and phone fields are not grouped directly below category selection')
require("!['CN', 'HK', 'MO', 'TW'].includes(country.code)" in admin_data,
        'international student nationality list still contains China-related regions')
require('formatPhoneDisplay' in phone_codes and 'phoneDisplayParts' in phone_codes,
        'international phone display is not separated into dial code and local number')

shell = read('frontend/src/layouts/AppShell.vue')
require('v-if="!auth.isAdmin"' in shell and 'page-compliance' in shell
        and 'scrollbar-width:none' in shell,
        'administrator language switch, navigation scrollbar, or page-bottom compliance footer is incorrect')

welcome = read('frontend/src/views/admin/AdminDashboardView.vue')
editor = read('frontend/src/components/admin/WelcomeMessageEditor.vue')
require('activeWelcomeEditor' in welcome and 'welcome-token-toolbar' in welcome
        and editor.count('token-toolbar') == 0,
        'welcome token toolbar is not centralized around the active editor')
require("P2_MULTILINGUAL_INTERFACE" in welcome and 'multilingualWelcomeEnabled' in welcome,
        'multilingual welcome editing is not protected by a system feature')

batch = read('frontend/src/views/admin/AdminBatchView.vue')
require(
    'publishPreflightSnapshot' in batch
    and 'openPublishConfirmationAfterPreflight' in batch
    and 'WAITING_CONFIRMATION' in batch
    and '<AppConfirmDialog' in batch,
    'batch publishing does not enter a nested shared confirmation dialog after preflight',
)
require(
    'description="发布后学生可以按开放时间参与选择' in batch
    and 'confirm-text="确认发布"' in batch
    and 'publish-confirmation-facts' in batch,
    'publish confirmation dialog does not provide the required surface, padding and summary',
)
require('background:transparent' not in batch and 'publish-confirmation-overlay' not in batch,
        'legacy transparent publication overlay remains in the batch page')

home_view = read('frontend/src/views/student/StudentHomeView.vue')
home_content = read('frontend/src/views/student/StudentHomeContent.vue')
require('cross-batch-preference-note' not in home_view and ':href="`tel:' not in home_view,
        'student home retains the cross-batch preference notice or clickable telephone link')
require('profile-phone-line' in home_content and 'profile-primary-actions' in home_content,
        'student phone edit control or profile team/room actions are missing')

questionnaire = read('frontend/src/views/student/QuestionnaireContent.vue')
require('questionnaire-wide' in questionnaire and 'question-detail' in questionnaire
        and 'choice-row' in questionnaire,
        'questionnaire is not widened or refined with single-row detailed choices')

team_service = read('backend-java/server/src/main/java/com/wust/dormitory/student/TeamService.java')
verified_service = read('backend-java/server/src/main/java/com/wust/dormitory/student/VerifiedTeamInvitationService.java')
require('return createInternalTeam(batchId, user);' in team_service
        and 'teamService.createFormingTeam(user)' not in verified_service,
        'verified invitations do not create the current-batch team atomically')

notices = read('frontend/src/utils/installTransientSuccessNotices.ts')
notice_component = read('frontend/src/components/common/TransientNotice.vue')
require("'.alert.success, .alert.error, .alert.warning, .alert.info'" in notices
        and "'error'" in notice_component,
        'operation results are not consistently converted to dismissible three-second floating notices')

if errors:
    print('\n'.join(f'- {error}' for error in errors))
    raise SystemExit(1)
print('Requested UI regression contracts passed')
