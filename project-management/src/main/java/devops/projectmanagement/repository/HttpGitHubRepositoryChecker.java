package devops.projectmanagement.repository;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * 通过 GitHub 公共 REST API 读取仓库元数据。仅 404/401/403 归类为永久不可用，网络或 5xx 不会误删配置。
 * 默认分支由响应中的非敏感字段读取，响应正文不会写入日志或异常。
 */
@Component
public class HttpGitHubRepositoryChecker implements GitHubRepositoryChecker {
    private static final Pattern GITHUB_URL = Pattern.compile("^https://github\\.com/([^/]+)/([^/]+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern DEFAULT_BRANCH = Pattern.compile("\\\"default_branch\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"])*)\\\"");
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();

    @Override
    public CheckResult check(String canonicalUrl) {
        Matcher matcher = GITHUB_URL.matcher(canonicalUrl);
        if (!matcher.matches()) {
            return CheckResult.permanentlyUnavailable("REPOSITORY_URL_INVALID");
        }
        URI endpoint = URI.create("https://api.github.com/repos/" + matcher.group(1) + "/" + matcher.group(2));
        try {
            HttpRequest request = HttpRequest.newBuilder(endpoint).timeout(TIMEOUT)
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "ai-devops-agent-platform")
                    .GET().build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                Matcher branchMatcher = DEFAULT_BRANCH.matcher(response.body());
                if (!branchMatcher.find() || branchMatcher.group(1).isBlank()) {
                    return CheckResult.permanentlyUnavailable("REPOSITORY_DEFAULT_BRANCH_MISSING");
                }
                return CheckResult.healthy(unescapeJson(branchMatcher.group(1)));
            }
            if (response.statusCode() == 401 || response.statusCode() == 403 || response.statusCode() == 404) {
                return CheckResult.permanentlyUnavailable("REPOSITORY_NOT_PUBLIC");
            }
            return CheckResult.temporarilyUnavailable("GITHUB_HTTP_" + response.statusCode());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return CheckResult.temporarilyUnavailable("GITHUB_INTERRUPTED");
        } catch (Exception exception) {
            return CheckResult.temporarilyUnavailable("GITHUB_UNAVAILABLE");
        }
    }

    private String unescapeJson(String value) {
        return value.replace("\\\"", "\"").replace("\\\\", "\\");
    }
}
