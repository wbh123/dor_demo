#!/usr/bin/env python3
from pathlib import Path

root = Path(__file__).resolve().parents[2]
service = root / 'backend-java/server/src/main/java/com/wust/dormitory/admin/StudentAdminService.java'
content = service.read_text(encoding='utf-8')
old = 'List.of("INITIAL_IMPORT", "TRANSFER_MANUAL", "ADMIN_MANUAL", "BATCH_IMPORT")'
new = 'List.of("INITIAL_IMPORT", "ADMIN_MANUAL", "BATCH_IMPORT")'
if old not in content:
    raise RuntimeError('legacy enrollment source fragment not found')
service.write_text(content.replace(old, new, 1), encoding='utf-8')
(root / 'scripts/ci/apply_single_contract_cleanup.py').unlink()
(root / '.github/workflows/agent-single-contract-cleanup.yml').unlink()
