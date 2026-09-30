package com.autohr.modules.auth.config;

import com.autohr.modules.auth.entity.SysUser;
import com.autohr.modules.auth.mapper.SysUserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.mock.env.MockEnvironment;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.anyString;
import com.autohr.modules.auth.service.PasswordPolicy;

@ExtendWith(MockitoExtension.class)
class AuthBootstrapRunnerTest {

    @Mock
    private SysUserMapper sysUserMapper;
    @Mock
    private PasswordEncoder passwordEncoder;
    private AuthBootstrapRunner runner;

    @BeforeEach
    void setUp() {
        runner = new AuthBootstrapRunner(sysUserMapper, passwordEncoder);
        ReflectionTestUtils.setField(runner, "defaultPasswordExemptUsernames", "");
    }

    @Test
    void createsBuiltInAccountsWithDocumentedPasswordAndForcesFirstLoginChange() {
        when(sysUserMapper.selectOne(any())).thenReturn(null);
        when(passwordEncoder.encode("123456")).thenReturn("encoded-default-password");

        runner.run();

        ArgumentCaptor<SysUser> users = ArgumentCaptor.forClass(SysUser.class);
        verify(sysUserMapper, times(3)).insert(users.capture());
        List<SysUser> created = users.getAllValues();
        assertEquals(List.of("itadmin", "hradmin", "hruser"),
                created.stream().map(SysUser::getUsername).toList());
        assertTrue(created.stream().allMatch(user -> "encoded-default-password".equals(user.getPassword())));
        assertTrue(created.stream().allMatch(user -> Integer.valueOf(1).equals(user.getMustChangePassword())));
        verify(passwordEncoder, times(3)).encode("123456");
    }

    @Test
    void allowsOnlyConfiguredDefaultAccountToBypassForcedChange() {
        ReflectionTestUtils.setField(runner, "defaultPasswordExemptUsernames", "itadmin");
        SysUser itadmin = existingDefaultUser("itadmin", 1, 7);
        SysUser hradmin = existingDefaultUser("hradmin", 1, 2);
        SysUser hruser = existingDefaultUser("hruser", 1, 3);
        when(sysUserMapper.selectOne(any())).thenReturn(itadmin, hradmin, hruser);
        when(passwordEncoder.matches("123456", "encoded-default-password")).thenReturn(true);

        runner.run();

        ArgumentCaptor<SysUser> updated = ArgumentCaptor.forClass(SysUser.class);
        verify(sysUserMapper).updateById(updated.capture());
        assertEquals("itadmin", updated.getValue().getUsername());
        assertEquals(0, updated.getValue().getMustChangePassword());
        assertEquals(8, updated.getValue().getTokenVersion());
    }

    @Test
    void productionCreatesUniqueStrongPasswordsAndNeverAllowsDefaultExemptions() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        ReflectionTestUtils.setField(runner, "environment", environment);
        ReflectionTestUtils.setField(runner, "defaultPasswordExemptUsernames", "itadmin,hradmin,hruser");
        when(passwordEncoder.encode(anyString())).thenAnswer(call -> "encoded-" + call.getArgument(0));
        runner.run();
        ArgumentCaptor<String> passwords = ArgumentCaptor.forClass(String.class);
        verify(passwordEncoder, times(3)).encode(passwords.capture());
        assertEquals(3, passwords.getAllValues().stream().distinct().count());
        passwords.getAllValues().forEach(PasswordPolicy::requireStrongPassword);
        verify(passwordEncoder, never()).encode("123456");
        ArgumentCaptor<SysUser> users = ArgumentCaptor.forClass(SysUser.class);
        verify(sysUserMapper, times(3)).insert(users.capture());
        assertTrue(users.getAllValues().stream().allMatch(user -> Integer.valueOf(1).equals(user.getMustChangePassword())));
    }

    @Test
    void productionRotatesAnExistingDefaultPasswordAndRevokesItsSessions() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        ReflectionTestUtils.setField(runner, "environment", environment);
        SysUser itadmin = existingDefaultUser("itadmin", 0, 7);
        SysUser hradmin = existingDefaultUser("hradmin", 0, 2);
        SysUser hruser = existingDefaultUser("hruser", 0, 3);
        when(sysUserMapper.selectOne(any())).thenReturn(itadmin, hradmin, hruser);
        when(passwordEncoder.matches("123456", "encoded-default-password")).thenReturn(true);
        when(passwordEncoder.encode(anyString())).thenReturn("random-password-hash");
        runner.run();
        verify(sysUserMapper, times(3)).updateById(any(SysUser.class));
        assertEquals("random-password-hash", itadmin.getPassword());
        assertEquals(1, itadmin.getMustChangePassword());
        assertEquals(8, itadmin.getTokenVersion());
    }

    @Test
    void productionPreservesPasswordsThatHaveAlreadyBeenChanged() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        ReflectionTestUtils.setField(runner, "environment", environment);
        SysUser itadmin = existingDefaultUser("itadmin", 0, 7);
        when(sysUserMapper.selectOne(any())).thenReturn(itadmin);
        // matches() is false: the real account no longer uses the default.
        runner.run();
        verify(sysUserMapper, never()).updateById(any(SysUser.class));
        verify(sysUserMapper, never()).insert(any(SysUser.class));
        verify(passwordEncoder, never()).encode(anyString());
        assertEquals(7, itadmin.getTokenVersion());
        assertEquals(0, itadmin.getMustChangePassword());
    }

    private SysUser existingDefaultUser(String username, int mustChangePassword, int tokenVersion) {
        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPassword("encoded-default-password");
        user.setMustChangePassword(mustChangePassword);
        user.setTokenVersion(tokenVersion);
        return user;
    }
}
