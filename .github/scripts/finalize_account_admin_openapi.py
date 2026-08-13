#!/usr/bin/env python3
from pathlib import Path

ROOT = Path.cwd()
OPENAPI_ROOT = ROOT / 'backend-java/model/src/main/resources/openapi-interface.yaml'
FRAGMENT = ROOT / 'backend-java/model/src/main/resources/account/openapi-account-admin.yaml'
TEST = ROOT / 'scripts/ci/test_account_admin_openapi.py'


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f'{label}: expected exactly one match, got {count}')
    return text.replace(old, new, 1)


FRAGMENT_TEXT = '''openapi: 3.0.3
info:
  title: 学校账户治理接口
  version: 1.0.0
paths:
  /api/v1/account-admin/accounts:
    get:
      tags: [AccountAdmin]
      operationId: listSchoolAdminAccounts
      security: [{ bearerAuth: [] }]
      parameters:
        - { name: keyword, in: query, required: false, schema: { type: string } }
      responses: { '200': { $ref: '#/components/responses/ListSuccess' }, '403': { $ref: '#/components/responses/ErrorResponse' } }
    post:
      tags: [AccountAdmin]
      operationId: createSchoolAdminAccount
      summary: 创建学校管理员并设置线下临时密码
      security: [{ bearerAuth: [] }]
      requestBody: { required: true, content: { application/json: { schema: { $ref: '#/components/schemas/CreateAccountRequest' } } } }
      responses: { '200': { $ref: '#/components/responses/ObjectSuccess' }, '400': { $ref: '#/components/responses/ErrorResponse' }, '403': { $ref: '#/components/responses/ErrorResponse' } }
  /api/v1/account-admin/accounts/{userId}/status:
    patch:
      tags: [AccountAdmin]
      operationId: changeSchoolAdminAccountStatus
      security: [{ bearerAuth: [] }]
      parameters: [{ $ref: '#/components/parameters/UserId' }]
      requestBody: { required: true, content: { application/json: { schema: { $ref: '#/components/schemas/StatusRequest' } } } }
      responses: { '200': { $ref: '#/components/responses/ObjectSuccess' }, '403': { $ref: '#/components/responses/ErrorResponse' }, '409': { $ref: '#/components/responses/ErrorResponse' } }
  /api/v1/account-admin/accounts/{userId}/recovery:
    put:
      tags: [AccountAdmin]
      operationId: replaceSchoolAdminTemporaryPassword
      summary: 人工重置管理员临时密码并要求首次登录改密
      security: [{ bearerAuth: [] }]
      parameters: [{ $ref: '#/components/parameters/UserId' }]
      requestBody: { required: true, content: { application/json: { schema: { $ref: '#/components/schemas/RecoveryRequest' } } } }
      responses: { '200': { $ref: '#/components/responses/ObjectSuccess' }, '400': { $ref: '#/components/responses/ErrorResponse' }, '403': { $ref: '#/components/responses/ErrorResponse' } }
  /api/v1/account-admin/permissions:
    get:
      tags: [AccountAdmin]
      operationId: listSchoolAdminBusinessPermissions
      security: [{ bearerAuth: [] }]
      responses: { '200': { $ref: '#/components/responses/ListSuccess' }, '403': { $ref: '#/components/responses/ErrorResponse' } }
  /api/v1/account-admin/templates:
    get:
      tags: [AccountAdmin]
      operationId: listSchoolAdminTemplates
      security: [{ bearerAuth: [] }]
      responses: { '200': { $ref: '#/components/responses/ListSuccess' }, '403': { $ref: '#/components/responses/ErrorResponse' } }
    post:
      tags: [AccountAdmin]
      operationId: createSchoolAdminTemplate
      security: [{ bearerAuth: [] }]
      requestBody: { required: true, content: { application/json: { schema: { $ref: '#/components/schemas/CreateTemplateRequest' } } } }
      responses: { '200': { $ref: '#/components/responses/ObjectSuccess' }, '400': { $ref: '#/components/responses/ErrorResponse' }, '403': { $ref: '#/components/responses/ErrorResponse' } }
  /api/v1/account-admin/templates/{templateId}/versions:
    post:
      tags: [AccountAdmin]
      operationId: publishSchoolAdminTemplateVersion
      security: [{ bearerAuth: [] }]
      parameters: [{ name: templateId, in: path, required: true, schema: { type: integer, format: int64 } }]
      requestBody: { required: true, content: { application/json: { schema: { $ref: '#/components/schemas/CreateTemplateVersionRequest' } } } }
      responses: { '200': { $ref: '#/components/responses/ObjectSuccess' }, '400': { $ref: '#/components/responses/ErrorResponse' }, '403': { $ref: '#/components/responses/ErrorResponse' } }
  /api/v1/account-admin/accounts/{userId}/profiles:
    get:
      tags: [AccountAdmin]
      operationId: listSchoolAdminProfiles
      security: [{ bearerAuth: [] }]
      parameters: [{ $ref: '#/components/parameters/UserId' }]
      responses: { '200': { $ref: '#/components/responses/ListSuccess' }, '403': { $ref: '#/components/responses/ErrorResponse' } }
    post:
      tags: [AccountAdmin]
      operationId: grantSchoolAdminProfile
      security: [{ bearerAuth: [] }]
      parameters: [{ $ref: '#/components/parameters/UserId' }]
      requestBody: { required: true, content: { application/json: { schema: { $ref: '#/components/schemas/GrantProfileRequest' } } } }
      responses: { '200': { $ref: '#/components/responses/ObjectSuccess' }, '400': { $ref: '#/components/responses/ErrorResponse' }, '403': { $ref: '#/components/responses/ErrorResponse' } }
  /api/v1/account-admin/profiles/batch:
    post:
      tags: [AccountAdmin]
      operationId: batchGrantSchoolAdminProfiles
      security: [{ bearerAuth: [] }]
      requestBody: { required: true, content: { application/json: { schema: { $ref: '#/components/schemas/BatchGrantProfileRequest' } } } }
      responses: { '200': { $ref: '#/components/responses/ListSuccess' }, '400': { $ref: '#/components/responses/ErrorResponse' }, '403': { $ref: '#/components/responses/ErrorResponse' } }
  /api/v1/account-admin/profiles/{profileId}:
    delete:
      tags: [AccountAdmin]
      operationId: revokeSchoolAdminProfile
      security: [{ bearerAuth: [] }]
      parameters: [{ name: profileId, in: path, required: true, schema: { type: integer, format: int64 } }]
      responses: { '200': { $ref: '#/components/responses/ObjectSuccess' }, '403': { $ref: '#/components/responses/ErrorResponse' } }
components:
  parameters:
    UserId: { name: userId, in: path, required: true, schema: { type: integer, format: int64 } }
  schemas:
    ScopeRequest:
      type: object
      required: [scopeType]
      properties:
        scopeType: { type: string }
        scopeRefId: { type: integer, format: int64, nullable: true }
    CreateAccountRequest:
      type: object
      required: [username, displayName, initialPassword, accountDomain, baseRole]
      properties:
        username: { type: string }
        displayName: { type: string }
        initialPassword: { type: string, minLength: 8 }
        accountDomain: { type: string, enum: [ACCOUNT, BUSINESS] }
        baseRole: { type: string, enum: [ACCOUNT_ADMIN, BUSINESS_ADMIN, DORM_STAFF] }
        staffNo: { type: string, nullable: true }
        contactPhone: { type: string, nullable: true }
        contactEmail: { type: string, nullable: true }
        templateVersionId: { type: integer, format: int64, nullable: true }
        profileName: { type: string, nullable: true }
        clientScope: { type: string, nullable: true }
        validFrom: { type: string, format: date-time, nullable: true }
        validUntil: { type: string, format: date-time, nullable: true }
        scopes: { type: array, items: { $ref: '#/components/schemas/ScopeRequest' } }
    StatusRequest:
      type: object
      required: [enabled]
      properties: { enabled: { type: boolean } }
    RecoveryRequest:
      type: object
      required: [value]
      properties: { value: { type: string, minLength: 8 } }
    CreateTemplateRequest:
      type: object
      required: [templateCode, templateName, targetBaseRole]
      properties:
        templateCode: { type: string }
        templateName: { type: string }
        targetBaseRole: { type: string, enum: [BUSINESS_ADMIN, DORM_STAFF] }
    CreateTemplateVersionRequest:
      type: object
      required: [versionName, permissionCodes, scopeTypes, webEnabled, mobileEnabled, changeReason]
      properties:
        versionName: { type: string }
        permissionCodes: { type: array, items: { type: string } }
        scopeTypes: { type: array, items: { type: string } }
        webEnabled: { type: boolean }
        mobileEnabled: { type: boolean }
        changeReason: { type: string }
    GrantProfileRequest:
      type: object
      required: [profileName, templateVersionId, clientScope, defaultProfile]
      properties:
        profileName: { type: string }
        templateVersionId: { type: integer, format: int64 }
        clientScope: { type: string }
        defaultProfile: { type: boolean }
        validFrom: { type: string, format: date-time, nullable: true }
        validUntil: { type: string, format: date-time, nullable: true }
        scopes: { type: array, items: { $ref: '#/components/schemas/ScopeRequest' } }
    BatchGrantProfileRequest:
      type: object
      required: [userIds, profile]
      properties:
        userIds: { type: array, items: { type: integer, format: int64 } }
        profile: { $ref: '#/components/schemas/GrantProfileRequest' }
  responses:
    ObjectSuccess: { description: 操作成功, content: { application/json: { schema: { $ref: '../common-response/openapi-common-response.yaml#/components/schemas/ObjectSuccessResponse' } } } }
    ListSuccess: { description: 查询成功, content: { application/json: { schema: { $ref: '../common-response/openapi-common-response.yaml#/components/schemas/ListSuccessResponse' } } } }
    ErrorResponse: { description: 业务错误, content: { application/json: { schema: { $ref: '../common-response/openapi-common-response.yaml#/components/schemas/ErrorResponse' } } } }
  securitySchemes:
    bearerAuth: { type: http, scheme: bearer, bearerFormat: Token }
'''

ROOT_REFS = '''  /api/v1/account-admin/accounts:
    $ref: 'account/openapi-account-admin.yaml#/paths/~1api~1v1~1account-admin~1accounts'
  /api/v1/account-admin/accounts/{userId}/status:
    $ref: 'account/openapi-account-admin.yaml#/paths/~1api~1v1~1account-admin~1accounts~1{userId}~1status'
  /api/v1/account-admin/accounts/{userId}/recovery:
    $ref: 'account/openapi-account-admin.yaml#/paths/~1api~1v1~1account-admin~1accounts~1{userId}~1recovery'
  /api/v1/account-admin/permissions:
    $ref: 'account/openapi-account-admin.yaml#/paths/~1api~1v1~1account-admin~1permissions'
  /api/v1/account-admin/templates:
    $ref: 'account/openapi-account-admin.yaml#/paths/~1api~1v1~1account-admin~1templates'
  /api/v1/account-admin/templates/{templateId}/versions:
    $ref: 'account/openapi-account-admin.yaml#/paths/~1api~1v1~1account-admin~1templates~1{templateId}~1versions'
  /api/v1/account-admin/accounts/{userId}/profiles:
    $ref: 'account/openapi-account-admin.yaml#/paths/~1api~1v1~1account-admin~1accounts~1{userId}~1profiles'
  /api/v1/account-admin/profiles/batch:
    $ref: 'account/openapi-account-admin.yaml#/paths/~1api~1v1~1account-admin~1profiles~1batch'
  /api/v1/account-admin/profiles/{profileId}:
    $ref: 'account/openapi-account-admin.yaml#/paths/~1api~1v1~1account-admin~1profiles~1{profileId}'

'''

TEST_TEXT = '''#!/usr/bin/env python3
from pathlib import Path
ROOT = Path(__file__).resolve().parents[2]
OPENAPI_ROOT = ROOT / "backend-java/model/src/main/resources/openapi-interface.yaml"
FRAGMENT = ROOT / "backend-java/model/src/main/resources/account/openapi-account-admin.yaml"
EXPECTED_PATHS = (
    "/api/v1/account-admin/accounts",
    "/api/v1/account-admin/accounts/{userId}/status",
    "/api/v1/account-admin/accounts/{userId}/recovery",
    "/api/v1/account-admin/permissions",
    "/api/v1/account-admin/templates",
    "/api/v1/account-admin/templates/{templateId}/versions",
    "/api/v1/account-admin/accounts/{userId}/profiles",
    "/api/v1/account-admin/profiles/batch",
    "/api/v1/account-admin/profiles/{profileId}",
)
def main() -> int:
    root_text = OPENAPI_ROOT.read_text(encoding="utf-8")
    if not FRAGMENT.exists(): raise AssertionError(f"缺少账户治理 OpenAPI 分片：{FRAGMENT.relative_to(ROOT)}")
    fragment_text = FRAGMENT.read_text(encoding="utf-8")
    for path in EXPECTED_PATHS:
        if path not in root_text: raise AssertionError(f"主 OpenAPI 未声明账户治理路径：{path}")
        if path not in fragment_text: raise AssertionError(f"账户治理 OpenAPI 分片未声明路径：{path}")
    if 'initialPassword: { type: string, minLength: 8 }' not in fragment_text: raise AssertionError('创建管理员临时密码必须至少8位')
    if 'value: { type: string, minLength: 8 }' not in fragment_text: raise AssertionError('人工重置临时密码必须至少8位')
    print("ACCOUNT_ADMIN_OPENAPI_CONTRACT_OK")
    return 0
if __name__ == "__main__": raise SystemExit(main())
'''


def main() -> None:
    FRAGMENT.parent.mkdir(parents=True, exist_ok=True)
    FRAGMENT.write_text(FRAGMENT_TEXT, encoding='utf-8')
    root = OPENAPI_ROOT.read_text(encoding='utf-8')
    if '/api/v1/account-admin/accounts:' not in root:
        root = replace_once(root, '  /api/v1/admin/dashboard:\n', ROOT_REFS + '  /api/v1/admin/dashboard:\n', 'root account-admin refs')
        OPENAPI_ROOT.write_text(root, encoding='utf-8')
    TEST.write_text(TEST_TEXT, encoding='utf-8')
    print('account-admin OpenAPI contract finalized')

if __name__ == '__main__': main()
