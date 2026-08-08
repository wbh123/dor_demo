from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
openapi = (ROOT / 'backend-java/model/src/main/resources/admin/openapi-student-catalog.yaml').read_text(encoding='utf-8')
view = (ROOT / 'frontend/src/views/admin/AdminDataView.vue').read_text(encoding='utf-8')
service = (ROOT / 'backend-java/server/src/main/java/com/wust/dormitory/admin/StudentAdminSortedQueryService.java').read_text(encoding='utf-8')
mapper = (ROOT / 'backend-java/server/src/main/resources/mapper/admin/StudentAdminSortedMapper.xml').read_text(encoding='utf-8')

assert 'sortField' in openapi and 'sortDirection' in openapi, 'student catalog OpenAPI must expose server-side sort parameters'
assert 'useTableSort(students' not in view, 'student list must not sort only the current page in Vue'
assert 'sortField' in view and 'sortDirection' in view, 'frontend must send sort state to server'
assert 'SORT_FIELDS' in service, 'backend must whitelist sort fields'
assert 'sortField' in service and 'sortDirection' in service
assert '<choose>' in mapper and "sortField == 'studentName'" in mapper, 'mapper must choose from known sort columns'
assert 'LIMIT #{limit} OFFSET #{offset}' in mapper, 'database query must paginate after applying the selected ORDER BY'
print('Student server-side sort contract passed')
