package devops.mcp.credential;

import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.WString;
import com.sun.jna.ptr.PointerByReference;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIOptions;
import devops.mcp.identity.McpErrorCode;
import devops.mcp.identity.McpIdentityException;
import devops.mcp.identity.StoredSession;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** 使用 Windows Generic Credential 保存 Token，避免通过文件或命令行暴露凭据。 */
public class WindowsCredentialStore implements CredentialStore {
    private static final int CRED_TYPE_GENERIC = 1;
    private static final int CRED_PERSIST_LOCAL_MACHINE = 2;
    private static final int ERROR_NOT_FOUND = 1168;
    private static final String TARGET_PREFIX = "ai-devops-mcp/";
    private static final String TOKEN_TARGET_PREFIX = "token/";
    private static final String ACTIVE_TARGET_PREFIX = "active/";
    private static final String PENDING_LOGIN_TARGET_PREFIX = "pending-login/";
    private static final String VALUE_SEPARATOR = "\n";
    private static final int PENDING_LOGIN_VALUE_PARTS = 5;
    private static final CredentialNativeApi CREDENTIAL_API = Native.load("Advapi32", CredentialNativeApi.class,
            W32APIOptions.DEFAULT_OPTIONS);

    @Override
    public Optional<StoredSession> readActive(String baseUrl) {
        String activeTarget = activeTarget(baseUrl);
        Optional<CredentialValue> active = readCredential(activeTarget);
        if (active.isEmpty()) {
            return Optional.empty();
        }
        String userId = active.get().secret();
        Optional<CredentialValue> tokenCredential = readCredential(tokenTarget(baseUrl, userId));
        if (tokenCredential.isEmpty()) {
            deleteCredential(activeTarget);
            return Optional.empty();
        }
        return Optional.of(parseSession(tokenCredential.get().secret(), userId));
    }

    @Override
    public void replaceActive(String baseUrl, StoredSession session) {
        clearActive(baseUrl);
        String tokenTarget = tokenTarget(baseUrl, session.userId());
        writeCredential(tokenTarget, session.userId(), session.expiresAt() + VALUE_SEPARATOR + session.token());
        writeCredential(activeTarget(baseUrl), session.userId(), session.userId());
    }

    @Override
    public void clearActive(String baseUrl) {
        Optional<CredentialValue> active = readCredential(activeTarget(baseUrl));
        active.ifPresent(value -> deleteCredential(tokenTarget(baseUrl, value.secret())));
        deleteCredential(activeTarget(baseUrl));
    }

    @Override
    public Optional<PendingLoginLink> readPendingLoginLink(String baseUrl) {
        return readCredential(pendingLoginTarget(baseUrl)).map(value -> parsePendingLoginLink(value.secret()));
    }

    @Override
    public void replacePendingLoginLink(String baseUrl, PendingLoginLink link) {
        String value = String.join(VALUE_SEPARATOR, link.id(), link.url(), link.token(), link.sessionToken(), link.expiresAt());
        writeCredential(pendingLoginTarget(baseUrl), "pending-login", value);
    }

    @Override
    public void clearPendingLoginLink(String baseUrl) {
        deleteCredential(pendingLoginTarget(baseUrl));
    }

    private Optional<CredentialValue> readCredential(String target) {
        PointerByReference reference = new PointerByReference();
        if (!CREDENTIAL_API.CredReadW(new WString(target), CRED_TYPE_GENERIC, 0, reference)) {
            if (Native.getLastError() == ERROR_NOT_FOUND) {
                return Optional.empty();
            }
            throw credentialFailure("无法读取 Windows 凭据管理器。");
        }
        Pointer pointer = reference.getValue();
        try {
            NativeCredential credential = new NativeCredential(pointer);
            String userName = credential.UserName == null ? "" : credential.UserName.toString();
            byte[] bytes = credential.CredentialBlob == null ? new byte[0]
                    : credential.CredentialBlob.getByteArray(0, credential.CredentialBlobSize);
            return Optional.of(new CredentialValue(userName, new String(bytes, StandardCharsets.UTF_8)));
        } finally {
            CREDENTIAL_API.CredFree(pointer);
        }
    }

    private void writeCredential(String target, String userName, String secret) {
        byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        Memory memory = new Memory(Math.max(secretBytes.length, 1));
        memory.write(0, secretBytes, 0, secretBytes.length);
        NativeCredential credential = new NativeCredential();
        credential.Type = CRED_TYPE_GENERIC;
        credential.TargetName = new WString(target);
        credential.CredentialBlobSize = secretBytes.length;
        credential.CredentialBlob = memory;
        credential.Persist = CRED_PERSIST_LOCAL_MACHINE;
        credential.UserName = new WString(userName);
        if (!CREDENTIAL_API.CredWriteW(credential, 0)) {
            throw credentialFailure("无法写入 Windows 凭据管理器。");
        }
    }

    private void deleteCredential(String target) {
        if (!CREDENTIAL_API.CredDeleteW(new WString(target), CRED_TYPE_GENERIC, 0)
                && Native.getLastError() != ERROR_NOT_FOUND) {
            throw credentialFailure("无法清理 Windows 凭据管理器。");
        }
    }

    private StoredSession parseSession(String value, String userId) {
        int separatorIndex = value.indexOf(VALUE_SEPARATOR);
        if (separatorIndex <= 0 || separatorIndex == value.length() - 1) {
            throw credentialFailure("本地登录凭据格式无效。");
        }
        try {
            Instant expiresAt = Instant.parse(value.substring(0, separatorIndex));
            return new StoredSession(value.substring(separatorIndex + VALUE_SEPARATOR.length()), userId, expiresAt);
        } catch (RuntimeException exception) {
            throw new McpIdentityException(McpErrorCode.CREDENTIAL_STORE_FAILED, "本地登录凭据格式无效。", exception);
        }
    }

    private PendingLoginLink parsePendingLoginLink(String value) {
        String[] parts = value.split(VALUE_SEPARATOR, -1);
        if (parts.length != PENDING_LOGIN_VALUE_PARTS) {
            throw credentialFailure("待完成登录链接格式无效。");
        }
        try {
            Instant.parse(parts[4]);
            return new PendingLoginLink(parts[0], parts[1], parts[2], parts[3], parts[4]);
        } catch (RuntimeException exception) {
            throw new McpIdentityException(McpErrorCode.CREDENTIAL_STORE_FAILED, "待完成登录链接格式无效。", exception);
        }
    }

    private String activeTarget(String baseUrl) {
        return TARGET_PREFIX + ACTIVE_TARGET_PREFIX + baseUrlHash(baseUrl);
    }

    private String tokenTarget(String baseUrl, String userId) {
        return TARGET_PREFIX + TOKEN_TARGET_PREFIX + baseUrlHash(baseUrl) + "/" + userId;
    }

    private String pendingLoginTarget(String baseUrl) {
        return TARGET_PREFIX + PENDING_LOGIN_TARGET_PREFIX + baseUrlHash(baseUrl);
    }

    private String baseUrlHash(String baseUrl) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(baseUrl.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte value : hash) {
                builder.append(String.format("%02x", value));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new McpIdentityException(McpErrorCode.CREDENTIAL_STORE_FAILED, "无法生成本地凭据标识。", exception);
        }
    }

    private McpIdentityException credentialFailure(String message) {
        return new McpIdentityException(McpErrorCode.CREDENTIAL_STORE_FAILED, message);
    }

    private record CredentialValue(String userName, String secret) {
    }

    /** 对应 Windows CREDENTIALW 结构，仅传递到 Advapi32，不包含业务日志。 */
    public static class NativeCredential extends Structure {
        public int Flags;
        public int Type;
        public WString TargetName;
        public WString Comment;
        public int LastWrittenLowDateTime;
        public int LastWrittenHighDateTime;
        public int CredentialBlobSize;
        public Pointer CredentialBlob;
        public int Persist;
        public int AttributeCount;
        public Pointer Attributes;
        public WString TargetAlias;
        public WString UserName;

        public NativeCredential() {
        }

        public NativeCredential(Pointer pointer) {
            super(pointer);
            read();
        }

        @Override
        protected List<String> getFieldOrder() {
            return List.of("Flags", "Type", "TargetName", "Comment", "LastWrittenLowDateTime", "LastWrittenHighDateTime",
                    "CredentialBlobSize", "CredentialBlob", "Persist", "AttributeCount", "Attributes", "TargetAlias", "UserName");
        }
    }

    private interface CredentialNativeApi extends StdCallLibrary {
        boolean CredReadW(WString targetName, int type, int flags, PointerByReference credential);

        boolean CredWriteW(NativeCredential credential, int flags);

        boolean CredDeleteW(WString targetName, int type, int flags);

        void CredFree(Pointer credential);
    }
}
