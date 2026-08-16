package com.autohr.common.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SensitiveDataMaskerTest {

    @Test
    void retainsOnlyTheConfiguredIdentityEdges() {
        assertEquals("123***********5678", SensitiveDataMasker.maskIdentityOrAccount("123456789012345678"));
        assertEquals("****", SensitiveDataMasker.maskIdentityOrAccount("1234"));
    }
}
