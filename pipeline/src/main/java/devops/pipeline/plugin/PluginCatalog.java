package devops.pipeline.plugin;

import java.util.Optional;

/** 插件目录负责按精确版本查找能力，是后续插件模块替换注册实现的唯一入口。 */
public interface PluginCatalog {
    Optional<PipelinePlugin> find(String name, String version);
}
