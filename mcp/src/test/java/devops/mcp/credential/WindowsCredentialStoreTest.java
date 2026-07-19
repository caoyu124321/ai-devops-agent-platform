package devops.mcp.credential;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import devops.mcp.identity.StoredSession;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WindowsCredentialStoreTest {
    @Test
    void shouldStoreReadAndClearCurrentWindowsUserToken() {
        WindowsCredentialStore store = new WindowsCredentialStore();
        String baseUrl = "http://127.0.0.1:" + UUID.randomUUID();
        StoredSession session = new StoredSession("test-token-" + UUID.randomUUID(), "test-user", Instant.now().plusSeconds(60));
        try {
            store.replaceActive(baseUrl, session);

            StoredSession read = store.readActive(baseUrl).orElseThrow();

            assertEquals(session.token(), read.token());
            assertEquals(session.userId(), read.userId());
        } finally {
            store.clearActive(baseUrl);
        }
        assertFalse(store.readActive(baseUrl).isPresent());
    }

    @Test
    void shouldPersistPendingLoginLinkAcrossMcpProcessRestarts() {
        WindowsCredentialStore store = new WindowsCredentialStore();
        String baseUrl = "http://127.0.0.1:" + UUID.randomUUID();
        PendingLoginLink link = new PendingLoginLink("link-1", "http://127.0.0.1/login?token=holder", "holder",
                "session-token", Instant.now().plusSeconds(60).toString());
        try {
            store.replacePendingLoginLink(baseUrl, link);

            PendingLoginLink read = store.readPendingLoginLink(baseUrl).orElseThrow();

            assertEquals(link, read);
        } finally {
            store.clearPendingLoginLink(baseUrl);
        }
        assertFalse(store.readPendingLoginLink(baseUrl).isPresent());
    }
}
