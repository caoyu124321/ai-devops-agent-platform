package devops.projectmanagement.domain;

/** 凭据停用后不能供新环境或新运行引用，但历史版本不被物理删除。 */
public enum CredentialStatus {
    ACTIVE,
    DISABLED
}
