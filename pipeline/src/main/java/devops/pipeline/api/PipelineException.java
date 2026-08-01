package devops.pipeline.api;

import org.springframework.http.HttpStatus;

/** 流水线模块统一业务异常，避免将解析器、数据库或插件内部细节直接暴露给调用方。 */
public class PipelineException extends RuntimeException {
    private final String code;
    private final HttpStatus status;

    public PipelineException(String code, HttpStatus status, String message) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public String code() {
        return code;
    }

    public HttpStatus status() {
        return status;
    }
}
