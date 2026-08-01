package devops.pipeline.plugin;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Spring Bean 插件目录不关心插件来源，后续可替换为数据库或远程注册表实现。 */
@Component
public class SpringPluginCatalog implements PluginCatalog {
    private final Map<String, PipelinePlugin> plugins;

    public SpringPluginCatalog(List<PipelinePlugin> plugins) {
        Map<String, PipelinePlugin> registered = new LinkedHashMap<>();
        for (PipelinePlugin plugin : plugins) {
            String reference = plugin.descriptor().reference();
            if (registered.putIfAbsent(reference, plugin) != null) {
                throw new IllegalStateException("重复的流水线插件版本：" + reference);
            }
        }
        this.plugins = Map.copyOf(registered);
    }

    @Override
    public Optional<PipelinePlugin> find(String name, String version) {
        return Optional.ofNullable(plugins.get(name + "@" + version));
    }
}
