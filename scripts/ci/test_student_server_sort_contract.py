from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
openapi = (ROOT / 'backend-java/model/src/main/resources/admin/openapi-student-catalog.yaml').read_text(encoding='utf-8')
view = (ROOT / 'frontend/src/views/admin/AdminDataView.vue').read_text(encoding='utf-8')
logic = (ROOT / 'frontend/src/views/admin/AdminDataView.logic.ts').read_text(encoding='utf-8')
service_path = ROOT / 'backend-java/server/src/main/java/com/wust/dormitory/admin/StudentCatalogQueryService.java'
assert service_path.exists(), 'missing StudentCatalogQueryService'
service = service_path.read_text(encoding='utf-8')

assert 'sortField' in openapi and 'sortDirection' in openapi, 'student catalog OpenAPI must expose server-side sort parameters'
assert 'useTableSort(students' not in view, 'student list must not sort only the current page in Vue'
assert 'sortField' in logic and 'sortDirection' in logic, 'frontend must send sort state to server'
assert 'SORT_COLUMNS' in service or 'ALLOWED_SORT' in service, 'backend must whitelist sort fields'
assert 'sortField' in service and 'sortDirection' in service
print('Student server-side sort contract passed')
