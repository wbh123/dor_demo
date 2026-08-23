from pathlib import Path
import re

ROOT = Path('private-repo')


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding='utf-8')


def write(path: str, text: str) -> None:
    (ROOT / path).write_text(text, encoding='utf-8')


def replace_once(path: str, old: str, new: str) -> None:
    text = read(path)
    if new in text:
        return
    if old not in text:
        raise RuntimeError(f'{path}: marker missing: {old[:100]!r}')
    write(path, text.replace(old, new, 1))


# ---- Site metadata assets: move JdbcTemplate SQL into the existing SiteMetadataMapper XML. ----
mapper_java_path = 'backend-java/server/src/main/java/com/wust/dormitory/admin/mapper/SiteMetadataMapper.java'
mapper_java = read(mapper_java_path)
if 'import java.util.Map;' not in mapper_java:
    mapper_java = mapper_java.replace('import org.apache.ibatis.annotations.Param;\n', 'import org.apache.ibatis.annotations.Param;\n\nimport java.util.Map;\n', 1)
if 'Map<String, Object> findAsset' not in mapper_java:
    mapper_java = mapper_java.replace(
        '\n}\n',
        '''\n\n    Map<String, Object> findAsset(@Param("slot") String slot);\n\n    int upsertAsset(\n            @Param("slot") String slot,\n            @Param("objectKey") String objectKey,\n            @Param("originalFileName") String originalFileName,\n            @Param("contentType") String contentType,\n            @Param("fileSize") long fileSize,\n            @Param("sha256") String sha256,\n            @Param("updatedBy") Long updatedBy);\n}\n''',
        1,
    )
write(mapper_java_path, mapper_java)

mapper_xml_path = 'backend-java/server/src/main/resources/mapper/admin/SiteMetadataMapper.xml'
mapper_xml = read(mapper_xml_path)
if 'id="findAsset"' not in mapper_xml:
    block = '''\n\n    <select id="findAsset" resultType="map">\n        SELECT slot, object_key, original_file_name, content_type, file_size, sha256, updated_at\n        FROM site_metadata_asset\n        WHERE slot = #{slot}\n        LIMIT 1\n    </select>\n\n    <insert id="upsertAsset">\n        INSERT INTO site_metadata_asset\n            (slot, object_key, original_file_name, content_type, file_size, sha256, updated_by)\n        VALUES\n            (#{slot}, #{objectKey}, #{originalFileName}, #{contentType}, #{fileSize}, #{sha256}, #{updatedBy})\n        ON DUPLICATE KEY UPDATE\n            object_key = VALUES(object_key),\n            original_file_name = VALUES(original_file_name),\n            content_type = VALUES(content_type),\n            file_size = VALUES(file_size),\n            sha256 = VALUES(sha256),\n            updated_by = VALUES(updated_by),\n            updated_at = CURRENT_TIMESTAMP(3)\n    </insert>\n'''
    mapper_xml = mapper_xml.replace('\n</mapper>\n', block + '</mapper>\n', 1)
write(mapper_xml_path, mapper_xml)

service_path = 'backend-java/server/src/main/java/com/wust/dormitory/admin/SiteMetadataAssetService.java'
service = read(service_path)
service = service.replace('import com.wust.dormitory.audit.AuditService;\n', 'import com.wust.dormitory.admin.mapper.SiteMetadataMapper;\nimport com.wust.dormitory.audit.AuditService;\n', 1) if 'import com.wust.dormitory.admin.mapper.SiteMetadataMapper;' not in service else service
service = service.replace('import org.springframework.jdbc.core.JdbcTemplate;\n', '')
service = service.replace('import java.util.List;\n', '')
service = service.replace('    private final JdbcTemplate jdbcTemplate;\n', '    private final SiteMetadataMapper mapper;\n')
service = service.replace('            JdbcTemplate jdbcTemplate,\n', '            SiteMetadataMapper mapper,\n')
service = service.replace('        this.jdbcTemplate = jdbcTemplate;\n', '        this.mapper = mapper;\n')
service, count = re.subn(
    r'''\s+jdbcTemplate\.update\("""\n\s+INSERT INTO site_metadata_asset[\s\S]*?\n\s+""", slot, objectKey, safeName\(file\.getOriginalFilename\(\)\), contentType,\n\s+bytes\.length, sha, user == null \? null : user\.userId\(\)\);''',
    '''\n                mapper.upsertAsset(\n                        slot,\n                        objectKey,\n                        safeName(file.getOriginalFilename()),\n                        contentType,\n                        bytes.length,\n                        sha,\n                        user == null ? null : user.userId());''',
    service,
    count=1,
)
if count == 0 and 'mapper.upsertAsset(' not in service:
    raise RuntimeError('SiteMetadataAssetService: embedded upsert SQL marker missing')
service, count = re.subn(
    r'''    private Map<String, Object> find\(String slot\) \{\n        List<Map<String, Object>> rows = jdbcTemplate\.queryForList\(\n                "SELECT slot, object_key, original_file_name, content_type, file_size, sha256, updated_at FROM site_metadata_asset WHERE slot=\? LIMIT 1",\n                slot\);\n        return rows\.isEmpty\(\) \? null : rows\.get\(0\);\n    \}''',
    '''    private Map<String, Object> find(String slot) {\n        return mapper.findAsset(slot);\n    }''',
    service,
    count=1,
)
if count == 0 and 'return mapper.findAsset(slot);' not in service:
    raise RuntimeError('SiteMetadataAssetService: embedded select SQL marker missing')
if 'JdbcTemplate' in service or 'INSERT INTO site_metadata_asset' in service or 'SELECT slot, object_key' in service:
    raise RuntimeError('SiteMetadataAssetService: embedded SQL/JdbcTemplate remains')
write(service_path, service)

# Update the focused regression test to mock the mapper boundary instead of JDBC.
test_path = 'backend-java/server/src/test/java/com/wust/dormitory/admin/SiteMetadataAssetServiceRegressionTest.java'
test = read(test_path)
if 'import com.wust.dormitory.admin.mapper.SiteMetadataMapper;' not in test:
    test = test.replace('import com.wust.dormitory.audit.AuditService;\n', 'import com.wust.dormitory.admin.mapper.SiteMetadataMapper;\nimport com.wust.dormitory.audit.AuditService;\n', 1)
test = test.replace('import org.springframework.jdbc.core.JdbcTemplate;\n', '')
test = test.replace('import java.util.List;\n', '')
test = test.replace('import static org.mockito.ArgumentMatchers.anyString;\n', '')
test = test.replace('import static org.mockito.ArgumentMatchers.eq;\n', '')
test = test.replace('        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);', '        SiteMetadataMapper mapper = mock(SiteMetadataMapper.class);')
test = re.sub(
    r'''        when\(jdbcTemplate\.queryForList\(anyString\(\), eq\(SiteMetadataAssetService\.SQUARE_LOGO\)\)\)\n                \.thenReturn\(List\.of\(Map\.of\(([\s\S]*?)\)\)\);''',
    r'''        when(mapper.findAsset(SiteMetadataAssetService.SQUARE_LOGO))\n                .thenReturn(Map.of(\1));''',
    test,
    count=1,
)
test = test.replace('                jdbcTemplate, storage, featureAccessService, auditService);', '                mapper, storage, featureAccessService, auditService);')
if 'JdbcTemplate' in test or 'jdbcTemplate' in test:
    raise RuntimeError('SiteMetadataAssetServiceRegressionTest: JDBC mock remains')
write(test_path, test)

# ---- Import task object-storage file SQL: keep mapper methods, move annotations into XML. ----
import_java_path = 'backend-java/server/src/main/java/com/wust/dormitory/importworkflow/mapper/ImportTaskPersistenceMapper.java'
import_java = read(import_java_path)
import_java = import_java.replace('import org.apache.ibatis.annotations.Insert;\n', '')
import_java = import_java.replace('import org.apache.ibatis.annotations.Select;\n', '')
import_java, insert_count = re.subn(r'''\n    @Insert\("""[\s\S]*?"""\)\n    int upsertObjectFile\(''', '\n    int upsertObjectFile(', import_java, count=1)
import_java, select_count = re.subn(r'''\n    @Select\("""[\s\S]*?"""\)\n    Map<String, Object> findObjectFile\(''', '\n    Map<String, Object> findObjectFile(', import_java, count=1)
if insert_count == 0 and '@Insert' in import_java:
    raise RuntimeError('ImportTaskPersistenceMapper: @Insert SQL remains')
if select_count == 0 and '@Select' in import_java:
    raise RuntimeError('ImportTaskPersistenceMapper: @Select SQL remains')
write(import_java_path, import_java)

import_xml_path = 'backend-java/server/src/main/resources/mapper/importworkflow/ImportTaskPersistenceMapper.xml'
import_xml = read(import_xml_path)
if 'id="upsertObjectFile"' not in import_xml:
    anchor = '''    <select id="findFile" resultType="map">'''
    block = '''    <insert id="upsertObjectFile">\n        INSERT INTO import_task_file\n            (task_id, storage_backend, object_key, content_type, file_size, file_bytes)\n        VALUES\n            (#{taskId}, 'OBJECT_STORAGE', #{objectKey}, #{contentType}, #{fileSize}, NULL)\n        ON DUPLICATE KEY UPDATE\n            storage_backend='OBJECT_STORAGE',\n            object_key=VALUES(object_key),\n            content_type=VALUES(content_type),\n            file_size=VALUES(file_size),\n            file_bytes=NULL,\n            created_at=CURRENT_TIMESTAMP(3)\n    </insert>\n\n    <select id="findObjectFile" resultType="map">\n        SELECT task_id AS taskId,\n               storage_backend AS storageBackend,\n               object_key AS objectKey,\n               content_type AS contentType,\n               file_size AS fileSize\n        FROM import_task_file\n        WHERE task_id=#{taskId}\n          AND storage_backend='OBJECT_STORAGE'\n    </select>\n\n'''
    if anchor not in import_xml:
        raise RuntimeError('ImportTaskPersistenceMapper.xml: findFile anchor missing')
    import_xml = import_xml.replace(anchor, block + anchor, 1)
write(import_xml_path, import_xml)

print('post-baseline embedded SQL moved into MyBatis XML')
