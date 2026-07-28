package devops.projectmanagement.security;

import devops.projectmanagement.api.ProjectManagementException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** 仅在内存中使用配置主密钥进行 AES-GCM 加解密，密钥原文绝不写入数据库或日志。 */
@Component
public class CredentialCryptoService {
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final String ALGORITHM_NAME = "AES-256-GCM";
    private static final int NONCE_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;
    private final String keyId;
    private final byte[] keyBytes;
    private final SecureRandom secureRandom = new SecureRandom();

    public CredentialCryptoService(@Value("${app.project-management.crypto.key-id:local-development-key}") String keyId,
                                   @Value("${app.project-management.crypto.key:}") String encodedKey) {
        this.keyId = keyId;
        this.keyBytes = decode(encodedKey);
    }

    public EncryptedPayload encrypt(String plainText) {
        if (keyBytes.length == 0) {
            throw error("CREDENTIAL_ENCRYPTION_NOT_CONFIGURED", "未配置凭据加密主密钥");
        }
        try {
            byte[] nonce = new byte[NONCE_LENGTH];
            secureRandom.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey(), new GCMParameterSpec(TAG_LENGTH_BITS, nonce));
            byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            byte[] payload = new byte[nonce.length + cipherText.length];
            System.arraycopy(nonce, 0, payload, 0, nonce.length);
            System.arraycopy(cipherText, 0, payload, nonce.length, cipherText.length);
            return new EncryptedPayload(payload, keyId, ALGORITHM_NAME);
        } catch (ProjectManagementException exception) {
            throw exception;
        } catch (Exception exception) {
            throw error("CREDENTIAL_ENCRYPTION_FAILED", "凭据加密失败");
        }
    }

    public String decrypt(byte[] payload) {
        if (keyBytes.length == 0 || payload == null || payload.length <= NONCE_LENGTH) {
            throw error("CREDENTIAL_DECRYPTION_FAILED", "凭据无法解密");
        }
        try {
            byte[] nonce = Arrays.copyOfRange(payload, 0, NONCE_LENGTH);
            byte[] cipherText = Arrays.copyOfRange(payload, NONCE_LENGTH, payload.length);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), new GCMParameterSpec(TAG_LENGTH_BITS, nonce));
            return new String(cipher.doFinal(cipherText), StandardCharsets.UTF_8);
        } catch (Exception exception) {
            throw error("CREDENTIAL_DECRYPTION_FAILED", "凭据无法解密");
        }
    }

    private SecretKey secretKey() {
        return new SecretKeySpec(keyBytes, "AES");
    }

    private byte[] decode(String encodedKey) {
        if (encodedKey == null || encodedKey.isBlank()) {
            return new byte[0];
        }
        try {
            byte[] decoded = Base64.getDecoder().decode(encodedKey);
            if (decoded.length != 32) {
                throw error("CREDENTIAL_ENCRYPTION_NOT_CONFIGURED", "凭据加密主密钥长度不合法");
            }
            return decoded;
        } catch (IllegalArgumentException exception) {
            throw error("CREDENTIAL_ENCRYPTION_NOT_CONFIGURED", "凭据加密主密钥不是有效 Base64");
        }
    }

    private ProjectManagementException error(String code, String message) {
        return new ProjectManagementException(code, HttpStatus.INTERNAL_SERVER_ERROR, message);
    }

    public record EncryptedPayload(byte[] payload, String keyId, String algorithm) {
    }
}
