from pathlib import Path
import runpy

path = Path('private-repo/backend-java/server/src/test/java/com/wust/dormitory/readiness/SystemReadinessFreshMysql84FlywayIntegrationTest.java')
text = path.read_text(encoding='utf-8')
old = '''        assertEquals("67", flyway.info().current().getVersion().getVersion(),
                "fresh database should finish at the normalized core schema version");'''
new = '''        assertEquals("69", flyway.info().current().getVersion().getVersion(),
                "fresh database should finish at the latest schema version");'''
if old in text:
    text = text.replace(old, new, 1)
elif new not in text:
    raise RuntimeError('Flyway latest-version assertion marker not found')
path.write_text(text, encoding='utf-8')
print('latest Flyway assertion aligned to V69')

runpy.run_path('ci-tools/ci/apply_semantic_regression_updates.py', run_name='__main__')
