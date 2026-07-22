package devops.iam.oauth;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/** 使用 Ed25519 签发 ID Token；生产密钥必须从受控配置注入，开发环境才允许临时密钥。 */
@Component
class OidcTokenSigner {
    private static final String ALGORITHM = "EdDSA";
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final OAuthProperties properties;
    private final KeyPair keyPair;
    private final String keyId;

    OidcTokenSigner(OAuthProperties properties) {
        this.properties = properties;
        this.keyPair = loadKeyPair(properties);
        this.keyId = Base64.getUrlEncoder().withoutPadding().encodeToString(sha256(keyPair.getPublic().getEncoded())).substring(0, 16);
    }

    String sign(String userId, String clientId, Instant now) {
        try {
            String header = encode(Map.of("alg", ALGORITHM, "typ", "JWT", "kid", keyId));
            Map<String, Object> claims = new LinkedHashMap<>();
            claims.put("iss", properties.getIssuer());
            claims.put("sub", userId);
            claims.put("aud", clientId);
            claims.put("iat", now.getEpochSecond());
            claims.put("exp", now.plus(OAuthConstants.ACCESS_TOKEN_TTL).getEpochSecond());
            String payload = encode(claims);
            String input = header + "." + payload;
            Signature signature = Signature.getInstance("Ed25519");
            signature.initSign(keyPair.getPrivate());
            signature.update(input.getBytes(StandardCharsets.US_ASCII));
            return input + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(signature.sign());
        } catch (Exception exception) {
            throw new IllegalStateException("无法签发 OIDC ID Token", exception);
        }
    }

    Map<String, Object> jwk() {
        byte[] encoded = keyPair.getPublic().getEncoded();
        byte[] rawPublicKey = java.util.Arrays.copyOfRange(encoded, encoded.length - 32, encoded.length);
        return Map.of("kty", "OKP", "crv", "Ed25519", "x", Base64.getUrlEncoder().withoutPadding().encodeToString(rawPublicKey), "kid", keyId,
                "use", "sig", "alg", ALGORITHM);
    }

    private KeyPair loadKeyPair(OAuthProperties properties) {
        try {
            if (!properties.getSigningPrivateKey().isBlank() && !properties.getSigningPublicKey().isBlank()) {
                KeyFactory factory = KeyFactory.getInstance("Ed25519");
                PrivateKey privateKey = factory.generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(properties.getSigningPrivateKey())));
                PublicKey publicKey = factory.generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(properties.getSigningPublicKey())));
                return new KeyPair(publicKey, privateKey);
            }
            return KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        } catch (Exception exception) {
            throw new IllegalStateException("OAuth 签名密钥配置无效", exception);
        }
    }

    private String encode(Object value) {
        try {
            return Base64.getUrlEncoder().withoutPadding().encodeToString(objectMapper.writeValueAsBytes(value));
        } catch (Exception exception) {
            throw new IllegalStateException("无法编码 OIDC Token", exception);
        }
    }

    private byte[] sha256(byte[] value) {
        try {
            return java.security.MessageDigest.getInstance("SHA-256").digest(value);
        } catch (Exception exception) {
            throw new IllegalStateException("无法计算 OIDC 密钥标识", exception);
        }
    }
}
