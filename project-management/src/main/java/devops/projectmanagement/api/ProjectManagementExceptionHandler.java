package devops.projectmanagement.api;

import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 统一输出安全错误体，业务异常中不得附带凭据或外部系统原始响应。 */
@RestControllerAdvice(basePackages = "devops.projectmanagement")
public class ProjectManagementExceptionHandler {
    @ExceptionHandler(ProjectManagementException.class)
    ResponseEntity<Map<String, Object>> handle(ProjectManagementException exception) {
        return ResponseEntity.status(exception.status()).body(Map.of(
                "code", exception.code(),
                "message", exception.getMessage(),
                "traceId", UUID.randomUUID().toString(),
                "details", Map.of()));
    }
}
