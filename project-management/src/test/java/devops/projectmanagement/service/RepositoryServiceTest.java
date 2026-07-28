package devops.projectmanagement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import devops.iam.contract.AuthorizationService;
import devops.projectmanagement.api.ProjectManagementException;
import devops.projectmanagement.dao.ProjectDao;
import devops.projectmanagement.dao.RepositoryDao;
import devops.projectmanagement.repository.GitHubRepositoryChecker;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RepositoryServiceTest {
    @Test
    void normalizesPublicGitHubUrlAndUsesRemoteDefaultBranch() {
        RepositoryDao dao = mock(RepositoryDao.class);
        ProjectDao projectDao = mock(ProjectDao.class);
        GitHubRepositoryChecker checker = mock(GitHubRepositoryChecker.class);
        when(projectDao.findById("project")).thenReturn(Optional.of(project("project")));
        when(checker.check("https://github.com/acme/demo")).thenReturn(GitHubRepositoryChecker.CheckResult.healthy("main"));
        when(dao.findById(any())).thenAnswer(invocation -> Optional.of(repository(invocation.getArgument(0), 1, "HEALTHY")));
        RepositoryService service = service(dao, projectDao, checker);

        RepositoryService.RepositoryView view = service.create("actor", "project", "https://github.com/acme/demo.git/", null);

        assertThat(view.defaultBranch()).isEqualTo("main");
        verify(dao).create(any(), eq("tenant"), eq("project"), eq("https://github.com/acme/demo"), eq("main"),
                eq("HEALTHY"), any(), eq(null), eq("actor"), any());
    }

    @Test
    void rejectsNonGithubOrSshUrlWithoutCallingRemote() {
        GitHubRepositoryChecker checker = mock(GitHubRepositoryChecker.class);
        ProjectDao projectDao = mock(ProjectDao.class);
        when(projectDao.findById("project")).thenReturn(Optional.of(project("project")));
        RepositoryService service = service(mock(RepositoryDao.class), projectDao, checker);

        assertThatThrownBy(() -> service.create("actor", "project", "git@github.com:acme/demo.git", null))
                .isInstanceOf(ProjectManagementException.class)
                .extracting(exception -> ((ProjectManagementException) exception).code())
                .isEqualTo("REPOSITORY_URL_INVALID");
    }

    @Test
    void removesExistingRepositoryWhenValidationFindsItNoLongerPublic() {
        RepositoryDao dao = mock(RepositoryDao.class);
        ProjectDao projectDao = mock(ProjectDao.class);
        GitHubRepositoryChecker checker = mock(GitHubRepositoryChecker.class);
        when(dao.findById("repository")).thenReturn(Optional.of(repository("repository", 1, "HEALTHY")));
        when(projectDao.findById("project")).thenReturn(Optional.of(project("project")));
        when(checker.check("https://github.com/acme/demo")).thenReturn(
                GitHubRepositoryChecker.CheckResult.permanentlyUnavailable("REPOSITORY_NOT_PUBLIC"));
        RepositoryService service = service(dao, projectDao, checker);

        assertThatThrownBy(() -> service.validate("actor", "repository"))
                .isInstanceOf(ProjectManagementException.class)
                .extracting(exception -> ((ProjectManagementException) exception).code())
                .isEqualTo("REPOSITORY_REMOVED");
        verify(dao).delete("repository");
    }

    @Test
    void transientValidationFailureKeepsConfigurationAndMarksUnavailable() {
        RepositoryDao dao = mock(RepositoryDao.class);
        ProjectDao projectDao = mock(ProjectDao.class);
        GitHubRepositoryChecker checker = mock(GitHubRepositoryChecker.class);
        when(dao.findById("repository")).thenReturn(Optional.of(repository("repository", 1, "HEALTHY")),
                Optional.of(repository("repository", 1, "UNAVAILABLE")));
        when(projectDao.findById("project")).thenReturn(Optional.of(project("project")));
        when(checker.check("https://github.com/acme/demo")).thenReturn(
                GitHubRepositoryChecker.CheckResult.temporarilyUnavailable("GITHUB_UNAVAILABLE"));
        RepositoryService service = service(dao, projectDao, checker);

        RepositoryService.RepositoryView view = service.validate("actor", "repository");

        assertThat(view.connectionStatus()).isEqualTo("UNAVAILABLE");
        verify(dao).updateHealth(eq("repository"), eq("UNAVAILABLE"), any(), eq("GITHUB_UNAVAILABLE"), any());
    }

    @Test
    void preventsTwentyFirstRepository() {
        RepositoryDao dao = mock(RepositoryDao.class);
        ProjectDao projectDao = mock(ProjectDao.class);
        when(projectDao.findById("project")).thenReturn(Optional.of(project("project")));
        when(dao.countByProject("project")).thenReturn(20);
        RepositoryService service = service(dao, projectDao, mock(GitHubRepositoryChecker.class));

        assertThatThrownBy(() -> service.create("actor", "project", "https://github.com/acme/demo", null))
                .isInstanceOf(ProjectManagementException.class)
                .extracting(exception -> ((ProjectManagementException) exception).code())
                .isEqualTo("REPOSITORY_LIMIT_EXCEEDED");
    }

    private RepositoryService service(RepositoryDao dao, ProjectDao projectDao, GitHubRepositoryChecker checker) {
        AuthorizationService authorizationService = mock(AuthorizationService.class);
        doNothing().when(authorizationService).requireAuthorization(any());
        return new RepositoryService(dao, projectDao, authorizationService, checker);
    }

    private ProjectDao.ProjectRow project(String id) {
        Instant now = Instant.now();
        return new ProjectDao.ProjectRow(id, "tenant", "project", null, 1, "creator", now, now);
    }

    private RepositoryDao.RepositoryRow repository(String id, int version, String status) {
        Instant now = Instant.now();
        return new RepositoryDao.RepositoryRow(id, "tenant", "project", "https://github.com/acme/demo", "main", version,
                status, now, null, "creator", now, now);
    }
}
