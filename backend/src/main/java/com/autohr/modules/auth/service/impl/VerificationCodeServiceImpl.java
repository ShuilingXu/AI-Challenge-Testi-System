package com.autohr.modules.auth.service.impl;

import cn.hutool.core.util.StrUtil;
import com.aliyun.dysmsapi20170525.Client;
import com.aliyun.dysmsapi20170525.models.SendSmsRequest;
import com.aliyun.dysmsapi20170525.models.SendSmsResponse;
import com.aliyun.teaopenapi.models.Config;
import com.autohr.common.exception.BusinessException;
import com.autohr.modules.auth.service.VerificationCodeService;
import com.autohr.modules.auth.service.CaptchaService;
import com.autohr.modules.auth.service.AuthRedisSecurityStore;
import com.autohr.modules.system.service.SystemConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

@Service
public class VerificationCodeServiceImpl implements VerificationCodeService {

    private static final Logger log = LoggerFactory.getLogger(VerificationCodeServiceImpl.class);
    private static final String[] CONFIG_KEYS = {
            "ALIYUN_SMS_ACCESS_KEY_ID", "ALIYUN_SMS_ACCESS_KEY_SECRET", "ALIYUN_SMS_ENDPOINT", "ALIYUN_SMS_SIGN_NAME", "ALIYUN_SMS_TEMPLATE_CODE",
            "SMTP_HOST", "SMTP_PORT", "SMTP_USERNAME", "SMTP_PASSWORD", "SMTP_FROM", "SMTP_SSL_ENABLED", "SMTP_STARTTLS_ENABLED"
    };
    private static final int EXPIRE_MINUTES = 5;
    private static final int RESEND_SECONDS = 60;
    private static final int MAX_VERIFY_ATTEMPTS = 5;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final String REGISTER_PURPOSE = "register";
    private static final String PASSWORD_RESET_PURPOSE = "password-reset";

    private final SystemConfigService systemConfigService;
    private final CaptchaService captchaService;
    private final AuthRedisSecurityStore securityStore;
    @Qualifier("verificationCodeExecutor")
    private final TaskExecutor verificationCodeExecutor;

    public VerificationCodeServiceImpl(SystemConfigService systemConfigService,
                                       CaptchaService captchaService,
                                       AuthRedisSecurityStore securityStore,
                                       @Qualifier("verificationCodeExecutor") TaskExecutor verificationCodeExecutor) {
        this.systemConfigService = systemConfigService;
        this.captchaService = captchaService;
        this.securityStore = securityStore;
        this.verificationCodeExecutor = verificationCodeExecutor;
    }

    @Override
    public void sendRegisterCode(String mobilePhone, String email, String captchaId, String captchaCode) {
        sendCode(REGISTER_PURPOSE, mobilePhone, email, captchaId, captchaCode);
    }

    @Override
    public void verifyRegisterCode(String mobilePhone, String email, String code) {
        verifyCode(REGISTER_PURPOSE, mobilePhone, email, code);
    }

    @Override
    public void sendPasswordResetCode(String mobilePhone, String email, String captchaId, String captchaCode, boolean deliver) {
        String target = normalizeTarget(mobilePhone, email);
        captchaService.verifyCaptcha(captchaId, captchaCode);
        if (!deliver) {
            return;
        }
        try {
            verificationCodeExecutor.execute(() -> sendPasswordResetCodeSafely(target));
        } catch (RuntimeException ex) {
            log.warn("Password-reset verification delivery could not be scheduled");
        }
    }

    @Override
    public void verifyPasswordResetCode(String mobilePhone, String email, String code) {
        verifyCode(PASSWORD_RESET_PURPOSE, mobilePhone, email, code);
    }

    private void sendCode(String purpose, String mobilePhone, String email, String captchaId, String captchaCode) {
        String target = normalizeTarget(mobilePhone, email);
        captchaService.verifyCaptcha(captchaId, captchaCode);
        sendCode(purpose, target);
    }

    private void sendCode(String purpose, String target) {
        String code = String.format(Locale.ROOT, "%06d", SECURE_RANDOM.nextInt(1_000_000));
        boolean stored = securityStore.replaceVerificationCode(purpose, target, code, Instant.now().getEpochSecond(),
                RESEND_SECONDS, EXPIRE_MINUTES * 60);
        if (!stored) {
            throw new BusinessException("验证码发送过于频繁，请稍后再试");
        }
        try {
            if (target.startsWith("sms:")) {
                sendSms(target.substring(4), code);
            } else {
                sendEmail(target.substring(6), code);
            }
        } catch (RuntimeException ex) {
            securityStore.discardVerificationCode(purpose, target, code);
            throw ex;
        }
    }

    private void sendPasswordResetCodeSafely(String target) {
        try {
            sendCode(PASSWORD_RESET_PURPOSE, target);
        } catch (RuntimeException ex) {
            log.warn("Password-reset verification delivery failed");
        }
    }

    private void verifyCode(String purpose, String mobilePhone, String email, String code) {
        String target = normalizeTarget(mobilePhone, email);
        AuthRedisSecurityStore.VerificationResult result = securityStore.consumeVerificationCode(purpose, target, code, MAX_VERIFY_ATTEMPTS);
        if (result == AuthRedisSecurityStore.VerificationResult.MISSING) {
            throw new BusinessException("验证码已过期，请重新获取");
        }
        if (result == AuthRedisSecurityStore.VerificationResult.LOCKED) {
            throw new BusinessException("验证码错误次数过多，请重新获取验证码");
        }
        if (result == AuthRedisSecurityStore.VerificationResult.MISMATCHED) {
            throw new BusinessException("验证码错误");
        }
    }

    private String normalizeTarget(String mobilePhone, String email) {
        boolean hasPhone = StrUtil.isNotBlank(mobilePhone);
        boolean hasEmail = StrUtil.isNotBlank(email);
        if (hasPhone == hasEmail) {
            throw new BusinessException("手机号和邮箱必须择一提供");
        }
        return hasPhone ? "sms:" + mobilePhone.trim() : "email:" + email.trim().toLowerCase();
    }

    private void sendSms(String mobilePhone, String code) {
        Map<String, String> config = systemConfigService.loadConfig(CONFIG_KEYS);
        requireConfig(config, "ALIYUN_SMS_ACCESS_KEY_ID", "阿里云短信AccessKey ID未配置");
        requireConfig(config, "ALIYUN_SMS_ACCESS_KEY_SECRET", "阿里云短信AccessKey Secret未配置");
        requireConfig(config, "ALIYUN_SMS_SIGN_NAME", "短信签名未配置");
        requireConfig(config, "ALIYUN_SMS_TEMPLATE_CODE", "短信模板Code未配置");
        try {
            Config aliyunConfig = new Config()
                    .setAccessKeyId(config.get("ALIYUN_SMS_ACCESS_KEY_ID"))
                    .setAccessKeySecret(config.get("ALIYUN_SMS_ACCESS_KEY_SECRET"));
            aliyunConfig.endpoint = StrUtil.blankToDefault(config.get("ALIYUN_SMS_ENDPOINT"), "dysmsapi.aliyuncs.com");
            Client client = new Client(aliyunConfig);
            SendSmsRequest request = new SendSmsRequest()
                    .setPhoneNumbers(mobilePhone)
                    .setSignName(config.get("ALIYUN_SMS_SIGN_NAME"))
                    .setTemplateCode(config.get("ALIYUN_SMS_TEMPLATE_CODE"))
                    .setTemplateParam("{\"code\":\"" + code + "\"}");
            SendSmsResponse response = client.sendSms(request);
            String responseCode = response == null || response.getBody() == null ? null : response.getBody().getCode();
            if (!StrUtil.equalsIgnoreCase(responseCode, "OK")) {
                String responseMessage = response == null || response.getBody() == null ? "无响应内容" : response.getBody().getMessage();
                String requestId = response == null || response.getBody() == null ? "" : response.getBody().getRequestId();
                throw new BusinessException("短信验证码发送失败: " + StrUtil.blankToDefault(responseMessage, responseCode)
                        + (StrUtil.isBlank(requestId) ? "" : " (RequestId: " + requestId + ")"));
            }
        } catch (BusinessException ex) {
            throw ex;
        } catch (Throwable ex) {
            throw new BusinessException("短信验证码发送失败");
        }
    }

    private void sendEmail(String email, String code) {
        Map<String, String> config = systemConfigService.loadConfig(CONFIG_KEYS);
        requireConfig(config, "SMTP_HOST", "SMTP服务器未配置");
        requireConfig(config, "SMTP_PORT", "SMTP端口未配置");
        requireConfig(config, "SMTP_USERNAME", "SMTP用户名未配置");
        requireConfig(config, "SMTP_PASSWORD", "SMTP密码未配置");
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(config.get("SMTP_HOST"));
        sender.setPort(parseSmtpPort(config.get("SMTP_PORT")));
        sender.setUsername(config.get("SMTP_USERNAME"));
        sender.setPassword(config.get("SMTP_PASSWORD"));
        Properties props = sender.getJavaMailProperties();
        String startTlsEnabled = StrUtil.blankToDefault(config.get("SMTP_STARTTLS_ENABLED"), "true");
        if (!Boolean.parseBoolean(startTlsEnabled)) {
            throw new BusinessException("SMTP必须启用STARTTLS");
        }
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.ssl.enable", StrUtil.blankToDefault(config.get("SMTP_SSL_ENABLED"), "false"));
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.starttls.required", "true");
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(StrUtil.blankToDefault(config.get("SMTP_FROM"), config.get("SMTP_USERNAME")));
        message.setTo(email);
        message.setSubject("人事系统验证码");
        message.setText("您的验证码为：" + code + "，有效期" + EXPIRE_MINUTES + "分钟。");
        try {
            sender.send(message);
        } catch (Exception ex) {
            throw new BusinessException("邮件验证码发送失败");
        }
    }

    private void requireConfig(Map<String, String> config, String key, String message) {
        if (StrUtil.isBlank(config.get(key))) {
            throw new BusinessException(message);
        }
    }

    private int parseSmtpPort(String value) {
        try {
            int port = Integer.parseInt(value);
            if (port < 1 || port > 65535) {
                throw new NumberFormatException("out of range");
            }
            return port;
        } catch (NumberFormatException ex) {
            throw new BusinessException("SMTP端口必须是 1 到 65535 的整数");
        }
    }

}
