package devops.iam.dao;

/** IAM 已评审并由外部脚本创建的持久化表清单。 */
public enum IamTable {
    USERS("iam_users"),
    SESSIONS("iam_sessions"),
    LOGIN_LOCKS("iam_login_locks"),
    REGISTRATION_LINKS("iam_registration_links"),
    LOGIN_LINKS("iam_login_links"),
    TENANTS("iam_tenants"),
    TENANT_MEMBERS("iam_tenant_members"),
    INVITATIONS("iam_invitations"),
    AUTHORIZATION_GRANTS("iam_authorization_grants"),
    PROJECT_ROLE_BINDINGS("iam_project_role_bindings"),
    OAUTH_CLIENTS("iam_oauth_clients"),
    OAUTH_GRANTS("iam_oauth_grants"),
    OAUTH_AUTHORIZATION_CODES("iam_oauth_authorization_codes"),
    OAUTH_ACCESS_TOKENS("iam_oauth_access_tokens"),
    OAUTH_REFRESH_TOKENS("iam_oauth_refresh_tokens"),
    OAUTH_BROWSER_SESSIONS("iam_oauth_browser_sessions");

    private final String tableName;

    IamTable(String tableName) {
        this.tableName = tableName;
    }

    public String tableName() {
        return tableName;
    }
}
