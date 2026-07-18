package devops.iam.api;

import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class IamExceptionHandler {
    @ExceptionHandler(IamException.class)
    ResponseEntity<Map<String, Object>> handleIam(IamException exception) {
        return ResponseEntity.status(exception.status()).body(body(exception.code(), exception.getMessage()));
    }

    @ExceptionHandler(DuplicateKeyException.class)
    ResponseEntity<Map<String, Object>> handleConflict() {
        return ResponseEntity.badRequest().body(body("IDENTIFIER_CONFLICT", "用户名或邮箱已被使用"));
    }

    private Map<String, Object> body(String code, String message) {
        return Map.of("code", code, "message", message, "traceId", UUID.randomUUID().toString(), "details", Map.of());
    }
}
