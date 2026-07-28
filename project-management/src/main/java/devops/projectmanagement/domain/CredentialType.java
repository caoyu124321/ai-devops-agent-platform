package devops.projectmanagement.domain;

/** 已确认的凭据类型决定允许加密保存的字段集合，禁止使用自由格式秘密载荷。 */
public enum CredentialType {
    KUBECONFIG,
    SSH_PASSWORD,
    SSH_PRIVATE_KEY,
    WINRM_PASSWORD,
    GITHUB_TOKEN
}
