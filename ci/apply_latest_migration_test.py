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


def collapse_adjacent(path_value: str, block: str) -> None:
    target = Path(path_value)
    content = target.read_text(encoding='utf-8')
    changed = False
    while block + block in content:
        content = content.replace(block + block, block, 1)
        changed = True
    if changed:
        target.write_text(content, encoding='utf-8')


# The compatibility helper inserts some follow-up content immediately before or
# after an existing marker. On an exact-head revalidation that marker still
# exists by design, so collapse the adjacent duplicate it would otherwise add.
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

collapse_adjacent(
    'private-repo/frontend/src/account/useAccountAdminConsole.ts',
    "import type { ScopeType } from './scopeSelection'\n",
)
collapse_adjacent(
    'private-repo/backend-java/server/src/main/java/com/wust/dormitory/importworkflow/ImportWorkflowService.java',
    '''    private Map<String, Object> policySnapshotFor(String type) {
        if (!"ROOM".equals(type)) return Map.of();
        return Map.of("roomAutoCreateBuilding", importPolicyService.roomAutoCreateBuildingEnabled());
    }

''',
)
collapse_adjacent(
    'private-repo/backend-java/server/src/main/java/com/wust/dormitory/importworkflow/JdbcImportTaskRepository.java',
    '''    private Map<String, Object> policySnapshot(Object value) {
        if (value == null) return Map.of();
        if (value instanceof Map<?, ?> source) {
            Map<String, Object> result = new LinkedHashMap<>();
            source.forEach((key, item) -> { if (key != null) result.put(String.valueOf(key), item); });
            return result;
        }
        String serialized = String.valueOf(value).trim();
        return serialized.isEmpty() ? Map.of() : read(serialized, OBJECT_MAP);
    }

''',
)
print('exact-head duplicate insertions normalized')

runpy.run_path('ci-tools/ci/apply_semantic_regression_updates.py', run_name='__main__')