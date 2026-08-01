package devops.pipeline.plugin;

import java.util.Map;

/** 具体任务只能经此契约接入，流水线编排器不得依赖 Maven、Docker 或 Kubernetes 类型。 */
public interface PipelinePlugin {
    PluginDescriptor descriptor();

    PluginInputValidation validateInput(Map<String, Object> input);

    /** 具体插件自行执行任务；调用方只接收通用结果，不感知任务类型。 */
    default PluginExecutionResult execute(PluginExecutionRequest request) {
        return PluginExecutionResult.failed("PLUGIN_EXECUTION_NOT_IMPLEMENTED", "插件未实现执行能力");
    }

    /** 调度器请求取消时交由插件处理其资源；默认实现表示无需额外取消动作。 */
    default void cancel(String executionId) {
    }

    record PluginInputValidation(boolean valid, String message) {
        public static PluginInputValidation accepted() {
            return new PluginInputValidation(true, null);
        }

        public static PluginInputValidation rejected(String message) {
            return new PluginInputValidation(false, message);
        }
    }

    record PluginExecutionRequest(String executionId, String runId, String stepRunId, String projectId,
                                  String repositoryId, String sourceBranch, String sourceCommit,
                                  Map<String, Object> input, Map<String, Object> runtimeContext) {
    }

    record PluginExecutionResult(Status status, String failureCode, String failureMessage,
                                 Map<String, Object> output, java.util.List<LogEntry> logs) {
        public static PluginExecutionResult succeeded(Map<String, Object> output, java.util.List<LogEntry> logs) {
            return new PluginExecutionResult(Status.SUCCEEDED, null, null, Map.copyOf(output), java.util.List.copyOf(logs));
        }

        public static PluginExecutionResult failed(String code, String message) {
            return new PluginExecutionResult(Status.FAILED, code, message, Map.of(), java.util.List.of());
        }
    }

    enum Status { SUCCEEDED, FAILED, CANCELED, TIMED_OUT }

    record LogEntry(String level, String message) {
    }
}
