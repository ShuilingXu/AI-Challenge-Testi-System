package com.autohr.modules.auth.service;

import com.autohr.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AuthRateLimitServiceTest {
    AuthRedisSecurityStore store;
    AuthRateLimitService service;
    MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        store = mock(AuthRedisSecurityStore.class);
        service = new AuthRateLimitService(store, false, 60, 10, 10, 30, 5, 3);
        ReflectionTestUtils.setField(service, "studentEntryPerIp", 600);
        ReflectionTestUtils.setField(service, "studentEntryPerStudent", 10);
        request = new MockHttpServletRequest();
        request.setRemoteAddr("192.0.2.1");
        request.addHeader("X-Forwarded-For", "198.51.100.1");
    }

    @Test
    void aSharedClassIpUsesItsOwnBudgetAndEveryStudentHasATighterBudget() {
        for (int i = 0; i < 60; i++) service.checkStudentEntry(request, "  student" + i + "  ");
        verify(store, times(60)).enforceRateLimit(eq("student-entry-ip"), eq("192.0.2.1"), eq(600), eq(60), anyString());
        verify(store).enforceRateLimit(eq("student-entry-student"), eq("student0"), eq(10), eq(60), anyString());
        verify(store, never()).enforceRateLimit(anyString(), eq("198.51.100.1"), anyInt(), anyInt(), anyString());
    }

    @Test
    void aStudentLimitRejectsFurtherEntryAttempts() {
        doThrow(new BusinessException("limited")).when(store).enforceRateLimit(
                eq("student-entry-student"), eq("student0"), anyInt(), anyInt(), anyString());
        assertThrows(BusinessException.class, () -> service.checkStudentEntry(request, "student0"));
    }
}
