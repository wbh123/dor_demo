#!/usr/bin/env python3
from pathlib import Path

root = Path(__file__).resolve().parents[2]

validator = root / 'scripts/ci/validate_system_contracts.py'
text = validator.read_text(encoding='utf-8')
old = '''    require(\n        "countryMessages" in dashboard_view\n        and "configuration.countryMessages().get(countryCode)" in welcome_service\n        and 'configuration.messages().get("en-US")' in welcome_service\n        and "data.setMessages(renderedMessages)" in welcome_service\n        and "data.setMessage(" not in welcome_service,\n        "language and country welcome messages do not follow the canonical messages object",\n        errors,\n    )'''
new = '''    require(\n        "countryMessages" in dashboard_view\n        and "configuration.countryMessages().get(countryCode)" in welcome_service\n        and "configuration.messages().forEach" in welcome_service\n        and "data.setMessages(renderedMessages)" in welcome_service\n        and "data.setMessage(" not in welcome_service,\n        "language and country welcome messages do not follow the canonical messages object",\n        errors,\n    )'''
if old not in text:
    raise RuntimeError('canonical welcome validator fragment not found')
validator.write_text(text.replace(old, new, 1), encoding='utf-8')

test = root / 'backend-java/server/src/test/java/com/wust/dormitory/auth/StudentWelcomeServiceTest.java'
text = test.read_text(encoding='utf-8')
text = text.replace(
    '''        assertThat(result.getMessage())\n                .isEqualTo("欢迎张同学，你是2026计算机科学与技术的硕士生。");\n        assertThat(result.getMessages())''',
    '''        assertThat(result.getMessages())\n                .containsEntry("zh-CN", "欢迎张同学，你是2026计算机科学与技术的硕士生。")''',
    1,
)
text = text.replace(
    '''        assertThat(result.getMessage())\n                .isEqualTo("Welcome 张同学 (202600000001).");\n        assertThat(result.getMessages().get("ja-JP"))''',
    '''        assertThat(result.getMessages().get("en-US"))\n                .isEqualTo("Welcome 张同学 (202600000001).");\n        assertThat(result.getMessages().get("ja-JP"))''',
    1,
)
text = text.replace(
    '''        assertThat(result.getMessage())\n                .isEqualTo("张同学，请先到国际学生服务中心完成报到。");\n        assertThat(result.getMessages())''',
    '''        assertThat(result.getMessages())''',
    1,
)
if 'getMessage()' in text:
    raise RuntimeError('legacy getMessage assertion remains')
test.write_text(text, encoding='utf-8')

(root / 'scripts/ci/apply_canonical_welcome_test_fix.py').unlink()
(root / '.github/workflows/agent-canonical-welcome-test-fix.yml').unlink()
