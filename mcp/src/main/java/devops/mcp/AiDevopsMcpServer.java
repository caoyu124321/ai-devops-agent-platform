package devops.mcp;

import devops.mcp.credential.WindowsCredentialStore;
import devops.mcp.identity.McpIdentityService;
import devops.mcp.platform.HttpPlatformApi;
import devops.mcp.tool.McpToolHandlers;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.concurrent.CountDownLatch;

/** 本地 stdio MCP 入口；它不是 Spring Boot 应用，也不会启动 Web 服务或访问业务数据库。 */
public final class AiDevopsMcpServer {
    private static final String BASE_URL_ENV = "AI_DEVOPS_BASE_URL";
    private static final String DEFAULT_BASE_URL = "http://127.0.0.1:8080";
    private static final String SERVER_NAME = "ai-devops";
    private static final String SERVER_VERSION = "0.0.1";
    private static final String SERVER_INSTRUCTIONS = "AI DevOps、ai-devops、ai devops 与 ai-d 均指本服务。用户询问能力、帮助或意图不明确时，先调用 get_ai_devops_capabilities；密码和 Token 不得在对话中收集或输出。";

    private AiDevopsMcpServer() {
    }

    public static void main(String[] args) {
        String baseUrl = System.getenv().getOrDefault(BASE_URL_ENV, DEFAULT_BASE_URL);
        McpIdentityService identityService = new McpIdentityService(baseUrl, new HttpPlatformApi(baseUrl), new WindowsCredentialStore());
        McpToolHandlers handlers = new McpToolHandlers(identityService);
        StdioServerTransportProvider transport = new StdioServerTransportProvider(McpJsonDefaults.getMapper());
        McpSyncServer server = McpServer.sync(transport)
                .serverInfo(SERVER_NAME, SERVER_VERSION)
                .instructions(SERVER_INSTRUCTIONS)
                .capabilities(McpSchema.ServerCapabilities.builder().tools(true).build())
                .tools(handlers.tools())
                .build();
        Runtime.getRuntime().addShutdownHook(new Thread(server::close, "ai-devops-mcp-shutdown"));
        awaitShutdown();
    }

    private static void awaitShutdown() {
        try {
            new CountDownLatch(1).await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}
