package com.autohr.modules.school.service;

import com.autohr.common.exception.BusinessException;
import com.autohr.modules.auth.service.AuthRedisSecurityStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SchoolExamRecordingServiceTest {
    @TempDir Path root;
    Path directory;
    JdbcTemplate jdbc;
    SchoolExamService exams;
    AuthRedisSecurityStore redis;
    SchoolExamRecordingService service;
    TransactionTemplate tx;

    @BeforeEach void setUp() {
        var dataSource = new DriverManagerDataSource("jdbc:sqlite:" + root.resolve("recordings.db"));
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE TABLE school_exam_process(id INTEGER PRIMARY KEY)");
        jdbc.execute("CREATE TABLE school_exam_recording(process_id INTEGER,segment_no INTEGER,file_name TEXT,created_at TEXT DEFAULT CURRENT_TIMESTAMP,PRIMARY KEY(process_id,segment_no))");
        jdbc.update("INSERT INTO school_exam_process VALUES(41)");
        tx = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        exams = mock(SchoolExamService.class);
        redis = mock(AuthRedisSecurityStore.class);
        when(exams.studentMonitoringPolicy(41L, 88L)).thenReturn(Map.of("camera_enabled", 1, "screen_recording_enabled", 0));
        directory = root.resolve("files");
        service = new SchoolExamRecordingService(jdbc, exams);
        ReflectionTestUtils.setField(service, "securityStore", redis);
        ReflectionTestUtils.setField(service, "recordingDirectory", directory.toString());
        ReflectionTestUtils.setField(service, "maxTotalBytes", 16L);
        ReflectionTestUtils.setField(service, "maxSegments", 3);
        ReflectionTestUtils.setField(service, "uploadsPerMinute", 20);
        ReflectionTestUtils.setField(service, "minFreeBytes", 0L);
    }
    private MockMultipartFile video(int size) {
        byte[] bytes = new byte[size];
        System.arraycopy(new byte[]{0x1a, 0x45, (byte) 0xdf, (byte) 0xa3}, 0, bytes, 0, 4);
        return new MockMultipartFile("file", "video.webm", "video/webm", bytes);
    }
    private void upload(int segment, MultipartFile file) {
        tx.executeWithoutResult(status -> service.upload(41L, segment, 88L, file));
    }
    private int count() { return jdbc.queryForObject("SELECT COUNT(*) FROM school_exam_recording", Integer.class); }

    @Test void aggregateQuotaIncludesReplacementsAndRejectsBeforeWriting() throws Exception {
        upload(0, video(8)); upload(1, video(8));
        assertThrows(BusinessException.class, () -> upload(2, video(8)));
        assertEquals(2, count()); assertFalse(Files.exists(directory.resolve("school-exam-41-2.webm")));
        upload(0, video(4)); upload(2, video(4));
        assertEquals(3, count());
        assertEquals(4, Files.size(directory.resolve("school-exam-41-0.webm")));
        verify(redis, times(5)).enforceRateLimit("exam-recording-user", "88", 20, 60, "录像上传过于频繁，请稍后重试");
    }

    @Test void outOfRangeSegmentIsRejectedAndReplacementAtLimitWorks() {
        ReflectionTestUtils.setField(service, "maxSegments", 1);
        upload(0, video(8)); upload(0, video(8));
        assertEquals(1, count());
        assertThrows(BusinessException.class, () -> upload(1, video(4)));
        assertThrows(BusinessException.class, () -> upload(-1, video(4)));
    }

    @Test void existingOrphanFilesStillConsumeQuota() throws Exception {
        Files.createDirectories(directory);
        Files.write(directory.resolve("school-exam-41-0.webm"), video(16).getBytes());
        assertThrows(BusinessException.class, () -> upload(1, video(4)));
        assertEquals(0, count());
    }

    @Test void rateLimitAndDiskReservePreventWrites() {
        doThrow(new BusinessException("limited")).when(redis).enforceRateLimit(anyString(), anyString(), anyInt(), anyInt(), anyString());
        assertThrows(BusinessException.class, () -> upload(0, video(8)));
        assertFalse(Files.exists(directory));
        reset(redis);
        ReflectionTestUtils.setField(service, "minFreeBytes", Long.MAX_VALUE);
        assertThrows(BusinessException.class, () -> upload(0, video(8)));
        assertEquals(0, count());
    }

    @Test void dishonestSizeAndInvalidMagicCannotBypassQuotaAndLeaveNoTemps() throws Exception {
        var file = mock(MultipartFile.class);
        when(file.getSize()).thenReturn(4L);
        when(file.getInputStream()).thenReturn(new ByteArrayInputStream(video(8).getBytes()));
        assertThrows(BusinessException.class, () -> upload(0, file));
        assertEquals(0, count());
        try (var files = Files.list(directory)) { assertEquals(0, files.count()); }
        assertThrows(BusinessException.class, () -> upload(0, new MockMultipartFile("file", new byte[]{1, 2, 3, 4})));
        when(file.getSize()).thenReturn(3L);
        assertThrows(BusinessException.class, () -> upload(0, file));
        assertEquals(0, count());
    }

    @Test void unauthorizedUploadFailsBeforeRateLimitOrDiskAccess() {
        when(exams.studentMonitoringPolicy(41L, 89L)).thenThrow(new BusinessException("denied"));
        assertThrows(BusinessException.class, () -> service.upload(41L, 0, 89L, video(8)));
        verifyNoInteractions(redis);
        assertFalse(Files.exists(directory));
    }

    @Test void concurrentServiceInstancesCannotOvershootProcessQuota() throws Exception {
        ReflectionTestUtils.setField(service, "maxTotalBytes", 8L);
        // Two independent service objects share the database and storage volume.
        var other = new SchoolExamRecordingService(jdbc, exams);
        for (String field : new String[]{"securityStore", "recordingDirectory", "maxTotalBytes", "maxSegments", "uploadsPerMinute", "minFreeBytes"})
            ReflectionTestUtils.setField(other, field, ReflectionTestUtils.getField(service, field));
        var start = new CountDownLatch(1);
        var workers = Executors.newFixedThreadPool(2);
        try {
            var first = workers.submit(() -> { start.await(); try { upload(0, video(8)); return true; } catch (BusinessException ex) { return false; } });
            var second = workers.submit(() -> { start.await(); try { tx.executeWithoutResult(status -> other.upload(41L, 1, 88L, video(8))); return true; } catch (BusinessException ex) { return false; } });
            start.countDown();
            assertNotEquals(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
            assertEquals(1, count());
            try (var files = Files.list(directory)) { assertEquals(1, files.count()); }
        } finally { workers.shutdownNow(); }
    }
}
