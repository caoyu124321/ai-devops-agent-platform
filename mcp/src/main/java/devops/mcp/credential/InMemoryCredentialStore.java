package devops.mcp.credential;

import devops.mcp.identity.StoredSession;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** 仅用于自动化测试，生产环境不得使用内存实现保存长期会话。 */
public class InMemoryCredentialStore implements CredentialStore {
    private final Map<String, StoredSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, PendingLoginLink> pendingLoginLinks = new ConcurrentHashMap<>();

    @Override
    public Optional<StoredSession> readActive(String baseUrl) {
        return Optional.ofNullable(sessions.get(baseUrl));
    }

    @Override
    public void replaceActive(String baseUrl, StoredSession session) {
        sessions.put(baseUrl, session);
    }

    @Override
    public void clearActive(String baseUrl) {
        sessions.remove(baseUrl);
    }

    @Override
    public Optional<PendingLoginLink> readPendingLoginLink(String baseUrl) {
        return Optional.ofNullable(pendingLoginLinks.get(baseUrl));
    }

    @Override
    public void replacePendingLoginLink(String baseUrl, PendingLoginLink link) {
        pendingLoginLinks.put(baseUrl, link);
    }

    @Override
    public void clearPendingLoginLink(String baseUrl) {
        pendingLoginLinks.remove(baseUrl);
    }
}
