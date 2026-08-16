package com.autohr.modules.auth.service.impl;

import cn.hutool.core.util.StrUtil;
import com.autohr.common.api.PageQuery;
import com.autohr.common.api.PageResponse;
import com.autohr.common.exception.BusinessException;
import com.autohr.modules.auth.dto.CandidateProfileUpdateRequest;
import com.autohr.modules.auth.dto.CandidateRegisterRequest;
import com.autohr.modules.auth.dto.LoginRequest;
import com.autohr.modules.auth.dto.LoginResponse;
import com.autohr.modules.auth.dto.PasswordChangeRequest;
import com.autohr.modules.auth.dto.PasswordResetRequest;
import com.autohr.modules.auth.dto.SessionUserVO;
import com.autohr.modules.auth.dto.UserAdminUpdateRequest;
import com.autohr.modules.auth.entity.SysUser;
import com.autohr.modules.auth.mapper.SysUserMapper;
import com.autohr.modules.auth.service.AuthService;
import com.autohr.modules.auth.service.CaptchaService;
import com.autohr.modules.auth.service.JwtService;
import com.autohr.modules.auth.service.PasswordPolicy;
import com.autohr.modules.auth.service.VerificationCodeService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private static final Set<String> DEFAULT_USERNAMES = Set.of("itadmin", "hradmin", "hruser");
    private static final Set<String> ALLOWED_ROLE_CODES = Set.of("IT_ADMIN", "HR_ADMIN", "HR_USER", "INTERVIEWEE");
    private static final List<String> USER_REFERENCE_QUERIES = List.of(
            "SELECT COUNT(*) FROM recruitment_candidate WHERE interviewee_user_id = ?",
            "SELECT COUNT(*) FROM interview_process WHERE interviewee_user_id = ? OR approved_hr_user_id = ?",
            "SELECT COUNT(*) FROM interview_video_session WHERE approver_user_id = ?",
            "SELECT COUNT(*) FROM interview_process_stage WHERE approved_hr_user_id = ?"
    );

    private final SysUserMapper sysUserMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final VerificationCodeService verificationCodeService;
    private final CaptchaService captchaService;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public LoginResponse login(LoginRequest request) {
        captchaService.verifyCaptcha(request.getCaptchaId(), request.getCaptchaCode());
        SysUser user = findUserByUsername(request.getUsername());
        if (user == null) {
            throw new BusinessException("用户名或密码错误");
        }
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BusinessException("用户名或密码错误");
        }
        if (!Objects.equals(user.getStatus(), 1)) {
            throw new BusinessException("用户名或密码错误");
        }
        LoginResponse response = new LoginResponse();
        response.setToken(jwtService.generateToken(user));
        response.setUser(toSessionUser(user));
        return response;
    }

    @Override
    @Transactional
    public SessionUserVO registerCandidate(CandidateRegisterRequest request) {
        captchaService.verifyCaptcha(request.getCaptchaId(), request.getCaptchaCode());
        PasswordPolicy.requireStrongPassword(request.getPassword());
        boolean hasPhone = StrUtil.isNotBlank(request.getMobilePhone());
        boolean hasEmail = StrUtil.isNotBlank(request.getEmail());
        if (hasPhone == hasEmail) {
            throw new BusinessException("手机号和邮箱必须择一提供");
        }
        verificationCodeService.verifyRegisterCode(request.getMobilePhone(), request.getEmail(), request.getVerificationCode());
        ensureUniqueUsername(request.getUsername());
        SysUser user = new SysUser();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRoleCode("INTERVIEWEE");
        user.setDisplayName(request.getDisplayName());
        user.setMobilePhone(request.getMobilePhone());
        user.setEmail(request.getEmail());
        updateNormalizedContacts(user);
        ensureUniqueContacts(user.getMobilePhoneNormalized(), user.getEmailNormalized(), null);
        user.setStatus(1);
        user.setProfileCompleted(0);
        user.setTokenVersion(0);
        user.setMustChangePassword(0);
        try {
            sysUserMapper.insert(user);
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessException("注册请求冲突，请稍后重试");
        }
        return toSessionUser(user);
    }

    @Override
    public SessionUserVO getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || StrUtil.equals(authentication.getName(), "anonymousUser")) {
            throw new BusinessException("未登录");
        }
        return loadUserByUsername(authentication.getName());
    }

    @Override
    @Transactional
    public SessionUserVO updateCandidateProfile(Long userId, CandidateProfileUpdateRequest request) {
        SysUser user = requireUser(userId);
        user.setDisplayName(StrUtil.blankToDefault(request.getDisplayName(), user.getDisplayName()));
        user.setMobilePhone(StrUtil.blankToDefault(request.getMobilePhone(), user.getMobilePhone()));
        user.setEmail(request.getEmail());
        if (StrUtil.isBlank(user.getMobilePhone()) && StrUtil.isBlank(user.getEmail())) {
            throw new BusinessException("手机号和邮箱至少需要保留一种");
        }
        updateNormalizedContacts(user);
        ensureUniqueContacts(user.getMobilePhoneNormalized(), user.getEmailNormalized(), user.getId());
        user.setProfileCompleted(1);
        updateUserContacts(user);
        return toSessionUser(user);
    }

    @Override
    public SessionUserVO loadUserByUsername(String username) {
        return toSessionUser(requireUserByUsername(username));
    }

    @Override
    public PageResponse<SessionUserVO> listUsers(String roleCode, Integer status, String keyword,
                                                 String operatorRoleCode, PageQuery pageQuery) {
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<SysUser>()
                .eq(status != null, SysUser::getStatus, status)
                .and(StrUtil.isNotBlank(keyword), q -> q.like(SysUser::getUsername, keyword)
                        .or().like(SysUser::getDisplayName, keyword)
                        .or().like(SysUser::getMobilePhone, keyword))
                .orderByAsc(SysUser::getId);
        if (StrUtil.equals(operatorRoleCode, "HR_ADMIN")) {
            wrapper.in(SysUser::getRoleCode, "HR_USER", "INTERVIEWEE");
        } else {
            wrapper.eq(StrUtil.isNotBlank(roleCode), SysUser::getRoleCode, roleCode);
        }
        Page<SysUser> result = sysUserMapper.selectPage(new Page<>(pageQuery.page(), pageQuery.pageSize()), wrapper);
        return PageResponse.of(result.getRecords().stream().map(this::toSessionUser).toList(), result.getTotal(), pageQuery);
    }

    @Override
    @Transactional
    public SessionUserVO updateUserByAdmin(Long id, UserAdminUpdateRequest request, String operatorRoleCode) {
        SysUser user = requireUser(id);
        if (StrUtil.equals(operatorRoleCode, "HR_ADMIN") && !(StrUtil.equals(user.getRoleCode(), "HR_USER") || StrUtil.equals(user.getRoleCode(), "INTERVIEWEE"))) {
            throw new BusinessException("HR管理员仅可维护HR用户和面试者用户");
        }
        if (StrUtil.isNotBlank(request.getRoleCode())) {
            if (!ALLOWED_ROLE_CODES.contains(request.getRoleCode())) {
                throw new BusinessException("不支持的用户角色");
            }
            if (StrUtil.equals(operatorRoleCode, "HR_ADMIN") && !(StrUtil.equals(request.getRoleCode(), "HR_USER") || StrUtil.equals(request.getRoleCode(), "INTERVIEWEE"))) {
                throw new BusinessException("HR管理员仅可授予HR用户或面试者角色");
            }
            user.setRoleCode(request.getRoleCode());
        }
        if (request.getStatus() != null) {
            if (!Set.of(0, 1).contains(request.getStatus())) {
                throw new BusinessException("用户状态仅支持启用或停用");
            }
            user.setStatus(request.getStatus());
        }
        if (request.getDisplayName() != null) {
            user.setDisplayName(request.getDisplayName());
        }
        if (request.getMobilePhone() != null) {
            user.setMobilePhone(request.getMobilePhone());
        }
        if (request.getEmail() != null) {
            user.setEmail(request.getEmail());
        }
        if (StrUtil.isNotBlank(request.getNewPassword())) {
            PasswordPolicy.requireStrongPassword(request.getNewPassword());
            user.setPassword(passwordEncoder.encode(request.getNewPassword()));
            user.setTokenVersion((user.getTokenVersion() == null ? 0 : user.getTokenVersion()) + 1);
            user.setMustChangePassword(1);
        }
        updateNormalizedContacts(user);
        ensureUniqueContacts(user.getMobilePhoneNormalized(), user.getEmailNormalized(), user.getId());
        updateUserContacts(user);
        return toSessionUser(user);
    }

    @Override
    @Transactional
    public void deleteUserByAdmin(Long id, Long operatorId, String operatorRoleCode) {
        SysUser user = requireUser(id);
        assertAdminCanManageUser(user, operatorRoleCode);
        if (Objects.equals(user.getId(), operatorId)) {
            throw new BusinessException("不能删除当前登录用户");
        }
        if (DEFAULT_USERNAMES.contains(user.getUsername())) {
            throw new BusinessException("默认账号不能删除");
        }
        if (hasLinkedBusinessData(user.getId())) {
            throw new BusinessException("该用户已有候选人、面试或审批记录，不能删除");
        }
        sysUserMapper.deleteById(user.getId());
    }

    @Override
    public boolean canResetPassword(String mobilePhone, String email) {
        return findActiveUserByContact(mobilePhone, email) != null;
    }

    @Override
    @Transactional
    public void resetPassword(PasswordResetRequest request) {
        SysUser user = findActiveUserByContact(request.getMobilePhone(), request.getEmail());
        if (user == null) {
            throw new BusinessException("验证码无效或已过期");
        }
        PasswordPolicy.requireStrongPassword(request.getNewPassword());
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        user.setMustChangePassword(0);
        user.setTokenVersion((user.getTokenVersion() == null ? 0 : user.getTokenVersion()) + 1);
        sysUserMapper.updateById(user);
    }

    @Override
    @Transactional
    public LoginResponse changePassword(String username, PasswordChangeRequest request) {
        SysUser user = requireUserByUsername(username);
        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new BusinessException("当前密码错误");
        }
        if (passwordEncoder.matches(request.getNewPassword(), user.getPassword())) {
            throw new BusinessException("新密码不能与当前密码相同");
        }
        PasswordPolicy.requireStrongPassword(request.getNewPassword());
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        user.setMustChangePassword(0);
        user.setTokenVersion((user.getTokenVersion() == null ? 0 : user.getTokenVersion()) + 1);
        sysUserMapper.updateById(user);
        LoginResponse response = new LoginResponse();
        response.setToken(jwtService.generateToken(user));
        response.setUser(toSessionUser(user));
        return response;
    }

    @Override
    @Transactional
    public void logout(String username) {
        SysUser user = requireUserByUsername(username);
        user.setTokenVersion((user.getTokenVersion() == null ? 0 : user.getTokenVersion()) + 1);
        sysUserMapper.updateById(user);
    }

    private void ensureUniqueUsername(String username) {
        Long count = sysUserMapper.selectCount(new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, username));
        if (count > 0) {
            throw new BusinessException("用户名已存在");
        }
    }

    private SysUser requireUserByUsername(String username) {
        SysUser user = findUserByUsername(username);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        return user;
    }

    private SysUser findUserByUsername(String username) {
        return sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, username)
                .last("LIMIT 1"));
    }

    private SysUser findActiveUserByContact(String mobilePhone, String email) {
        boolean hasPhone = StrUtil.isNotBlank(mobilePhone);
        boolean hasEmail = StrUtil.isNotBlank(email);
        if (hasPhone == hasEmail) {
            throw new BusinessException("手机号和邮箱必须择一提供");
        }
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getStatus, 1)
                .eq(hasPhone, SysUser::getMobilePhoneNormalized, normalizeMobilePhone(mobilePhone))
                .eq(hasEmail, SysUser::getEmailNormalized, normalizeEmail(email))
                .last("LIMIT 1");
        return sysUserMapper.selectOne(wrapper);
    }

    private void updateNormalizedContacts(SysUser user) {
        user.setMobilePhoneNormalized(normalizeMobilePhone(user.getMobilePhone()));
        user.setEmailNormalized(normalizeEmail(user.getEmail()));
    }

    private String normalizeMobilePhone(String value) {
        return StrUtil.isBlank(value) ? null : value.trim();
    }

    private String normalizeEmail(String value) {
        return StrUtil.isBlank(value) ? null : value.trim().toLowerCase(Locale.ROOT);
    }

    private void ensureUniqueContacts(String normalizedMobilePhone, String normalizedEmail, Long currentUserId) {
        if (normalizedMobilePhone != null && hasContactConflict(SysUser::getMobilePhoneNormalized, normalizedMobilePhone, currentUserId)) {
            throw new BusinessException("手机号已被其他账号使用");
        }
        if (normalizedEmail != null && hasContactConflict(SysUser::getEmailNormalized, normalizedEmail, currentUserId)) {
            throw new BusinessException("邮箱已被其他账号使用");
        }
    }

    private boolean hasContactConflict(com.baomidou.mybatisplus.core.toolkit.support.SFunction<SysUser, String> field,
                                       String value, Long currentUserId) {
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<SysUser>().eq(field, value);
        if (currentUserId != null) {
            wrapper.ne(SysUser::getId, currentUserId);
        }
        return sysUserMapper.selectCount(wrapper) > 0;
    }

    private void updateUserContacts(SysUser user) {
        try {
            sysUserMapper.updateById(user);
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessException("手机号或邮箱已被其他账号使用，请刷新后重试");
        }
    }

    private void assertAdminCanManageUser(SysUser user, String operatorRoleCode) {
        if (StrUtil.equals(operatorRoleCode, "HR_ADMIN")
                && !(StrUtil.equals(user.getRoleCode(), "HR_USER") || StrUtil.equals(user.getRoleCode(), "INTERVIEWEE"))) {
            throw new BusinessException("HR管理员仅可维护HR用户和面试者用户");
        }
    }

    private boolean hasLinkedBusinessData(Long userId) {
        for (String query : USER_REFERENCE_QUERIES) {
            Integer count = query.contains(" OR ")
                    ? jdbcTemplate.queryForObject(query, Integer.class, userId, userId)
                    : jdbcTemplate.queryForObject(query, Integer.class, userId);
            if (count != null && count > 0) {
                return true;
            }
        }
        return false;
    }

    private SysUser requireUser(Long id) {
        SysUser user = sysUserMapper.selectById(id);
        if (user == null) {
            throw new BusinessException("用户不存在: " + id);
        }
        return user;
    }

    private SessionUserVO toSessionUser(SysUser user) {
        SessionUserVO vo = new SessionUserVO();
        BeanUtils.copyProperties(user, vo);
        return vo;
    }

}
