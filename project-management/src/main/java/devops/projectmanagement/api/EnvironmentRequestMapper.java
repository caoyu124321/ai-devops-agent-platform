package devops.projectmanagement.api;

import devops.projectmanagement.environment.EnvironmentTarget;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** 将 REST 的无敏感目标字段转换为服务层类型；Controller 不承载目标类型分支规则。 */
@Component
public class EnvironmentRequestMapper {
    public EnvironmentTarget target(EnvironmentController.EnvironmentRequest request) {
        return build(request.targetType(), request.apiServerUrl(), request.contextName(), request.defaultNamespace(),
                request.allowedNamespaces(), request.host(), request.port(), request.hostKeyFingerprint(), request.endpointUrl(),
                request.certificateFingerprint());
    }

    public EnvironmentTarget target(EnvironmentController.UpdateEnvironmentRequest request) {
        return build(request.targetType(), request.apiServerUrl(), request.contextName(), request.defaultNamespace(),
                request.allowedNamespaces(), request.host(), request.port(), request.hostKeyFingerprint(), request.endpointUrl(),
                request.certificateFingerprint());
    }

    private EnvironmentTarget build(String type, String apiServerUrl, String contextName, String defaultNamespace,
                                    List<String> allowedNamespaces, String host, int port, String hostKeyFingerprint,
                                    String endpointUrl, String certificateFingerprint) {
        if ("KUBERNETES".equals(type)) {
            return new EnvironmentTarget.KubernetesTarget(apiServerUrl, contextName, defaultNamespace, allowedNamespaces);
        }
        if ("LINUX_HOST".equals(type)) {
            return new EnvironmentTarget.LinuxHostTarget(host, port, hostKeyFingerprint);
        }
        if ("WINDOWS_HOST".equals(type)) {
            return new EnvironmentTarget.WindowsHostTarget(endpointUrl, certificateFingerprint);
        }
        throw new ProjectManagementException("TARGET_CONFIGURATION_INVALID", HttpStatus.BAD_REQUEST, "不支持的环境目标类型");
    }
}
