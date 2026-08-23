from pathlib import Path

ROOT = Path('private-repo')


def patch(path: str, old: str, new: str) -> None:
    target = ROOT / path
    text = target.read_text(encoding='utf-8')
    if old in text:
        text = text.replace(old, new, 1)
    elif new not in text:
        raise RuntimeError(f'{path}: expected marker not found')
    target.write_text(text, encoding='utf-8')

patch(
    'backend-java/server/src/test/java/com/wust/dormitory/accountadmin/AccountAdminAccountServiceTest.java',
    '''    void dormStaffCreationRequiresInitialScope() {\n''',
    '''    void dormStaffCreationRequiresExplicitScopeDecision() {\n''',
)
patch(
    'backend-java/server/src/test/java/com/wust/dormitory/accountadmin/AccountAdminAccountServiceTest.java',
    '''                null,\n                null,\n                List.of());\n\n        assertThatThrownBy(() -> service.createBase(operator(), command))\n                .isInstanceOfSatisfying(BusinessException.class, exception ->\n                        assertThat(exception.getCode()).isEqualTo("BUSINESS_ADMIN_INITIAL_SCOPE_REQUIRED"));''',
    '''                null,\n                null,\n                null);\n\n        assertThatThrownBy(() -> service.createBase(operator(), command))\n                .isInstanceOfSatisfying(BusinessException.class, exception ->\n                        assertThat(exception.getCode()).isEqualTo("BUSINESS_ADMIN_INITIAL_SCOPE_REQUIRED"));''',
)

patch(
    'backend-java/server/src/test/java/com/wust/dormitory/admin/AdminHomeSiteMetadataTest.java',
    '''    void fallsBackToCurrentDashboardCopyWhenAdminHomeIsNotConfigured() {''',
    '''    void fallsBackToRequestedSchoolBrandingWhenAdminHomeIsNotConfigured() {''',
)
patch(
    'backend-java/server/src/test/java/com/wust/dormitory/admin/AdminHomeSiteMetadataTest.java',
    '''        assertThat(adminHome.get("title")).isEqualTo("宿舍管理运行概览");\n        assertThat(adminHome.get("subtitle")).isEqualTo("集中查看学生、宿舍、选寝批次与分配工作的最新情况。");''',
    '''        assertThat(adminHome.get("title")).isEqualTo("武汉科技大学");\n        assertThat(adminHome.get("subtitle")).isEqualTo("统一宿舍管理平台");''',
)

patch(
    'backend-java/server/src/test/java/com/wust/dormitory/admin/SiteMetadataServiceTest.java',
    '''    void exposesConfiguredLoginHtmlThroughPublicConfig() {''',
    '''    void exposesConfiguredLoginHtmlButIgnoresLegacyManualImageUrl() {''',
)
patch(
    'backend-java/server/src/test/java/com/wust/dormitory/admin/SiteMetadataServiceTest.java',
    '''        assertThat(login.get("imageUrl")).isEqualTo("/assets/new-login.png");''',
    '''        assertThat(login.get("imageUrl")).isEqualTo("");''',
)

print('semantic regression tests aligned with requested behavior')
