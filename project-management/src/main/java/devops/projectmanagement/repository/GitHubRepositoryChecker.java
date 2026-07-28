package devops.projectmanagement.repository;

/** GitHub 校验器只执行匿名只读访问，不接受或传递 GitHub Token。 */
public interface GitHubRepositoryChecker {
    CheckResult check(String canonicalUrl);

    enum Outcome { HEALTHY, PERMANENTLY_UNAVAILABLE, TEMPORARILY_UNAVAILABLE }

    record CheckResult(Outcome outcome, String defaultBranch, String errorCode) {
        public static CheckResult healthy(String defaultBranch) {
            return new CheckResult(Outcome.HEALTHY, defaultBranch, null);
        }

        public static CheckResult permanentlyUnavailable(String errorCode) {
            return new CheckResult(Outcome.PERMANENTLY_UNAVAILABLE, null, errorCode);
        }

        public static CheckResult temporarilyUnavailable(String errorCode) {
            return new CheckResult(Outcome.TEMPORARILY_UNAVAILABLE, null, errorCode);
        }
    }
}
