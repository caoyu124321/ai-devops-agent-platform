package devops.mcp.identity;

/** 仅携带安全错误码和面向用户的信息，异常原因不携带凭据。 */
public class McpIdentityException extends RuntimeException {
    private final McpErrorCode code;

    public McpIdentityException(McpErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public McpIdentityException(McpErrorCode code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public McpErrorCode code() {
        return code;
    }
}
