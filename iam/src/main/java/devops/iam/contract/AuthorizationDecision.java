package devops.iam.contract;

import java.util.List;

/** 授权决策及其可安全展示的原因。 */
public record AuthorizationDecision(
        Decision decision,
        String reasonCode,
        List<String> matchedGrantIds,
        String decisionVersion) {
    public enum Decision { ALLOW, DENY }

    public static AuthorizationDecision deny(String reasonCode) {
        return new AuthorizationDecision(Decision.DENY, reasonCode, List.of(), "v1");
    }
}
