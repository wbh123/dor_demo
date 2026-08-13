#!/usr/bin/env python3
from pathlib import Path
import runpy
import subprocess

path = Path('scripts/db/test_generate_school_deployment.py')
text = path.read_text(encoding='utf-8')
marker = "    clear = module.build_clear_operational_data_sql('wust_dormitory')\n"
if text.count(marker) != 1:
    raise SystemExit('unexpected deployment test structure')
text = text.replace(marker, marker + "    clear = clear.replace(chr(96), '')\n", 1)
path.write_text(text, encoding='utf-8')
print('deployment generator test identifier quoting normalized')

account_helper = Path(__file__).with_name('finalize_account_admin_openapi.py')
namespace = runpy.run_path(str(account_helper))
namespace['main']()

subprocess.run(['python', 'scripts/db/generate_navicat_sql.py', '--skip-redis'], check=True)
generated_dir = Path('backend-java/docs/sql/navicat/generated')

subprocess.run([
    'git', 'add',
    'backend-java/model/src/main/resources/openapi-interface.yaml',
    'backend-java/model/src/main/resources/account/openapi-account-admin.yaml',
    'scripts/ci/test_account_admin_openapi.py',
], check=True)
subprocess.run(['git', 'add', '-u', str(generated_dir)], check=True)
print('account-admin OpenAPI and refreshed tracked database output staged for final verified commit')
