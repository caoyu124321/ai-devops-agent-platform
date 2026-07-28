package devops.projectmanagement.service;

import devops.iam.contract.AuthenticatedSubject;
import devops.iam.contract.AuthorizationRequest;
import devops.iam.contract.AuthorizationScope;
import devops.iam.contract.AuthorizationService;
import devops.projectmanagement.api.ProjectManagementException;
import devops.projectmanagement.dao.ProjectDao;
import devops.projectmanagement.dao.RepositoryDao;
import devops.projectmanagement.domain.ConnectionStatus;
import devops.projectmanagement.repository.GitHubRepositoryChecker;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 公开 GitHub 仓库仅以匿名 HTTPS 方式校验；永久不可访问会删除已有配置，临时故障只更新健康摘要。 */
@Service
public class RepositoryService {
    private static final String REPOSITORY_RESOURCE = "REPOSITORY";
    private static final String ACTION_VIEW = "repository.view";
    private static final String ACTION_MODIFY = "repository.modify";
    private static final int MAX_REPOSITORIES_PER_PROJECT = 20;
    private static final Pattern OWNER_OR_REPOSITORY = Pattern.compile("[A-Za-z0-9_.-]{1,100}");
    private final RepositoryDao repositoryDao;
    private final ProjectDao projectDao;
    private final AuthorizationService authorizationService;
    private final GitHubRepositoryChecker checker;

    public RepositoryService(RepositoryDao repositoryDao, ProjectDao projectDao, AuthorizationService authorizationService,
                             GitHubRepositoryChecker checker) {
        this.repositoryDao = repositoryDao;
        this.projectDao = projectDao;
        this.authorizationService = authorizationService;
        this.checker = checker;
    }

    @Transactional
    public RepositoryView create(String actorId, String projectId, String url, String defaultBranch) {
        ProjectDao.ProjectRow project = requireProject(projectId);
        require(actorId, project, ACTION_MODIFY);
        if (repositoryDao.countByProject(projectId) >= MAX_REPOSITORIES_PER_PROJECT) {
            throw error("REPOSITORY_LIMIT_EXCEEDED", HttpStatus.CONFLICT, "每个项目最多配置 20 个仓库");
        }
        String canonicalUrl = canonicalize(url);
        GitHubRepositoryChecker.CheckResult check = checker.check(canonicalUrl);
        rejectPermanent(check);
        String branch = chooseBranch(defaultBranch, check.defaultBranch());
        if (branch == null) {
            throw error("REPOSITORY_VALIDATION_FAILED", HttpStatus.SERVICE_UNAVAILABLE, "GitHub 暂时不可用，且未提供默认分支");
        }
        String repositoryId = UUID.randomUUID().toString();
        Instant now = Instant.now();
        try {
            repositoryDao.create(repositoryId, project.tenantId(), projectId, canonicalUrl, branch, status(check), now,
                    check.errorCode(), actorId, now);
            repositoryDao.createVersion(UUID.randomUUID().toString(), repositoryId, 1, canonicalUrl, branch, actorId, now);
        } catch (DuplicateKeyException exception) {
            throw error("REPOSITORY_ALREADY_EXISTS", HttpStatus.CONFLICT, "项目内仓库已存在");
        }
        return view(requireRepository(repositoryId));
    }

    public List<RepositoryView> list(String actorId, String projectId) {
        ProjectDao.ProjectRow project = requireProject(projectId);
        require(actorId, project, ACTION_VIEW);
        return repositoryDao.listByProject(projectId).stream().map(this::view).toList();
    }

    @Transactional
    public RepositoryView update(String actorId, String repositoryId, int expectedVersion, String url, String defaultBranch) {
        RepositoryDao.RepositoryRow current = requireRepository(repositoryId);
        ProjectDao.ProjectRow project = requireProject(current.projectId());
        require(actorId, project, ACTION_MODIFY);
        String canonicalUrl = canonicalize(url);
        GitHubRepositoryChecker.CheckResult check = checker.check(canonicalUrl);
        if (check.outcome() == GitHubRepositoryChecker.Outcome.PERMANENTLY_UNAVAILABLE) {
            repositoryDao.delete(repositoryId);
            throw error("REPOSITORY_REMOVED", HttpStatus.CONFLICT, "仓库已不存在或不再公开，配置已删除");
        }
        String branch = chooseBranch(defaultBranch, check.defaultBranch());
        if (branch == null) {
            throw error("REPOSITORY_VALIDATION_FAILED", HttpStatus.SERVICE_UNAVAILABLE, "GitHub 暂时不可用，且未提供默认分支");
        }
        Instant now = Instant.now();
        try {
            if (!repositoryDao.update(repositoryId, expectedVersion, canonicalUrl, branch, status(check), now, check.errorCode(), now)) {
                throw error("REPOSITORY_VERSION_CONFLICT", HttpStatus.CONFLICT, "仓库版本不匹配");
            }
        } catch (DuplicateKeyException exception) {
            throw error("REPOSITORY_ALREADY_EXISTS", HttpStatus.CONFLICT, "项目内仓库已存在");
        }
        repositoryDao.createVersion(UUID.randomUUID().toString(), repositoryId, expectedVersion + 1, canonicalUrl, branch, actorId, now);
        return view(requireRepository(repositoryId));
    }

    @Transactional
    public RepositoryView validate(String actorId, String repositoryId) {
        RepositoryDao.RepositoryRow current = requireRepository(repositoryId);
        ProjectDao.ProjectRow project = requireProject(current.projectId());
        require(actorId, project, ACTION_MODIFY);
        GitHubRepositoryChecker.CheckResult check = checker.check(current.canonicalUrl());
        if (check.outcome() == GitHubRepositoryChecker.Outcome.PERMANENTLY_UNAVAILABLE) {
            repositoryDao.delete(repositoryId);
            throw error("REPOSITORY_REMOVED", HttpStatus.CONFLICT, "仓库已不存在或不再公开，配置已删除");
        }
        Instant now = Instant.now();
        repositoryDao.updateHealth(repositoryId, status(check), now, check.errorCode(), now);
        return view(requireRepository(repositoryId));
    }

    @Transactional
    public void delete(String actorId, String repositoryId) {
        RepositoryDao.RepositoryRow current = requireRepository(repositoryId);
        require(actorId, requireProject(current.projectId()), ACTION_MODIFY);
        repositoryDao.delete(repositoryId);
    }

    private String canonicalize(String url) {
        if (url == null || url.isBlank()) {
            throw error("REPOSITORY_URL_INVALID", HttpStatus.BAD_REQUEST, "仓库地址不能为空");
        }
        try {
            URI parsed = URI.create(url.trim());
            if (!"https".equalsIgnoreCase(parsed.getScheme()) || !"github.com".equalsIgnoreCase(parsed.getHost())
                    || parsed.getQuery() != null || parsed.getFragment() != null || parsed.getUserInfo() != null) {
                throw error("REPOSITORY_URL_INVALID", HttpStatus.BAD_REQUEST, "仅支持公开 GitHub HTTPS 仓库地址");
            }
            String path = parsed.getPath() == null ? "" : parsed.getPath();
            if (path.endsWith("/")) {
                path = path.substring(0, path.length() - 1);
            }
            if (path.endsWith(".git")) {
                path = path.substring(0, path.length() - 4);
            }
            String[] parts = path.split("/");
            if (parts.length != 3 || !OWNER_OR_REPOSITORY.matcher(parts[1]).matches()
                    || !OWNER_OR_REPOSITORY.matcher(parts[2]).matches()) {
                throw error("REPOSITORY_URL_INVALID", HttpStatus.BAD_REQUEST, "仓库地址格式不正确");
            }
            return "https://github.com/" + parts[1] + "/" + parts[2];
        } catch (IllegalArgumentException exception) {
            throw error("REPOSITORY_URL_INVALID", HttpStatus.BAD_REQUEST, "仓库地址格式不正确");
        }
    }

    private String chooseBranch(String requestedBranch, String discoveredBranch) {
        String branch = requestedBranch == null || requestedBranch.isBlank() ? discoveredBranch : requestedBranch.trim();
        if (branch == null || branch.isBlank() || branch.length() > 255 || branch.contains("\n") || branch.contains("\r")) {
            return null;
        }
        return branch;
    }

    private void rejectPermanent(GitHubRepositoryChecker.CheckResult check) {
        if (check.outcome() == GitHubRepositoryChecker.Outcome.PERMANENTLY_UNAVAILABLE) {
            throw error("REPOSITORY_NOT_PUBLIC", HttpStatus.BAD_REQUEST, "仓库不存在、不是公开仓库或无法匿名访问");
        }
    }

    private String status(GitHubRepositoryChecker.CheckResult check) {
        return check.outcome() == GitHubRepositoryChecker.Outcome.HEALTHY ? ConnectionStatus.HEALTHY.name()
                : ConnectionStatus.UNAVAILABLE.name();
    }

    private RepositoryDao.RepositoryRow requireRepository(String repositoryId) {
        return repositoryDao.findById(repositoryId)
                .orElseThrow(() -> error("REPOSITORY_NOT_FOUND", HttpStatus.NOT_FOUND, "仓库不存在或不可见"));
    }

    private ProjectDao.ProjectRow requireProject(String projectId) {
        return projectDao.findById(projectId)
                .orElseThrow(() -> error("PROJECT_NOT_FOUND", HttpStatus.NOT_FOUND, "项目不存在或不可见"));
    }

    private void require(String actorId, ProjectDao.ProjectRow project, String action) {
        authorizationService.requireAuthorization(new AuthorizationRequest(new AuthenticatedSubject(actorId, null, Instant.now()),
                REPOSITORY_RESOURCE, project.id(), action, new AuthorizationScope(AuthorizationScope.ScopeType.PROJECT,
                project.tenantId(), project.id(), null, null), Map.of()));
    }

    private RepositoryView view(RepositoryDao.RepositoryRow row) {
        return new RepositoryView(row.id(), row.tenantId(), row.projectId(), row.canonicalUrl(), row.defaultBranch(),
                row.currentVersionNo(), row.connectionStatus(), row.lastCheckedAt(), row.lastErrorCode(), row.createdAt(), row.updatedAt());
    }

    private ProjectManagementException error(String code, HttpStatus status, String message) {
        return new ProjectManagementException(code, status, message);
    }

    public record RepositoryView(String id, String tenantId, String projectId, String canonicalUrl, String defaultBranch,
                                 int version, String connectionStatus, Instant lastCheckedAt, String lastErrorCode,
                                 Instant createdAt, Instant updatedAt) {
    }
}
