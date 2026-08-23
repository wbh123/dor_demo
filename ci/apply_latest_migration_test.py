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

# The compatibility helper inserts each new aggregate OpenAPI path immediately
# after an existing path. On an exact-head revalidation the existing path is
# intentionally still present, so normalize any adjacent duplicate asset refs
# before API generation. This keeps the validation helper idempotent without
# changing the already-validated private commit.
aggregate = Path('private-repo/backend-java/model/src/main/resources/openapi-interface.yaml')
aggregate_text = aggregate.read_text(encoding='utf-8')
asset_blocks = [
    "  /api/v1/public/site-assets/{slot}:\n    $ref: 'admin/openapi-site-metadata.yaml#/paths/~1api~1v1~1public~1site-assets~1{slot}'\n",
    "  /api/v1/admin/settings/site-assets/{slot}:\n    $ref: 'admin/openapi-site-metadata.yaml#/paths/~1api~1v1~1admin~1settings~1site-assets~1{slot}'\n",
    "  /api/v1/platform/site-metadata/assets/{slot}:\n    $ref: 'admin/openapi-site-metadata.yaml#/paths/~1api~1v1~1platform~1site-metadata~1assets~1{slot}'\n",
]
for block in asset_blocks:
    while block + block in aggregate_text:
        aggregate_text = aggregate_text.replace(block + block, block, 1)
aggregate.write_text(aggregate_text, encoding='utf-8')
print('aggregate OpenAPI asset refs normalized for exact-head revalidation')

runpy.run_path('ci-tools/ci/apply_semantic_regression_updates.py', run_name='__main__')