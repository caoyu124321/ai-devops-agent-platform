package devops.projectmanagement.environment;

import devops.projectmanagement.domain.ConnectionStatus;

/**
 * 目标校验扩展点只允许只读验证。实现必须校验既定 SSH 主机指纹或 WinRM TLS 证书指纹，禁止提供跳过校验的参数。
 * 凭据内容由后续受控执行适配器短暂使用；本接口不返回、记录或传递任何明文秘密。
 */
public interface EnvironmentConnectionValidator {
    ValidationResult validate(EnvironmentTarget target, String credentialId);

    record ValidationResult(ConnectionStatus status, String errorCode) {
        public static ValidationResult healthy() { return new ValidationResult(ConnectionStatus.HEALTHY, null); }
        public static ValidationResult unavailable(String errorCode) { return new ValidationResult(ConnectionStatus.UNAVAILABLE, errorCode); }
    }
}
