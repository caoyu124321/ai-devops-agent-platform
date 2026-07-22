package devops.iam.oauth;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import devops.iam.api.IamException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

/** 确保同一来源在一个小时窗口内最多完成十次客户端注册。 */
class OAuthClientRegistrationRateLimiterTest {
    @Test
    void shouldRejectEleventhRegistrationFromSameIpInOneHour() {
        OAuthProperties properties = new OAuthProperties();
        properties.setClientRegistrationHourlyLimit(10);
        OAuthClientRegistrationRateLimiter limiter = new OAuthClientRegistrationRateLimiter(properties,
                Clock.fixed(Instant.parse("2026-07-22T14:00:00Z"), ZoneOffset.UTC));

        for (int index = 0; index < 10; index++) {
            assertDoesNotThrow(() -> limiter.check("203.0.113.10"));
        }

        assertThrows(IamException.class, () -> limiter.check("203.0.113.10"));
    }
}
