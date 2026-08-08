#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
INTERFACE = ROOT / "backend-java/model/src/main/resources/openapi-interface.yaml"
SITE_SPEC = ROOT / "backend-java/model/src/main/resources/admin/openapi-site-metadata.yaml"
STUDENT_CATALOG_SPEC = ROOT / "backend-java/model/src/main/resources/admin/openapi-student-catalog.yaml"
SITE_SERVICE = ROOT / "backend-java/server/src/main/java/com/wust/dormitory/admin/SiteMetadataService.java"
ADMIN_SITE_CONTROLLER = ROOT / "backend-java/server/src/main/java/com/wust/dormitory/admin/AdminLoginPageSettingController.java"

interface = INTERFACE.read_text(encoding="utf-8")

required_refs = (
    "/api/v1/public/site-config:",
    "admin/openapi-site-metadata.yaml#/paths/~1api~1v1~1public~1site-config",
    "/api/v1/admin/settings/login-page:",
    "admin/openapi-site-metadata.yaml#/paths/~1api~1v1~1admin~1settings~1login-page",
    "/api/v1/admin/settings/theme:",
    "admin/openapi-site-metadata.yaml#/paths/~1api~1v1~1admin~1settings~1theme",
    "/api/v1/platform/site-metadata:",
    "admin/openapi-site-metadata.yaml#/paths/~1api~1v1~1platform~1site-metadata",
    "/api/v1/admin/student-catalog:",
    "admin/openapi-student-catalog.yaml#/paths/~1api~1v1~1admin~1student-catalog",
)
for token in required_refs:
    if token not in interface:
        raise AssertionError(f"聚合 OpenAPI 缺少站点/学生目录契约：{token}")

for path in (SITE_SPEC, STUDENT_CATALOG_SPEC):
    if not path.exists():
        raise AssertionError(f"缺少拆分 OpenAPI 契约：{path.relative_to(ROOT)}")

site_spec = SITE_SPEC.read_text(encoding="utf-8")
student_spec = STUDENT_CATALOG_SPEC.read_text(encoding="utf-8")
for token in (
    "operationId: getPublicSiteConfig",
    "operationId: getAdminLoginPageSetting",
    "operationId: updateAdminLoginPageSetting",
    "operationId: updateAdminThemeSetting",
    "operationId: getPlatformSiteMetadata",
    "operationId: updatePlatformSiteMetadata",
    "PlatformSiteMetadataUpdateRequest:",
    "LoginPageContentUpdateRequest:",
    "SchoolThemeUpdateRequest:",
    "enum: [blue, green]",
):
    if token not in site_spec:
        raise AssertionError(f"站点元数据 OpenAPI 缺少：{token}")
for token in (
    "operationId: getAdminStudentCatalog",
    "sortField",
    "sortDirection",
):
    if token not in student_spec:
        raise AssertionError(f"学生目录 OpenAPI 缺少：{token}")

controllers = {
    "backend-java/server/src/main/java/com/wust/dormitory/admin/PublicSiteMetadataController.java": (
        "com.wust.dormitory.model.api.PublicSiteMetadataApi",
        "implements PublicSiteMetadataApi",
    ),
    "backend-java/server/src/main/java/com/wust/dormitory/admin/AdminLoginPageSettingController.java": (
        "com.wust.dormitory.model.api.AdminSiteMetadataApi",
        "implements AdminSiteMetadataApi",
    ),
    "backend-java/server/src/main/java/com/wust/dormitory/platform/PlatformSiteMetadataController.java": (
        "com.wust.dormitory.model.api.PlatformSiteMetadataApi",
        "implements PlatformSiteMetadataApi",
    ),
    "backend-java/server/src/main/java/com/wust/dormitory/admin/AdminStudentSortedQueryController.java": (
        "com.wust.dormitory.model.api.AdminStudentCatalogApi",
        "implements AdminStudentCatalogApi",
    ),
}
for relative, tokens in controllers.items():
    path = ROOT / relative
    if not path.exists():
        raise AssertionError(f"缺少接口实现：{relative}")
    text = path.read_text(encoding="utf-8")
    for token in tokens:
        if token not in text:
            raise AssertionError(f"{relative} 未通过生成 API 承载路由：{token}")
    if "@RequestMapping(" in text or "@GetMapping" in text or "@PutMapping" in text:
        raise AssertionError(f"{relative} 仍在绕过 OpenAPI 手写路由注解")

service = SITE_SERVICE.read_text(encoding="utf-8")
admin_controller = ADMIN_SITE_CONTROLLER.read_text(encoding="utf-8")
for token in (
    'SITE_THEME',
    'result.put("theme", theme())',
    'updateThemeForSchoolAdmin',
):
    if token not in service:
        raise AssertionError(f"站点元数据服务缺少学校级主题能力：{token}")
if "updateAdminThemeSetting" not in admin_controller:
    raise AssertionError("学校管理员站点 Controller 尚未实现生成式主题更新端点")

print("Site metadata, school theme and admin student catalog OpenAPI contract passed")
