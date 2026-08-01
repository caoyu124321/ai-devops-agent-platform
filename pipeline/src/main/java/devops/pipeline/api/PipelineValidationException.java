package devops.pipeline.api;

import devops.pipeline.domain.PipelineValidationError;
import java.util.List;
import org.springframework.http.HttpStatus;

/** 校验异常携带结构化错误列表，供编辑器直接定位 YAML 问题。 */
public class PipelineValidationException extends PipelineException {
    private final List<PipelineValidationError> errors;

    public PipelineValidationException(List<PipelineValidationError> errors) {
        super("PIPELINE_VALIDATION_FAILED", HttpStatus.BAD_REQUEST, "流水线 YAML 校验失败");
        this.errors = List.copyOf(errors);
    }

    public List<PipelineValidationError> errors() {
        return errors;
    }
}
