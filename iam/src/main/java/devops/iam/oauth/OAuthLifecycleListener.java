package devops.iam.oauth;

import devops.iam.event.PasswordChangedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** 改密后的 OAuth 撤销通过 IAM 已有事务后事件触发，避免密码事务失败时误撤销授权链。 */
@Component
class OAuthLifecycleListener {
    private final OAuthService service;

    OAuthLifecycleListener(OAuthService service) {
        this.service = service;
    }

    @EventListener
    void onPasswordChanged(PasswordChangedEvent event) {
        service.revokeUserAfterPasswordChange(event.userId());
    }
}
