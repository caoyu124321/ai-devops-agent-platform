package devops.pipeline.domain;

import java.util.List;
import java.util.Map;

/** 解析后的 YAML 定义，是流水线核心与持久化/插件层之间的无执行语义数据边界。 */
public record PipelineDefinition(String documentName, String repositoryId, String defaultBranch,
                                 Map<String, PipelineParameter> parameters, List<PipelineStage> stages) {
    /**
     * 参数声明只描述运行时输入的名称、类型和必填性。流水线核心不解释参数所对应的具体任务，
     * 从而使构建、部署等插件能够独立演进。
     */
    public record PipelineParameter(String name, Type type, boolean required) {
        public enum Type { STRING, NUMBER, BOOLEAN, ENVIRONMENT }
    }

    public record PipelineStage(String name, int sequenceNo, List<PipelineStep> steps) {
    }

    public record PipelineStep(String id, int sequenceNo, String pluginName, String pluginVersion,
                               Map<String, Object> input) {
    }
}
