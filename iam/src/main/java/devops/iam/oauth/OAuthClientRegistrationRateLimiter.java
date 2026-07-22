package devops.iam.oauth;

import devops.iam.api.IamException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** 动态客户端注册按直接来源 IP 限流，防止匿名注册接口被批量滥用。 */
@Component
class OAuthClientRegistrationRateLimiter {
    private final OAuthProperties properties;
    private final Clock clock;
    private final ConcurrentHashMap<String, WindowCounter> counters = new ConcurrentHashMap<>();

    @Autowired
    OAuthClientRegistrationRateLimiter(OAuthProperties properties) {
        this(properties, Clock.systemUTC());
    }

    OAuthClientRegistrationRateLimiter(OAuthProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    void check(String sourceIp) {
        int limit = properties.getClientRegistrationHourlyLimit();
        if (limit < 1) {
            throw new IllegalStateException("OAuth 客户端注册限流配置必须大于 0");
        }
        Instant hour = Instant.now(clock).truncatedTo(ChronoUnit.HOURS);
        WindowCounter counter = counters.compute(sourceIp == null ? "unknown" : sourceIp, (key, current) -> {
            if (current == null || !current.windowStart().equals(hour)) return new WindowCounter(hour, new AtomicInteger(1));
            current.count().incrementAndGet();
            return current;
        });
        if (counter.count().get() > limit) {
            throw new IamException("CLIENT_REGISTRATION_RATE_LIMITED", HttpStatus.TOO_MANY_REQUESTS, "客户端注册请求过于频繁，请稍后重试");
        }
    }

    private record WindowCounter(Instant windowStart, AtomicInteger count) { }
}
