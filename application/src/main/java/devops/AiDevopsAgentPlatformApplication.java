package devops;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** 平台唯一的 Spring Boot 启动入口，统一装配所有业务模块。 */
@SpringBootApplication
public class AiDevopsAgentPlatformApplication {
    public static void main(String[] args) {
        SpringApplication.run(AiDevopsAgentPlatformApplication.class, args);
    }
}
