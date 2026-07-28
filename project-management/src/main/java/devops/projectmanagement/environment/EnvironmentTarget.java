package devops.projectmanagement.environment;

import devops.projectmanagement.domain.EnvironmentTargetType;
import java.util.List;

/** 类型专属目标配置；该对象不包含凭据秘密，仅持有由环境版本引用的凭据 ID。 */
public sealed interface EnvironmentTarget permits EnvironmentTarget.KubernetesTarget, EnvironmentTarget.LinuxHostTarget,
        EnvironmentTarget.WindowsHostTarget {
    EnvironmentTargetType type();

    record KubernetesTarget(String apiServerUrl, String contextName, String defaultNamespace, List<String> allowedNamespaces)
            implements EnvironmentTarget {
        @Override public EnvironmentTargetType type() { return EnvironmentTargetType.KUBERNETES; }
    }

    record LinuxHostTarget(String host, int port, String hostKeyFingerprint) implements EnvironmentTarget {
        @Override public EnvironmentTargetType type() { return EnvironmentTargetType.LINUX_HOST; }
    }

    record WindowsHostTarget(String endpointUrl, String certificateFingerprint) implements EnvironmentTarget {
        @Override public EnvironmentTargetType type() { return EnvironmentTargetType.WINDOWS_HOST; }
    }
}
