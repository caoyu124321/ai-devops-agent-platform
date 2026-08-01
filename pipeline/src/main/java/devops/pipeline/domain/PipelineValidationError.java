package devops.pipeline.domain;

/** YAML 校验错误只返回字段定位和可执行建议，不回显可能包含敏感内容的 YAML 片段。 */
public record PipelineValidationError(String path, int line, int column, String ruleCode, String message,
                                      String suggestion) {
}
