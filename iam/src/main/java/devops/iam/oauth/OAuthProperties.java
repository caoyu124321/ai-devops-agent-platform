package devops.iam.oauth;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** OAuth 外部地址与密钥只允许经受控配置注入，不能由请求头或客户端输入推导。 */
@ConfigurationProperties(prefix = "app.oauth")
public class OAuthProperties {
    private static final String MCP_RESOURCE_PATH = "/mcp";
    private static final String MCP_RESOURCE_METADATA_PATH = "/.well-known/oauth-protected-resource/mcp";
    private String issuer = "http://127.0.0.1:8080";
    private boolean allowLoopbackHttp = true;
    private boolean autoActivateLoopbackClients = true;
    private int clientRegistrationHourlyLimit = 10;
    private String unknownClientDefaultStatus = "ACTIVE";
    private Map<String, String> clientStatusOverrides = new LinkedHashMap<>();
    private String signingPrivateKey = "";
    private String signingPublicKey = "";

    public String getIssuer() { return issuer; }

    public void setIssuer(String issuer) { this.issuer = issuer; }

    public boolean isAllowLoopbackHttp() { return allowLoopbackHttp; }

    public void setAllowLoopbackHttp(boolean allowLoopbackHttp) { this.allowLoopbackHttp = allowLoopbackHttp; }

    public boolean isAutoActivateLoopbackClients() { return autoActivateLoopbackClients; }

    public void setAutoActivateLoopbackClients(boolean autoActivateLoopbackClients) { this.autoActivateLoopbackClients = autoActivateLoopbackClients; }

    public int getClientRegistrationHourlyLimit() { return clientRegistrationHourlyLimit; }

    public void setClientRegistrationHourlyLimit(int clientRegistrationHourlyLimit) { this.clientRegistrationHourlyLimit = clientRegistrationHourlyLimit; }

    public String getUnknownClientDefaultStatus() { return unknownClientDefaultStatus; }

    public void setUnknownClientDefaultStatus(String unknownClientDefaultStatus) { this.unknownClientDefaultStatus = unknownClientDefaultStatus; }

    public Map<String, String> getClientStatusOverrides() { return clientStatusOverrides; }

    public void setClientStatusOverrides(Map<String, String> clientStatusOverrides) { this.clientStatusOverrides = clientStatusOverrides == null ? new LinkedHashMap<>() : new LinkedHashMap<>(clientStatusOverrides); }

    public String getSigningPrivateKey() { return signingPrivateKey; }

    public void setSigningPrivateKey(String signingPrivateKey) { this.signingPrivateKey = signingPrivateKey; }

    public String getSigningPublicKey() { return signingPublicKey; }

    public void setSigningPublicKey(String signingPublicKey) { this.signingPublicKey = signingPublicKey; }

    /**
     * OAuth resource 必须与受保护资源元数据中公布的 URL 完全一致，避免客户端、授权服务器和 MCP 服务对受众产生歧义。
     */
    public String getMcpResource() { return issuer + MCP_RESOURCE_PATH; }

    public String getMcpProtectedResourceMetadataUrl() { return issuer + MCP_RESOURCE_METADATA_PATH; }
}
