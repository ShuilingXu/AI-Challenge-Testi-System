package com.autohr.modules.auth.config;

import com.autohr.modules.auth.entity.SysUser;
import com.autohr.modules.auth.mapper.SysUserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

import java.util.Arrays;
import java.util.Base64;
import java.security.SecureRandom;


@Component
@RequiredArgsConstructor
@Slf4j
public class AuthBootstrapRunner implements CommandLineRunner {

    private final SysUserMapper sysUserMapper;
    private final PasswordEncoder passwordEncoder;
    private static final SecureRandom RANDOM = new SecureRandom();
    @Autowired
    private Environment environment;

    @Value("${auth.bootstrap.default-password-exempt-usernames:}")
    private String defaultPasswordExemptUsernames;

    @Override
    public void run(String... args) {
        ensureUser("itadmin", "123456", "IT_ADMIN", "系统管理员");
        ensureUser("hradmin", "123456", "HR_ADMIN", "教师管理员");
        ensureUser("hruser", "123456", "HR_USER", "教师");
    }

    private void ensureUser(String username, String password, String roleCode, String displayName) {
        boolean exemptFromPasswordChange = isDefaultPasswordExempt(username);
        SysUser existing = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, username)
                .last("LIMIT 1"));
        if (existing != null) {
            if (passwordEncoder.matches(password, existing.getPassword()) && isProduction()) {
                String generated = generateInitialPassword();
                existing.setPassword(passwordEncoder.encode(generated));
                existing.setMustChangePassword(1);
                existing.setTokenVersion((existing.getTokenVersion() == null ? 0 : existing.getTokenVersion()) + 1);
                sysUserMapper.updateById(existing);
                logInitialPassword(username, generated);
            } else if (passwordEncoder.matches(password, existing.getPassword())
                    && !Integer.valueOf(exemptFromPasswordChange ? 0 : 1).equals(existing.getMustChangePassword())) {
                existing.setMustChangePassword(exemptFromPasswordChange ? 0 : 1);
                existing.setTokenVersion((existing.getTokenVersion() == null ? 0 : existing.getTokenVersion()) + 1);
                sysUserMapper.updateById(existing);
            }
            return;
        }
        SysUser user = new SysUser();
        String initialPassword = isProduction() ? generateInitialPassword() : password;
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(initialPassword));
        user.setRoleCode(roleCode);
        user.setDisplayName(displayName);
        user.setStatus(1);
        user.setProfileCompleted(1);
        user.setTokenVersion(0);
        user.setMustChangePassword(!isProduction() && exemptFromPasswordChange ? 0 : 1);
        sysUserMapper.insert(user);
        if (isProduction()) logInitialPassword(username, initialPassword);
    }

    private boolean isProduction() {
        return environment != null && environment.acceptsProfiles(Profiles.of("prod"));
    }

    private String generateInitialPassword() {
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        return "Aa1!" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private void logInitialPassword(String username, String password) {
        // Emitted once, only when creating or rotating an insecure built-in
        // account. Production service logs must be accessible only to operators.
        log.warn("Bootstrap account {} initial password: {} (change it immediately after login)", username, password);
    }

    private boolean isDefaultPasswordExempt(String username) {
        return Arrays.stream(defaultPasswordExemptUsernames.split(","))
                .map(String::trim)
                .anyMatch(username::equalsIgnoreCase);
    }
}
