package devops.iam.dao;

/** IAM 已评审并由外部脚本创建的持久化表清单。 */
public enum IamTable {
    USERS("iam_users"),
    SESSIONS("iam_sessions"),
    LOGIN_LOCKS("iam_login_locks"),
    TENANTS("iam_tenants"),
    TENANT_MEMBERS("iam_tenant_members"),
    INVITATIONS("iam_invitations"),
    AUTHORIZATION_GRANTS("iam_authorization_grants"),
    PROJECT_ROLE_BINDINGS("iam_project_role_bindings");

    private final String tableName;

    IamTable(String tableName) {
        this.tableName = tableName;
    }

    public String tableName() {
        return tableName;
    }
}
