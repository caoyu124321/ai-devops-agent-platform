package devops.mcp.credential;

import devops.mcp.identity.StoredSession;
import java.util.Optional;

/** 将平台 Token 与业务存储隔离，便于替换为受保护的系统凭据仓库。 */
public interface CredentialStore {
    Optional<StoredSession> readActive(String baseUrl);

    void replaceActive(String baseUrl, StoredSession session);

    void clearActive(String baseUrl);

    Optional<PendingLoginLink> readPendingLoginLink(String baseUrl);

    void replacePendingLoginLink(String baseUrl, PendingLoginLink link);

    void clearPendingLoginLink(String baseUrl);
}
