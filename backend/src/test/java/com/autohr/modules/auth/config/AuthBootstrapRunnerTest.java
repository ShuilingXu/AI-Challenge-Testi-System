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

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

    private SysUser existingDefaultUser(String username, int mustChangePassword, int tokenVersion) {
        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPassword("encoded-default-password");
        user.setMustChangePassword(mustChangePassword);
        user.setTokenVersion(tokenVersion);
        return user;
    }
}
