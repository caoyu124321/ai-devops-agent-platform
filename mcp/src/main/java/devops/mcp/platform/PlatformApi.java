package devops.mcp.platform;

import devops.mcp.identity.LoginResult;
import devops.mcp.identity.LoginLink;
import devops.mcp.identity.LoginLinkStatus;
import devops.mcp.identity.RegistrationLink;
import devops.mcp.identity.RegistrationLinkStatus;
import devops.mcp.identity.UserSummary;

/** 已有平台 REST API 的最小访问边界，MCP 不直接依赖 IAM 实现类。 */
public interface PlatformApi {
    RegistrationLink createRegistrationLink();

    RegistrationLinkStatus registrationLinkStatus(String id, String token);

    LoginLink createLoginLink(String sessionTokenHash);

    LoginLinkStatus loginLinkStatus(String id, String token);

    LoginResult login(String login, String password);

    UserSummary currentUser(String token);

    void logout(String token);
}
