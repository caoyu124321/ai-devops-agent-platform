package devops.projectmanagement.api;

import org.springframework.http.HttpStatus;

/** 项目管理模块仅暴露稳定业务错误码，避免将持久化和敏感连接细节返回给调用方。 */
public class ProjectManagementException extends RuntimeException {
    private final String code;
    private final HttpStatus status;

    public ProjectManagementException(String code, HttpStatus status, String message) {
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
