package com.autohr.modules.auth.controller;

import com.autohr.common.api.ApiResponse;
import com.autohr.common.api.PageQuery;
import com.autohr.common.api.PageResponse;
import com.autohr.modules.auth.dto.CandidateProfileUpdateRequest;
import com.autohr.modules.auth.dto.CandidateRegisterRequest;
import com.autohr.modules.auth.dto.CaptchaVO;
import com.autohr.modules.auth.dto.AuditLogVO;
import com.autohr.modules.auth.dto.LoginRequest;
import com.autohr.modules.auth.dto.LoginResponse;
import com.autohr.modules.auth.dto.PasswordChangeRequest;
import com.autohr.modules.auth.dto.PasswordResetRequest;
import com.autohr.modules.auth.dto.SessionUserVO;
import com.autohr.modules.auth.dto.UserAdminUpdateRequest;
import com.autohr.modules.auth.dto.VerificationCodeRequest;
import com.autohr.modules.auth.service.AuthService;
import com.autohr.modules.auth.service.AuthRateLimitService;
import com.autohr.modules.auth.service.AuditLogService;
import com.autohr.modules.auth.service.CaptchaService;
import com.autohr.modules.auth.service.VerificationCodeService;
import com.autohr.modules.auth.config.AuthCookieService;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final AuditLogService auditLogService;
    private final VerificationCodeService verificationCodeService;
    private final CaptchaService captchaService;
    private final AuthRateLimitService authRateLimitService;
    private final AuthCookieService authCookieService;

    @GetMapping("/captcha")
    public ApiResponse<CaptchaVO> captcha(HttpServletRequest request) {
        authRateLimitService.checkCaptchaIssue(request);
        return ApiResponse.success(captchaService.createCaptcha());
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(HttpServletRequest httpRequest, HttpServletResponse response,
                                            @Valid @RequestBody LoginRequest request) {
        authRateLimitService.checkLogin(httpRequest, request.getUsername());
        LoginResponse login = authService.login(request);
        authCookieService.write(response, login.getToken());
        login.setToken(null);
        return ApiResponse.success(login);
    }

    @PostMapping("/register")
    public ApiResponse<SessionUserVO> register(@Valid @RequestBody CandidateRegisterRequest request) {
        return ApiResponse.success(authService.registerCandidate(request));
    }

    @PostMapping("/register/code")
    public ApiResponse<Void> sendRegisterCode(HttpServletRequest httpRequest, @RequestBody VerificationCodeRequest request) {
        authRateLimitService.checkVerificationSend(httpRequest, "register", request.getMobilePhone(), request.getEmail());
        verificationCodeService.sendRegisterCode(request.getMobilePhone(), request.getEmail(), request.getCaptchaId(), request.getCaptchaCode());
        return ApiResponse.success("验证码已发送", null);
    }

    @PostMapping("/password-reset/code")
    public ApiResponse<Void> sendPasswordResetCode(HttpServletRequest httpRequest, @RequestBody VerificationCodeRequest request) {
        authRateLimitService.checkVerificationSend(httpRequest, "password-reset", request.getMobilePhone(), request.getEmail());
        boolean deliver = authService.canResetPassword(request.getMobilePhone(), request.getEmail());
        verificationCodeService.sendPasswordResetCode(request.getMobilePhone(), request.getEmail(),
                request.getCaptchaId(), request.getCaptchaCode(), deliver);
        return ApiResponse.success("如该联系方式已绑定账号，验证码将很快发送", null);
    }

    @PostMapping("/password-reset")
    public ApiResponse<Void> resetPassword(@Valid @RequestBody PasswordResetRequest request) {
        verificationCodeService.verifyPasswordResetCode(request.getMobilePhone(), request.getEmail(), request.getVerificationCode());
        authService.resetPassword(request);
        return ApiResponse.success("密码已重置，请使用新密码登录", null);
    }

    @GetMapping("/me")
    public ApiResponse<SessionUserVO> me() {
        return ApiResponse.success(authService.getCurrentUser());
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(Authentication authentication, HttpServletResponse response) {
        if (authentication != null) {
            authService.logout(authentication.getName());
        }
        authCookieService.clear(response);
        return ApiResponse.success("logged out", null);
    }

    @PostMapping("/change-password")
    public ApiResponse<LoginResponse> changePassword(Authentication authentication, HttpServletResponse response,
                                                     @Valid @RequestBody PasswordChangeRequest request) {
        LoginResponse login = authService.changePassword(authentication.getName(), request);
        authCookieService.write(response, login.getToken());
        login.setToken(null);
        return ApiResponse.success(login);
    }

    @PostMapping("/profile")
    public ApiResponse<SessionUserVO> updateProfile(Authentication authentication,
                                                    @RequestBody CandidateProfileUpdateRequest request) {
        SessionUserVO current = authService.loadUserByUsername(authentication.getName());
        return ApiResponse.success(authService.updateCandidateProfile(current.getId(), request));
    }

    @GetMapping("/admin/users")
    public ApiResponse<PageResponse<SessionUserVO>> listUsers(Authentication authentication,
                                                      @RequestParam(required = false) String roleCode,
                                                      @RequestParam(required = false) Integer status,
                                                      @RequestParam(required = false) String keyword,
                                                      @RequestParam(required = false) Integer page,
                                                      @RequestParam(required = false) Integer pageSize) {
        SessionUserVO current = authService.loadUserByUsername(authentication.getName());
        return ApiResponse.success(authService.listUsers(roleCode, status, keyword, current.getRoleCode(),
                PageQuery.of(page, pageSize)));
    }

    @PostMapping("/admin/users/{id}")
    public ApiResponse<SessionUserVO> updateUser(Authentication authentication,
                                                  @PathVariable Long id,
                                                  @Valid @RequestBody UserAdminUpdateRequest request) {
        SessionUserVO current = authService.loadUserByUsername(authentication.getName());
        SessionUserVO updated = authService.updateUserByAdmin(id, request, current.getRoleCode());
        String action = request.getNewPassword() == null || request.getNewPassword().isBlank() ? "UPDATE_USER" : "RESET_USER_PASSWORD";
        auditLogService.log(current.getId(), current.getDisplayName(), current.getRoleCode(), "ADMIN", action, "SYS_USER", String.valueOf(updated.getId()), updated.getUsername());
        return ApiResponse.success(updated);
    }

    @DeleteMapping("/admin/users/{id}")
    public ApiResponse<Void> deleteUser(Authentication authentication, @PathVariable Long id) {
        SessionUserVO current = authService.loadUserByUsername(authentication.getName());
        authService.deleteUserByAdmin(id, current.getId(), current.getRoleCode());
        auditLogService.log(current.getId(), current.getDisplayName(), current.getRoleCode(), "ADMIN", "DELETE_USER", "SYS_USER", String.valueOf(id), "deleted user");
        return ApiResponse.success("用户已删除", null);
    }

    @GetMapping("/admin/audit-logs")
    public ApiResponse<PageResponse<AuditLogVO>> listAuditLogs(@RequestParam(required = false) String moduleCode,
                                                       @RequestParam(required = false) String actionCode,
                                                       @RequestParam(required = false) String keyword,
                                                       @RequestParam(required = false) Integer page,
                                                       @RequestParam(required = false) Integer pageSize) {
        return ApiResponse.success(auditLogService.list(moduleCode, actionCode, keyword, PageQuery.of(page, pageSize)));
    }
}
