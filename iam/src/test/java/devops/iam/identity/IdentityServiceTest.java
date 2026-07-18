package devops.iam.identity;

import devops.iam.api.IamException;
import devops.iam.dao.IdentityDao;
import devops.iam.event.IamEventPublisher;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 覆盖注册与密码策略的关键用户故事，不依赖真实数据库。 */
@ExtendWith(MockitoExtension.class)
class IdentityServiceTest {
    @Mock
    private IdentityDao dao;
    @Mock
    private IamEventPublisher eventPublisher;

    @Test
    void shouldRejectPasswordWithoutLetter() {
        IdentityService service = new IdentityService(dao, eventPublisher);

        IamException exception = assertThrows(IamException.class,
                () -> service.register("user", "user@example.com", "12345678"));

        assertEquals("PASSWORD_POLICY_VIOLATION", exception.code());
        verify(dao, never()).createUser(ArgumentMatchers.anyString(), ArgumentMatchers.anyString(),
                ArgumentMatchers.anyString(), ArgumentMatchers.anyString(), ArgumentMatchers.any());
    }

    @Test
    void shouldRevokeEverySessionAndBroadcastAfterPasswordChange() {
        IdentityService service = new IdentityService(dao, eventPublisher);
        String encodedPassword = new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder()
                .encode("OldPassword1");
        org.mockito.Mockito.when(dao.findUserById("user-1"))
                .thenReturn(Optional.of(new IdentityDao.UserRow("user-1", "user", "user@example.com", encodedPassword)));

        service.changePassword("user-1", "OldPassword1", "NewPassword2");

        verify(dao).revokeAll(ArgumentMatchers.eq("user-1"), ArgumentMatchers.any());
        verify(eventPublisher).publishAfterCommit(ArgumentMatchers.any());
    }

    @Test
    void shouldCreateOpaqueSessionAndClearFailuresAfterSuccessfulLogin() {
        IdentityService service = new IdentityService(dao, eventPublisher);
        String encodedPassword = new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder()
                .encode("Password123");
        when(dao.findUser("user")).thenReturn(Optional.of(new IdentityDao.UserRow("user-1", "user",
                "user@example.com", encodedPassword)));
        when(dao.findLock("user-1")).thenReturn(new IdentityDao.LockRow(2, null));

        IdentityService.LoginView login = service.login("user", "Password123", "test-client");

        org.mockito.ArgumentCaptor<String> tokenHash = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(dao).createSession(ArgumentMatchers.anyString(), ArgumentMatchers.eq("user-1"), tokenHash.capture(),
                ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.eq("test-client"));
        assertEquals(43, login.token().length());
        org.junit.jupiter.api.Assertions.assertNotEquals(login.token(), tokenHash.getValue());
        verify(dao).clearFailures("user-1");
    }

    @Test
    void shouldLockAccountAfterFifthConsecutiveFailure() {
        IdentityService service = new IdentityService(dao, eventPublisher);
        String encodedPassword = new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder()
                .encode("Password123");
        when(dao.findUser("user")).thenReturn(Optional.of(new IdentityDao.UserRow("user-1", "user",
                "user@example.com", encodedPassword)));
        when(dao.findLock("user-1")).thenReturn(new IdentityDao.LockRow(4, null));

        IamException exception = assertThrows(IamException.class, () -> service.login("user", "wrong", null));

        assertEquals("LOGIN_FAILED", exception.code());
        verify(dao).recordFailure(ArgumentMatchers.eq("user-1"), ArgumentMatchers.eq(5), ArgumentMatchers.any(),
                ArgumentMatchers.any());
    }
}
