package com.autohr.common.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecretValueCipherTest {

    @Test
    void encryptsAndDecryptsSecretValuesWhileAcceptingLegacyPlaintext() {
        SecretValueCipher cipher = new SecretValueCipher("test-key-material-at-least-32-characters");

        String encrypted = cipher.encrypt("sk-production-secret");

        assertTrue(encrypted.startsWith("enc:v1:"));
        assertNotEquals("sk-production-secret", encrypted);
        assertEquals("sk-production-secret", cipher.decrypt(encrypted));
        assertEquals("legacy-plaintext", cipher.decrypt("legacy-plaintext"));
    }
}
