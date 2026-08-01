package devops.pipeline.plugin;

import java.util.Set;

/** 插件以描述符声明能力，流水线只依赖该元数据而不识别具体任务实现。 */
public record PluginDescriptor(String name, String version, Set<String> requiredInputKeys,
                               Set<String> supportedTargetTypes, int timeoutSeconds) {
    public String reference() {
        return name + "@" + version;
    }
}
