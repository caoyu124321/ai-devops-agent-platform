package devops.pipeline.api;

import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 统一返回不含 YAML 原文、凭据或插件内部堆栈的流水线错误。 */
@RestControllerAdvice(basePackages = "devops.pipeline")
public class PipelineExceptionHandler {
    @ExceptionHandler(PipelineValidationException.class)
    ResponseEntity<Map<String, Object>> handleValidation(PipelineValidationException exception) {
        return ResponseEntity.status(exception.status()).body(Map.of(
                "code", exception.code(),
                "message", exception.getMessage(),
                "traceId", UUID.randomUUID().toString(),
                "details", Map.of("errors", exception.errors())));
    }

    @ExceptionHandler(PipelineException.class)
    ResponseEntity<Map<String, Object>> handle(PipelineException exception) {
        return ResponseEntity.status(exception.status()).body(Map.of(
                "code", exception.code(),
                "message", exception.getMessage(),
                "traceId", UUID.randomUUID().toString(),
                "details", Map.of()));
    }
}
