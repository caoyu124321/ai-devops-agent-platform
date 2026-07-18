package devops.controller;

import java.time.Instant;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 提供平台进程存活探针，不包含业务状态判断。 */
@RestController
@RequestMapping("/api")
public class HeartbeatController {
    @GetMapping("/heartbeat")
    public Map<String, Object> heartbeat() {
        return Map.of("status", "UP", "timestamp", Instant.now().toString());
    }
}
