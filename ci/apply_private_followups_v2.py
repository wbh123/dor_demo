from pathlib import Path

ROOT = Path('private-repo')


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding='utf-8')


def write(path: str, content: str) -> None:
    target = ROOT / path
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(content, encoding='utf-8')


def replace_once(path: str, old: str, new: str) -> None:
    text = read(path)
    count = text.count(old)
    if count == 0:
        if new in text:
            return
        raise RuntimeError(f'{path}: marker not found: {old[:120]!r}')
    if count != 1:
        raise RuntimeError(f'{path}: expected one marker, found {count}: {old[:120]!r}')
    write(path, text.replace(old, new, 1))


def replace_count(path: str, old: str, new: str, expected: int) -> None:
    text = read(path)
    count = text.count(old)
    if count == 0 and text.count(new) >= expected:
        return
    if count != expected:
        raise RuntimeError(f'{path}: expected {expected} markers, found {count}: {old[:120]!r}')
    write(path, text.replace(old, new))


# Frontend: successful HTTP response completes one explicit attempt; the next click gets a new UUID.
replace_once(
    'frontend/src/components/admin/ImportWorkflowModal.vue',
    "import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'",
    "import { computed, onBeforeUnmount, ref, watch } from 'vue'",
)
replace_once(
    'frontend/src/components/admin/ImportWorkflowModal.vue',
    "    const task = (response.data.data ?? {}) as DataObject\n    selected.value = task\n    message.value = '文件已上传，后台预检任务已创建。可以关闭窗口，稍后重新查看结果。'",
    "    const task = (response.data.data ?? {}) as DataObject\n    selected.value = task\n    attemptId.value = ''\n    message.value = '文件已上传，后台预检任务已创建。可以关闭窗口，稍后重新查看结果。'",
)
replace_once(
    'frontend/src/views/admin/AdminImportQualityView.vue',
    "    const task = (response.data.data ?? {}) as DataObject\n    selected.value = task\n    upsertTaskSummary(task)",
    "    const task = (response.data.data ?? {}) as DataObject\n    selected.value = task\n    uploadAttemptId.value = ''\n    upsertTaskSummary(task)",
)

# Frontend: narrow hierarchical scope state so Vue's generated v-model assignments type-check.
replace_once(
    'frontend/src/account/useAccountAdminConsole.ts',
    "import type { DataObject } from '../api/types'\nimport { useAccountAdminSession } from './session'",
    "import type { DataObject } from '../api/types'\nimport { useAccountAdminSession } from './session'\nimport type { ScopeType } from './scopeSelection'",
)
replace_once(
    'frontend/src/account/useAccountAdminConsole.ts',
    "  scopeType: 'BUILDING',",
    "  scopeType: 'BUILDING' as ScopeType,",
)
replace_once(
    'frontend/src/account/useAccountAdminConsole.ts',
    "  scopeType: 'SCHOOL',",
    "  scopeType: 'SCHOOL' as ScopeType,",
)

# Frontend: URL values remain response-only preview state; save requests contain only editable text fields.
replace_once(
    'frontend/src/views/platform/PlatformSiteMetadataView.vue',
    "function updatePayload() {\n  return { branding: { ...branding }, login: { ...login }, schoolAdminEditable: schoolAdminEditable.value, theme: { ...theme }, adminHome: { ...adminHome } }\n}",
    "function updatePayload() {\n  return { branding: { schoolName: branding.schoolName }, login: { html: login.html }, schoolAdminEditable: schoolAdminEditable.value, theme: { ...theme }, adminHome: { ...adminHome } }\n}",
)
replace_once(
    'frontend/src/views/admin/AdminSiteSettingsView.vue',
    "    const response = await api.put<ObjectSuccessResponse>('/api/v1/admin/settings/login-page', {\n      html: state.html.trim(), imageUrl: state.imageUrl,\n    })",
    "    const response = await api.put<ObjectSuccessResponse>('/api/v1/admin/settings/login-page', {\n      html: state.html.trim(),\n    })",
)

# Backend: capture the policy at task creation, expose it, and preserve it through JDBC persistence.
replace_count(
    'backend-java/server/src/main/java/com/wust/dormitory/importworkflow/ImportWorkflowService.java',
    "                    normalizedKey,\n                    Instant.now());",
    "                    normalizedKey,\n                    policySnapshotFor(normalizedType),\n                    Instant.now());",
    2,
)
replace_once(
    'backend-java/server/src/main/java/com/wust/dormitory/importworkflow/ImportWorkflowService.java',
    "        result.put(\"idempotencyKey\", task.idempotencyKey());\n        result.put(\"status\", task.status());",
    "        result.put(\"idempotencyKey\", task.idempotencyKey());\n        result.put(\"policySnapshot\", task.policySnapshot());\n        result.put(\"status\", task.status());",
)
replace_once(
    'backend-java/server/src/main/java/com/wust/dormitory/importworkflow/ImportWorkflowService.java',
    "    private String normalizeIdempotencyKey(String supplied, String type, String digest) {",
    "    private Map<String, Object> policySnapshotFor(String type) {\n        if (!\"ROOM\".equals(type)) return Map.of();\n        return Map.of(\"roomAutoCreateBuilding\", importPolicyService.roomAutoCreateBuildingEnabled());\n    }\n\n    private String normalizeIdempotencyKey(String supplied, String type, String digest) {",
)

replace_once(
    'backend-java/server/src/main/java/com/wust/dormitory/importworkflow/JdbcImportTaskRepository.java',
    "                string(header.get(\"idempotencyKey\")),\n                string(header.get(\"status\")),",
    "                string(header.get(\"idempotencyKey\")),\n                policySnapshot(header.get(\"policySnapshot\")),\n                string(header.get(\"status\")),",
)
replace_once(
    'backend-java/server/src/main/java/com/wust/dormitory/importworkflow/JdbcImportTaskRepository.java',
    "        params.put(\"idempotencyKey\", task.idempotencyKey());\n        params.put(\"status\", task.status());",
    "        params.put(\"idempotencyKey\", task.idempotencyKey());\n        params.put(\"policySnapshot\", json(task.policySnapshot()));\n        params.put(\"status\", task.status());",
)
replace_once(
    'backend-java/server/src/main/java/com/wust/dormitory/importworkflow/JdbcImportTaskRepository.java',
    "    private int invalidRowCount(List<Map<String, Object>> errors) {",
    "    private Map<String, Object> policySnapshot(Object value) {\n        if (value == null) return Map.of();\n        if (value instanceof Map<?, ?> source) {\n            Map<String, Object> result = new LinkedHashMap<>();\n            source.forEach((key, item) -> { if (key != null) result.put(String.valueOf(key), item); });\n            return result;\n        }\n        String serialized = String.valueOf(value).trim();\n        return serialized.isEmpty() ? Map.of() : read(serialized, OBJECT_MAP);\n    }\n\n    private int invalidRowCount(List<Map<String, Object>> errors) {",
)

replace_once(
    'backend-java/server/src/main/resources/mapper/importworkflow/ImportTaskPersistenceMapper.xml',
    "        idempotency_key AS idempotencyKey,\n        task_status AS status,",
    "        idempotency_key AS idempotencyKey,\n        policy_snapshot AS policySnapshot,\n        task_status AS status,",
)
replace_once(
    'backend-java/server/src/main/resources/mapper/importworkflow/ImportTaskPersistenceMapper.xml',
    "        (task_id, import_type, file_name, file_digest, idempotency_key,\n         task_status, total_rows, processed_rows, invalid_rows, mutation_count,",
    "        (task_id, import_type, file_name, file_digest, idempotency_key, policy_snapshot,\n         task_status, total_rows, processed_rows, invalid_rows, mutation_count,",
)
replace_once(
    'backend-java/server/src/main/resources/mapper/importworkflow/ImportTaskPersistenceMapper.xml',
    "        (#{task.taskId}, #{task.importType}, #{task.fileName}, #{task.digest}, #{task.idempotencyKey},\n         #{task.status}, #{task.totalRows}, #{task.processedRows}, #{task.invalidRows}, #{task.mutationCount},",
    "        (#{task.taskId}, #{task.importType}, #{task.fileName}, #{task.digest}, #{task.idempotencyKey}, #{task.policySnapshot},\n         #{task.status}, #{task.totalRows}, #{task.processedRows}, #{task.invalidRows}, #{task.mutationCount},",
)
replace_once(
    'backend-java/server/src/main/resources/mapper/importworkflow/ImportTaskPersistenceMapper.xml',
    "            idempotency_key=VALUES(idempotency_key),\n            task_status=VALUES(task_status),",
    "            idempotency_key=VALUES(idempotency_key),\n            policy_snapshot=VALUES(policy_snapshot),\n            task_status=VALUES(task_status),",
)

# V69 is still private/unreleased, so keep this feature's schema changes in the same migration.
write('backend-java/server/src/main/resources/db/migration/V69__add_site_metadata_assets.sql', '''-- V69: controlled site assets, auditable import policy snapshots, and final admin defaults.\n-- V1-V68 remain immutable.\n\nCREATE TABLE IF NOT EXISTS site_metadata_asset (\n    slot VARCHAR(32) NOT NULL PRIMARY KEY COMMENT 'SQUARE_LOGO/HORIZONTAL_LOGO/LOGIN_IMAGE',\n    object_key VARCHAR(512) NOT NULL,\n    original_file_name VARCHAR(255) NOT NULL,\n    content_type VARCHAR(64) NOT NULL,\n    file_size BIGINT NOT NULL,\n    sha256 CHAR(64) NOT NULL,\n    updated_by BIGINT NULL,\n    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),\n    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),\n    UNIQUE KEY uk_site_metadata_asset_object_key (object_key),\n    KEY idx_site_metadata_asset_sha256 (sha256),\n    CONSTRAINT fk_site_metadata_asset_updated_by\n        FOREIGN KEY (updated_by) REFERENCES app_user(id) ON DELETE SET NULL\n) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;\n\nALTER TABLE import_task\n    ADD COLUMN policy_snapshot JSON NULL\n        COMMENT '创建导入任务时的学校导入策略快照' AFTER idempotency_key;\nUPDATE import_task SET policy_snapshot=JSON_OBJECT() WHERE policy_snapshot IS NULL;\nALTER TABLE import_task\n    MODIFY COLUMN policy_snapshot JSON NOT NULL COMMENT '创建导入任务时的学校导入策略快照';\n\n-- Preserve customized welcome messages while upgrading legacy defaults and adding the display switch.\nUPDATE system_setting\nSET setting_value = JSON_SET(\n        setting_value,\n        '$.displayEnabled', COALESCE(JSON_EXTRACT(setting_value, '$.displayEnabled'), JSON_EXTRACT('true', '$')),\n        '$.countryMessages', COALESCE(JSON_EXTRACT(setting_value, '$.countryMessages'), JSON_OBJECT())\n    ),\n    version = version + 1\nWHERE setting_key='STUDENT_WELCOME_MESSAGE'\n  AND JSON_VALID(setting_value)=1\n  AND JSON_TYPE(setting_value)='OBJECT';\n\nUPDATE system_setting\nSET setting_value = JSON_SET(\n        setting_value,\n        '$.messages.\"zh-CN\"',\n        '欢迎 {{studentName}} 加入武汉科技大学。请先完善个人偏好，再根据楼层、剩余铺位和室友匹配情况心仪的宿舍与床位。'\n    ),\n    version = version + 1\nWHERE setting_key='STUDENT_WELCOME_MESSAGE'\n  AND JSON_UNQUOTE(JSON_EXTRACT(setting_value, '$.messages.\"zh-CN\"')) IN (\n      '欢迎加入武汉科技大学宿舍智能选择系统。请先完善个人偏好，再根据楼层、剩余铺位和室友匹配情况选择合适的宿舍与床位。',\n      '欢迎使用武汉科技大学学生宿舍智能选择系统。请先完成个人偏好，再选择合适的宿舍与床位。'\n  );\n\nUPDATE system_setting\nSET setting_value = JSON_SET(\n        setting_value,\n        '$.messages.\"en-US\"',\n        'Welcome {{studentName}} to Wuhan University of Science and Technology. Please complete your personal preferences first, then choose your preferred dormitory and bed based on the floor, remaining beds, and roommate compatibility.'\n    ),\n    version = version + 1\nWHERE setting_key='STUDENT_WELCOME_MESSAGE'\n  AND JSON_UNQUOTE(JSON_EXTRACT(setting_value, '$.messages.\"en-US\"')) =\n      'Welcome to the Wuhan University of Science and Technology dormitory selection system. Complete your personal preferences first, then choose a suitable room and bed.';\n''')

# OpenAPI-first site asset endpoints; URL fields are no longer editable request properties.
write('backend-java/model/src/main/resources/admin/openapi-site-metadata.yaml', '''openapi: 3.0.3\ninfo:\n  title: Wust Dormitory Select 站点元数据接口\n  version: 1.2.0\npaths:\n  /api/v1/public/site-config:\n    get:\n      tags: [PublicSiteMetadata]\n      operationId: getPublicSiteConfig\n      summary: 获取登录页公开站点配置\n      responses:\n        '200':\n          description: 当前公开站点配置\n          content:\n            application/json:\n              schema:\n                $ref: '../common-response/openapi-common-response.yaml#/components/schemas/ObjectSuccessResponse'\n  /api/v1/public/site-assets/{slot}:\n    get:\n      tags: [SiteMetadataAsset]\n      operationId: getPublicSiteAsset\n      summary: 读取当前受控站点图片素材\n      parameters:\n        - $ref: '#/components/parameters/SiteAssetSlotPath'\n      responses:\n        '200':\n          description: 图片二进制内容\n          content:\n            application/octet-stream:\n              schema: { type: string, format: binary }\n        '404': { $ref: '#/components/responses/ErrorResponse' }\n  /api/v1/admin/settings/login-page:\n    get:\n      tags: [AdminSiteMetadata]\n      operationId: getAdminLoginPageSetting\n      summary: 获取学校管理员可维护的登录页配置\n      security: [{ bearerAuth: [] }]\n      responses:\n        '200':\n          description: 当前登录页配置及是否可编辑\n          content:\n            application/json:\n              schema:\n                $ref: '../common-response/openapi-common-response.yaml#/components/schemas/ObjectSuccessResponse'\n        '401': { $ref: '#/components/responses/ErrorResponse' }\n        '403': { $ref: '#/components/responses/ErrorResponse' }\n    put:\n      tags: [AdminSiteMetadata]\n      operationId: updateAdminLoginPageSetting\n      summary: 更新学校管理员登录页展示文本\n      security: [{ bearerAuth: [] }]\n      requestBody:\n        required: true\n        content:\n          application/json:\n            schema: { $ref: '#/components/schemas/LoginPageContentUpdateRequest' }\n      responses:\n        '200':\n          description: 更新后的登录页配置\n          content:\n            application/json:\n              schema:\n                $ref: '../common-response/openapi-common-response.yaml#/components/schemas/ObjectSuccessResponse'\n        '400': { $ref: '#/components/responses/ErrorResponse' }\n        '401': { $ref: '#/components/responses/ErrorResponse' }\n        '403': { $ref: '#/components/responses/ErrorResponse' }\n  /api/v1/admin/settings/site-assets/{slot}:\n    post:\n      tags: [SiteMetadataAsset]\n      operationId: uploadAdminSiteAsset\n      summary: 学校管理员上传受控站点图片素材\n      security: [{ bearerAuth: [] }]\n      parameters:\n        - $ref: '#/components/parameters/SiteAssetSlotPath'\n      requestBody:\n        required: true\n        content:\n          multipart/form-data:\n            schema:\n              type: object\n              required: [file]\n              properties:\n                file: { type: string, format: binary }\n      responses:\n        '200':\n          description: 上传成功\n          content:\n            application/json:\n              schema:\n                $ref: '../common-response/openapi-common-response.yaml#/components/schemas/ObjectSuccessResponse'\n        '400': { $ref: '#/components/responses/ErrorResponse' }\n        '401': { $ref: '#/components/responses/ErrorResponse' }\n        '403': { $ref: '#/components/responses/ErrorResponse' }\n  /api/v1/admin/settings/student-theme:\n    get:\n      tags: [AdminSiteMetadata]\n      operationId: getAdminStudentThemeSetting\n      summary: 获取学生端主题及学校修改权限\n      security: [{ bearerAuth: [] }]\n      responses:\n        '200':\n          description: 当前学生端主题设置\n          content:\n            application/json:\n              schema:\n                $ref: '../common-response/openapi-common-response.yaml#/components/schemas/ObjectSuccessResponse'\n        '401': { $ref: '#/components/responses/ErrorResponse' }\n        '403': { $ref: '#/components/responses/ErrorResponse' }\n    put:\n      tags: [AdminSiteMetadata]\n      operationId: updateAdminStudentThemeSetting\n      summary: 学校管理员修改学生端主题\n      security: [{ bearerAuth: [] }]\n      requestBody:\n        required: true\n        content:\n          application/json:\n            schema: { $ref: '#/components/schemas/StudentThemeUpdateRequest' }\n      responses:\n        '200':\n          description: 更新后的学生端主题设置\n          content:\n            application/json:\n              schema:\n                $ref: '../common-response/openapi-common-response.yaml#/components/schemas/ObjectSuccessResponse'\n        '400': { $ref: '#/components/responses/ErrorResponse' }\n        '401': { $ref: '#/components/responses/ErrorResponse' }\n        '403': { $ref: '#/components/responses/ErrorResponse' }\n  /api/v1/platform/site-metadata:\n    get:\n      tags: [PlatformSiteMetadata]\n      operationId: getPlatformSiteMetadata\n      summary: 获取系统管理员站点元数据配置\n      security: [{ bearerAuth: [] }]\n      responses:\n        '200':\n          description: 当前站点元数据配置\n          content:\n            application/json:\n              schema:\n                $ref: '../common-response/openapi-common-response.yaml#/components/schemas/ObjectSuccessResponse'\n        '401': { $ref: '#/components/responses/ErrorResponse' }\n        '403': { $ref: '#/components/responses/ErrorResponse' }\n    put:\n      tags: [PlatformSiteMetadata]\n      operationId: updatePlatformSiteMetadata\n      summary: 更新系统管理员站点文本、标题与主题配置\n      security: [{ bearerAuth: [] }]\n      requestBody:\n        required: true\n        content:\n          application/json:\n            schema: { $ref: '#/components/schemas/PlatformSiteMetadataUpdateRequest' }\n      responses:\n        '200':\n          description: 更新后的站点元数据配置\n          content:\n            application/json:\n              schema:\n                $ref: '../common-response/openapi-common-response.yaml#/components/schemas/ObjectSuccessResponse'\n        '400': { $ref: '#/components/responses/ErrorResponse' }\n        '401': { $ref: '#/components/responses/ErrorResponse' }\n        '403': { $ref: '#/components/responses/ErrorResponse' }\n  /api/v1/platform/site-metadata/assets/{slot}:\n    post:\n      tags: [SiteMetadataAsset]\n      operationId: uploadPlatformSiteAsset\n      summary: 系统管理员上传受控站点图片素材\n      security: [{ bearerAuth: [] }]\n      parameters:\n        - $ref: '#/components/parameters/SiteAssetSlotPath'\n      requestBody:\n        required: true\n        content:\n          multipart/form-data:\n            schema:\n              type: object\n              required: [file]\n              properties:\n                file: { type: string, format: binary }\n      responses:\n        '200':\n          description: 上传成功\n          content:\n            application/json:\n              schema:\n                $ref: '../common-response/openapi-common-response.yaml#/components/schemas/ObjectSuccessResponse'\n        '400': { $ref: '#/components/responses/ErrorResponse' }\n        '401': { $ref: '#/components/responses/ErrorResponse' }\n        '403': { $ref: '#/components/responses/ErrorResponse' }\ncomponents:\n  parameters:\n    SiteAssetSlotPath:\n      name: slot\n      in: path\n      required: true\n      schema:\n        type: string\n        enum: [SQUARE_LOGO, HORIZONTAL_LOGO, LOGIN_IMAGE]\n  responses:\n    ErrorResponse:\n      description: 业务错误\n      content:\n        application/json:\n          schema:\n            $ref: '../common-response/openapi-common-response.yaml#/components/schemas/ErrorResponse'\n  schemas:\n    LoginPageContentUpdateRequest:\n      type: object\n      required: [html]\n      properties:\n        html:\n          type: string\n          minLength: 1\n          maxLength: 8000\n    StudentThemeUpdateRequest:\n      type: object\n      required: [studentTheme]\n      properties:\n        studentTheme:\n          type: string\n          minLength: 1\n          maxLength: 16\n    StudentThemeSettingUpdateRequest:\n      type: object\n      required: [studentTheme, schoolAdminEditable]\n      properties:\n        studentTheme:\n          type: string\n          minLength: 1\n          maxLength: 16\n        schoolAdminEditable:\n          type: boolean\n    SiteBrandingUpdateRequest:\n      type: object\n      required: [schoolName]\n      properties:\n        schoolName:\n          type: string\n          minLength: 1\n          maxLength: 128\n    AdminHomeContentUpdateRequest:\n      type: object\n      required: [title]\n      properties:\n        title:\n          type: string\n          minLength: 1\n          maxLength: 80\n        subtitle:\n          type: string\n          maxLength: 240\n    PlatformSiteMetadataUpdateRequest:\n      type: object\n      required: [branding, login, schoolAdminEditable, theme]\n      properties:\n        branding:\n          $ref: '#/components/schemas/SiteBrandingUpdateRequest'\n        login:\n          $ref: '#/components/schemas/LoginPageContentUpdateRequest'\n        schoolAdminEditable:\n          type: boolean\n        theme:\n          $ref: '#/components/schemas/StudentThemeSettingUpdateRequest'\n        adminHome:\n          $ref: '#/components/schemas/AdminHomeContentUpdateRequest'\n  securitySchemes:\n    bearerAuth:\n      type: http\n      scheme: bearer\n      bearerFormat: Token\n''')

# Aggregate spec references for the generated Java and TypeScript clients.
replace_once(
    'backend-java/model/src/main/resources/openapi-interface.yaml',
    "  /api/v1/public/site-config:\n    $ref: 'admin/openapi-site-metadata.yaml#/paths/~1api~1v1~1public~1site-config'",
    "  /api/v1/public/site-config:\n    $ref: 'admin/openapi-site-metadata.yaml#/paths/~1api~1v1~1public~1site-config'\n  /api/v1/public/site-assets/{slot}:\n    $ref: 'admin/openapi-site-metadata.yaml#/paths/~1api~1v1~1public~1site-assets~1{slot}'",
)
replace_once(
    'backend-java/model/src/main/resources/openapi-interface.yaml',
    "  /api/v1/admin/settings/login-page:\n    $ref: 'admin/openapi-site-metadata.yaml#/paths/~1api~1v1~1admin~1settings~1login-page'",
    "  /api/v1/admin/settings/login-page:\n    $ref: 'admin/openapi-site-metadata.yaml#/paths/~1api~1v1~1admin~1settings~1login-page'\n  /api/v1/admin/settings/site-assets/{slot}:\n    $ref: 'admin/openapi-site-metadata.yaml#/paths/~1api~1v1~1admin~1settings~1site-assets~1{slot}'",
)
replace_once(
    'backend-java/model/src/main/resources/openapi-interface.yaml',
    "  /api/v1/platform/site-metadata:\n    $ref: 'admin/openapi-site-metadata.yaml#/paths/~1api~1v1~1platform~1site-metadata'",
    "  /api/v1/platform/site-metadata:\n    $ref: 'admin/openapi-site-metadata.yaml#/paths/~1api~1v1~1platform~1site-metadata'\n  /api/v1/platform/site-metadata/assets/{slot}:\n    $ref: 'admin/openapi-site-metadata.yaml#/paths/~1api~1v1~1platform~1site-metadata~1assets~1{slot}'",
)

write('backend-java/server/src/main/java/com/wust/dormitory/admin/SiteMetadataAssetController.java', '''package com.wust.dormitory.admin;\n\nimport com.wust.dormitory.common.response.ResponseFactory;\nimport com.wust.dormitory.model.api.SiteMetadataAssetApi;\nimport com.wust.dormitory.model.dto.ObjectSuccessResponse;\nimport com.wust.dormitory.security.SecurityUsers;\nimport org.springframework.core.io.InputStreamResource;\nimport org.springframework.core.io.Resource;\nimport org.springframework.http.CacheControl;\nimport org.springframework.http.MediaType;\nimport org.springframework.http.ResponseEntity;\nimport org.springframework.web.bind.annotation.RestController;\nimport org.springframework.web.multipart.MultipartFile;\n\nimport java.util.concurrent.TimeUnit;\n\n@RestController\npublic class SiteMetadataAssetController implements SiteMetadataAssetApi {\n    private final SiteMetadataAssetService service;\n\n    public SiteMetadataAssetController(SiteMetadataAssetService service) {\n        this.service = service;\n    }\n\n    @Override\n    public ResponseEntity<Resource> getPublicSiteAsset(String slot) {\n        SiteMetadataAssetService.AssetRead asset = service.open(slot);\n        return ResponseEntity.ok()\n                .contentType(MediaType.parseMediaType(asset.contentType()))\n                .contentLength(asset.contentLength())\n                .cacheControl(CacheControl.maxAge(5, TimeUnit.MINUTES).cachePublic())\n                .body(new InputStreamResource(asset.result().inputStream()));\n    }\n\n    @Override\n    public ResponseEntity<ObjectSuccessResponse> uploadAdminSiteAsset(String slot, MultipartFile file) {\n        return ResponseEntity.ok(ResponseFactory.object(\n                service.upload(SecurityUsers.requireAdmin(), slot, file)));\n    }\n\n    @Override\n    public ResponseEntity<ObjectSuccessResponse> uploadPlatformSiteAsset(String slot, MultipartFile file) {\n        return ResponseEntity.ok(ResponseFactory.object(\n                service.upload(SecurityUsers.requireSystemAdmin(), slot, file)));\n    }\n}\n''')

# Generated DTOs no longer expose editable URL properties; controller commands keep response compatibility internally.
replace_once(
    'backend-java/server/src/main/java/com/wust/dormitory/admin/AdminLoginPageSettingController.java',
    "        var command = new SiteMetadataService.LoginContentCommand(\n                request.getHtml(),\n                request.getImageUrl());",
    "        var command = new SiteMetadataService.LoginContentCommand(\n                request.getHtml(),\n                null);",
)
replace_once(
    'backend-java/server/src/main/java/com/wust/dormitory/platform/PlatformSiteMetadataController.java',
    "                new SiteMetadataService.BrandingCommand(\n                        branding.getSchoolName(),\n                        branding.getSquareLogoUrl(),\n                        branding.getHorizontalLogoUrl()),\n                new SiteMetadataService.LoginContentCommand(\n                        login.getHtml(),\n                        login.getImageUrl()),",
    "                new SiteMetadataService.BrandingCommand(\n                        branding.getSchoolName(),\n                        null,\n                        null),\n                new SiteMetadataService.LoginContentCommand(\n                        login.getHtml(),\n                        null),",
)

# Contract test also locks the removal of URL-edit request fields.
replace_once(
    'scripts/ci/test_site_metadata_openapi_contract.py',
    "student_spec = STUDENT_CATALOG_SPEC.read_text(encoding=\"utf-8\")\nfor token in (",
    "student_spec = STUDENT_CATALOG_SPEC.read_text(encoding=\"utf-8\")\nfor forbidden in (\"squareLogoUrl:\", \"horizontalLogoUrl:\", \"imageUrl:\"):\n    if forbidden in site_spec:\n        raise AssertionError(f\"站点元数据请求契约仍暴露手工 URL 修改字段：{forbidden}\")\nfor token in (",
)

# Fresh-deployment generator test must prove V69 schema/defaults are present.
replace_once(
    'scripts/db/test_generate_school_deployment.py',
    "        'DELETE FROM app_user;',\n    ]:",
    "        'DELETE FROM app_user;',\n        'CREATE TABLE IF NOT EXISTS site_metadata_asset',\n        'policy_snapshot',\n        'displayEnabled',\n        '欢迎 {{studentName}} 加入武汉科技大学。请先完善个人偏好',\n    ]:",
)

print('private follow-up patch v2 applied')
