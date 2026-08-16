package com.autohr.modules.auth.service.impl;

import com.autohr.common.exception.BusinessException;
import com.autohr.modules.auth.service.AuthRedisSecurityStore;
import com.autohr.modules.auth.service.CaptchaService;
import com.autohr.modules.system.service.SystemConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VerificationCodeServiceImplTest {

    private SystemConfigService systemConfigService;
    private CaptchaService captchaService;
    private AuthRedisSecurityStore securityStore;

    @BeforeEach
    void setUp() {
        systemConfigService = mock(SystemConfigService.class);
        captchaService = mock(CaptchaService.class);
        securityStore = mock(AuthRedisSecurityStore.class);
    }

    @Test
    void passwordResetAlwaysValidatesCaptchaRegardlessOfAccountExistence() {
        BusinessException captchaFailure = new BusinessException("captcha failed");
        doThrow(captchaFailure).when(captchaService).verifyCaptcha("captcha-id", "wrong-code");
        VerificationCodeServiceImpl service = createService(new SyncTaskExecutor());

        BusinessException existingFailure = assertThrows(BusinessException.class,
                () -> service.sendPasswordResetCode(null, "person@example.com", "captcha-id", "wrong-code", true));
        BusinessException missingFailure = assertThrows(BusinessException.class,
                () -> service.sendPasswordResetCode(null, "person@example.com", "captcha-id", "wrong-code", false));

        assertSame(captchaFailure, existingFailure);
        assertSame(captchaFailure, missingFailure);
        verify(securityStore, never()).replaceVerificationCode(anyString(), anyString(), anyString(),
                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void passwordResetDoesNotQueueDeliveryForUnknownAccount() {
        TaskExecutor executor = mock(TaskExecutor.class);
        VerificationCodeServiceImpl service = createService(executor);

        service.sendPasswordResetCode(null, "person@example.com", "captcha-id", "captcha-code", false);

        verify(captchaService).verifyCaptcha("captcha-id", "captcha-code");
        verify(executor, never()).execute(org.mockito.ArgumentMatchers.any(Runnable.class));
        verify(securityStore, never()).replaceVerificationCode(anyString(), anyString(), anyString(),
                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void passwordResetHidesDeliveryFailureAfterCodeIsStored() {
        when(securityStore.replaceVerificationCode(eq("password-reset"), eq("email:person@example.com"),
                anyString(), org.mockito.ArgumentMatchers.anyLong(), eq(60), eq(300))).thenReturn(true);
        when(systemConfigService.loadConfig(org.mockito.ArgumentMatchers.any(String[].class))).thenReturn(Map.of());
        VerificationCodeServiceImpl service = createService(new SyncTaskExecutor());

        assertDoesNotThrow(() -> service.sendPasswordResetCode(
                null, "Person@Example.com", "captcha-id", "captcha-code", true));

        verify(securityStore).replaceVerificationCode(eq("password-reset"), eq("email:person@example.com"),
                anyString(), org.mockito.ArgumentMatchers.anyLong(), eq(60), eq(300));
        verify(securityStore).discardVerificationCode(eq("password-reset"), eq("email:person@example.com"), anyString());
    }

    @Test
    void passwordResetHidesExecutorRejection() {
        TaskExecutor executor = command -> {
            throw new IllegalStateException("queue full");
        };
        VerificationCodeServiceImpl service = createService(executor);

        assertDoesNotThrow(() -> service.sendPasswordResetCode(
                "13800138000", null, "captcha-id", "captcha-code", true));

        verify(captchaService).verifyCaptcha("captcha-id", "captcha-code");
        verify(securityStore, never()).replaceVerificationCode(anyString(), anyString(), anyString(),
                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void emailDeliveryRejectsConfigurationWithoutStartTls() {
        when(securityStore.replaceVerificationCode(eq("register"), eq("email:person@example.com"),
                anyString(), org.mockito.ArgumentMatchers.anyLong(), eq(60), eq(300))).thenReturn(true);
        when(systemConfigService.loadConfig(org.mockito.ArgumentMatchers.any(String[].class))).thenReturn(Map.of(
                "SMTP_HOST", "smtp.example.com",
                "SMTP_PORT", "587",
                "SMTP_USERNAME", "sender@example.com",
                "SMTP_PASSWORD", "secret",
                "SMTP_STARTTLS_ENABLED", "false"
        ));
        VerificationCodeServiceImpl service = createService(new SyncTaskExecutor());

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.sendRegisterCode(null, "person@example.com", "captcha-id", "captcha-code"));

        assertTrue(error.getMessage().contains("STARTTLS"));
        verify(securityStore).discardVerificationCode(eq("register"), eq("email:person@example.com"), anyString());
    }

    private VerificationCodeServiceImpl createService(TaskExecutor executor) {
        return new VerificationCodeServiceImpl(systemConfigService, captchaService, securityStore, executor);
    }
}
