#!/usr/bin/env python3
from pathlib import Path

root = Path(__file__).resolve().parents[2]

openapi = root / 'backend-java/model/src/main/resources/openapi-interface.yaml'
text = openapi.read_text(encoding='utf-8')
old = "  /api/v1/auth/logout:\n    $ref: 'auth/openapi-auth.yaml#/paths/~1api~1v1~1auth~1logout'\n  /api/v1/auth/me:"
new = "  /api/v1/auth/logout:\n    $ref: 'auth/openapi-auth.yaml#/paths/~1api~1v1~1auth~1logout'\n  /api/v1/auth/password:\n    $ref: 'auth/openapi-auth.yaml#/paths/~1api~1v1~1auth~1password'\n  /api/v1/auth/me:"
if old not in text:
    raise RuntimeError('auth root path fragment not found')
openapi.write_text(text.replace(old, new, 1), encoding='utf-8')

validator = root / 'scripts/ci/validate_system_contracts.py'
text = validator.read_text(encoding='utf-8')
text = text.replace(
    '        "/api/v1/auth/activate",\n',
    '        "/api/v1/auth/activate",\n        "/api/v1/auth/password",\n',
    1,
)
old = '''    require(\n        "countryMessages" in dashboard_view\n        and "configuration.countryMessages().get(countryCode)" in welcome_service\n        and 'configuration.messages().get("en-US")' in welcome_service,\n        "country-specific welcome messages or English fallback are missing",\n        errors,\n    )'''
new = '''    require(\n        "countryMessages" in dashboard_view\n        and "configuration.countryMessages().get(countryCode)" in welcome_service\n        and 'configuration.messages().get("en-US")' in welcome_service\n        and "data.setMessages(renderedMessages)" in welcome_service\n        and "data.setMessage(" not in welcome_service,\n        "language and country welcome messages do not follow the canonical messages object",\n        errors,\n    )'''
if old not in text:
    raise RuntimeError('legacy welcome validation fragment not found')
validator.write_text(text.replace(old, new, 1), encoding='utf-8')

(root / 'scripts/ci/apply_contract_generation_fix.py').unlink()
(root / '.github/workflows/agent-contract-generation-fix.yml').unlink()
