package com.autohr.modules.auth.service.impl;

import cn.hutool.core.util.StrUtil;
import com.autohr.common.exception.BusinessException;
import com.autohr.modules.auth.dto.CaptchaVO;
import com.autohr.modules.auth.service.CaptchaService;
import com.autohr.modules.auth.service.AuthRedisSecurityStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CaptchaServiceImpl implements CaptchaService {

    private static final int EXPIRE_MINUTES = 5;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final AuthRedisSecurityStore securityStore;

    @Override
    public CaptchaVO createCaptcha() {
        String code = randomCode();
        String captchaId = UUID.randomUUID().toString();
        securityStore.storeCaptcha(captchaId, code, java.time.Duration.ofMinutes(EXPIRE_MINUTES));
        return new CaptchaVO(captchaId, createImageBase64(code));
    }

    @Override
    public void verifyCaptcha(String captchaId, String captchaCode) {
        if (StrUtil.isBlank(captchaId) || StrUtil.isBlank(captchaCode)) {
            throw new BusinessException("图形验证码必填");
        }
        AuthRedisSecurityStore.CaptchaResult result = securityStore.consumeCaptcha(captchaId, captchaCode.trim().toUpperCase(Locale.ROOT));
        if (result == AuthRedisSecurityStore.CaptchaResult.MISSING) {
            throw new BusinessException("图形验证码已过期，请刷新后重试");
        }
        if (result == AuthRedisSecurityStore.CaptchaResult.MISMATCHED) {
            throw new BusinessException("图形验证码错误");
        }
    }

    private String createImageBase64(String code) {
        int width = 128;
        int height = 44;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setColor(new Color(246, 241, 232));
        graphics.fillRect(0, 0, width, height);
        for (int i = 0; i < 9; i++) {
            graphics.setColor(randomSoftColor());
            graphics.drawLine(SECURE_RANDOM.nextInt(width), SECURE_RANDOM.nextInt(height), SECURE_RANDOM.nextInt(width), SECURE_RANDOM.nextInt(height));
        }
        graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 26));
        for (int i = 0; i < code.length(); i++) {
            graphics.setColor(randomTextColor());
            int pivotX = 24 + i * 25;
            int pivotY = 26;
            double angle = Math.toRadians(randomInt(-12, 13));
            graphics.rotate(angle, pivotX, pivotY);
            graphics.drawString(String.valueOf(code.charAt(i)), 16 + i * 25, 31);
            graphics.rotate(-angle, pivotX, pivotY);
        }
        graphics.dispose();
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", output);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(output.toByteArray());
        } catch (Exception ex) {
            throw new BusinessException("图形验证码生成失败");
        }
    }

    private Color randomSoftColor() {
        return new Color(randomInt(150, 220), randomInt(150, 220), randomInt(150, 220));
    }

    private Color randomTextColor() {
        return new Color(randomInt(25, 90), randomInt(45, 110), randomInt(60, 130));
    }

    private String randomCode() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < 4; i++) {
            builder.append(chars.charAt(SECURE_RANDOM.nextInt(chars.length())));
        }
        return builder.toString();
    }

    private int randomInt(int origin, int bound) {
        return origin + SECURE_RANDOM.nextInt(bound - origin);
    }
}
