package devops.iam.contract;

/** IAM 对其他业务模块暴露的唯一授权入口。 */
public interface AuthorizationService {
    AuthorizationDecision authorize(AuthorizationRequest request);

    void requireAuthorization(AuthorizationRequest request);
}
