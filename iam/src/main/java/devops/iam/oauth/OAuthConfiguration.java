package devops.iam.oauth;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** 仅注册 OAuth 受控配置，不在此处启动独立应用或 Web 容器。 */
@Configuration
@EnableConfigurationProperties(OAuthProperties.class)
class OAuthConfiguration {
}
