from pathlib import Path
import subprocess

ROOT = Path('private-repo')


def patch(path: str, old: str, new: str) -> None:
    target = ROOT / path
    text = target.read_text(encoding='utf-8')
    if old in text:
        text = text.replace(old, new, 1)
    elif new not in text:
        raise RuntimeError(f'{path}: expected marker not found')
    target.write_text(text, encoding='utf-8')

# Create-account input must distinguish "no decision supplied" (null) from
# "explicitly no data permission" (empty list). Grant commands keep their
# existing empty-list normalization because the decision has already been made.
patch(
    'backend-java/server/src/main/java/com/wust/dormitory/accountadmin/AccountAdminService.java',
    '''    public record CreateAccountCommand(
            String username, String displayName, String initialPassword,
            String accountDomain, String baseRole, String staffNo,
            String contactPhone, String contactEmail, Long templateVersionId,
            String profileName, String clientScope, LocalDateTime validFrom,
            LocalDateTime validUntil, List<ScopeCommand> scopes) {
        public CreateAccountCommand {
            clientScope = clientScope == null || clientScope.isBlank() ? "BOTH" : clientScope;
            scopes = scopes == null ? List.of() : List.copyOf(scopes);
        }
    }''',
    '''    public record CreateAccountCommand(
            String username, String displayName, String initialPassword,
            String accountDomain, String baseRole, String staffNo,
            String contactPhone, String contactEmail, Long templateVersionId,
            String profileName, String clientScope, LocalDateTime validFrom,
            LocalDateTime validUntil, List<ScopeCommand> scopes) {
        public CreateAccountCommand {
            clientScope = clientScope == null || clientScope.isBlank() ? "BOTH" : clientScope;
            scopes = scopes == null ? null : List.copyOf(scopes);
        }
    }''',
)

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

# Remove accidentally tracked interpreter caches from the Git index. Keep any
# files in the worktree: once untracked, the generic ignore rules below prevent
# Python/test runs from re-adding them before the final commit.
subprocess.run(
    ['git', '-C', str(ROOT), 'rm', '--cached', '-r', '--ignore-unmatch',
     'scripts/db/baseline/__pycache__'],
    check=True,
)

gitignore = ROOT / '.gitignore'
gitignore_text = gitignore.read_text(encoding='utf-8')
ignore_block = '\n# Python interpreter caches\n**/__pycache__/\n*.py[cod]\n'
if '**/__pycache__/' not in gitignore_text or '*.py[cod]' not in gitignore_text:
    gitignore.write_text(gitignore_text.rstrip() + ignore_block, encoding='utf-8')

print('semantic regression tests aligned; generated Python bytecode removed from Git index')
