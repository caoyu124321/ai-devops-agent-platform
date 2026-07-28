package devops.projectmanagement.environment;

import devops.projectmanagement.domain.ConnectionStatus;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.MessageDigest;
import java.security.cert.X509Certificate;
import java.time.Duration;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.transport.verification.FingerprintVerifier;
import org.springframework.stereotype.Component;

/**
 * 只读环境校验器不执行部署或远程命令。Kubernetes 仅请求版本端点；WinRM 只做 TLS 握手并严格匹配证书指纹。
 * JDK 标准库无法安全完成 SSH 主机密钥协商，因此 Linux 目标保持不可用，直到引入协议级校验器，绝不跳过指纹校验。
 */
@Component
public class ReadOnlyEnvironmentConnectionValidator implements EnvironmentConnectionValidator {
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();

    @Override
    public ValidationResult validate(EnvironmentTarget target, String credentialId) {
        return switch (target) {
            case EnvironmentTarget.KubernetesTarget value -> validateKubernetes(value);
            case EnvironmentTarget.WindowsHostTarget value -> validateWinRm(value);
            case EnvironmentTarget.LinuxHostTarget value -> validateLinux(value);
        };
    }

    private ValidationResult validateKubernetes(EnvironmentTarget.KubernetesTarget target) {
        try {
            URI endpoint = URI.create(target.apiServerUrl()).resolve("/version");
            HttpResponse<Void> response = client.send(HttpRequest.newBuilder(endpoint).timeout(TIMEOUT).GET().build(), HttpResponse.BodyHandlers.discarding());
            return response.statusCode() < 500 ? ValidationResult.healthy() : ValidationResult.unavailable("KUBERNETES_UNAVAILABLE");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return ValidationResult.unavailable("KUBERNETES_INTERRUPTED");
        } catch (Exception exception) {
            return ValidationResult.unavailable("KUBERNETES_UNAVAILABLE");
        }
    }

    private ValidationResult validateWinRm(EnvironmentTarget.WindowsHostTarget target) {
        try {
            URI endpoint = URI.create(target.endpointUrl());
            int port = endpoint.getPort() < 0 ? 5986 : endpoint.getPort();
            try (SSLSocket socket = (SSLSocket) SSLSocketFactory.getDefault().createSocket(endpoint.getHost(), port)) {
                socket.setSoTimeout((int) TIMEOUT.toMillis());
                socket.startHandshake();
                X509Certificate certificate = (X509Certificate) socket.getSession().getPeerCertificates()[0];
                return fingerprint(certificate).equals(normalize(target.certificateFingerprint())) ? ValidationResult.healthy() : ValidationResult.unavailable("WINRM_CERTIFICATE_FINGERPRINT_MISMATCH");
            }
        } catch (Exception exception) {
            return ValidationResult.unavailable("WINRM_UNAVAILABLE");
        }
    }

    private ValidationResult validateLinux(EnvironmentTarget.LinuxHostTarget target) {
        try (SSHClient client = new SSHClient()) {
            // SSHJ 在握手阶段调用严格指纹验证器；不执行认证、命令或文件传输。
            client.addHostKeyVerifier(FingerprintVerifier.getInstance(target.hostKeyFingerprint()));
            client.setConnectTimeout((int) TIMEOUT.toMillis());
            client.connect(target.host(), target.port());
            return ValidationResult.healthy();
        } catch (Exception exception) {
            return ValidationResult.unavailable("SSH_HOST_KEY_VERIFICATION_FAILED");
        }
    }

    private String fingerprint(X509Certificate certificate) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(certificate.getEncoded());
        StringBuilder value = new StringBuilder();
        for (byte item : digest) value.append(String.format("%02X", item));
        return value.toString();
    }

    private String normalize(String fingerprint) { return fingerprint == null ? "" : fingerprint.replace("SHA256:", "").replace("SHA-256:", "").replace(":", "").toUpperCase(); }
}
