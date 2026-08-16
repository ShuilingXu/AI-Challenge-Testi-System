package com.autohr.common.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

@Component
public class SecretValueCipher {

    private static final String PREFIX = "enc:v1:";
    private static final int IV_LENGTH = 12;
    private static final int GCM_TAG_BITS = 128;

    private final SecretKeySpec key;
    private final SecureRandom secureRandom = new SecureRandom();

    public SecretValueCipher(@Value("${security.config-encryption-key}") String keyMaterial) {
        if (keyMaterial == null || keyMaterial.isBlank()) {
            throw new IllegalStateException("CONFIG_ENCRYPTION_KEY or JWT_SECRET must be configured");
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(keyMaterial.getBytes(StandardCharsets.UTF_8));
            this.key = new SecretKeySpec(digest, "AES");
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to initialize configuration encryption", ex);
        }
    }

    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isBlank() || isEncrypted(plaintext)) {
            return plaintext;
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] payload = ByteBuffer.allocate(iv.length + encrypted.length)
                    .put(iv)
                    .put(encrypted)
                    .array();
            return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(payload);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to encrypt configuration secret", ex);
        }
    }

    public String decrypt(String storedValue) {
        if (storedValue == null || storedValue.isBlank() || !isEncrypted(storedValue)) {
            return storedValue;
        }
        try {
            byte[] payload = Base64.getUrlDecoder().decode(storedValue.substring(PREFIX.length()));
            if (payload.length <= IV_LENGTH) {
                throw new IllegalArgumentException("Encrypted value is truncated");
            }
            byte[] iv = new byte[IV_LENGTH];
            byte[] encrypted = new byte[payload.length - IV_LENGTH];
            System.arraycopy(payload, 0, iv, 0, iv.length);
            System.arraycopy(payload, iv.length, encrypted, 0, encrypted.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to decrypt configuration secret; check CONFIG_ENCRYPTION_KEY", ex);
        }
    }

    public boolean isEncrypted(String value) {
        return value != null && value.startsWith(PREFIX);
    }
}
