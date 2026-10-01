package com.autohr.modules.school.service;

import com.autohr.common.exception.BusinessException;
import com.autohr.common.file.FileDownloadSupport;
import com.autohr.common.file.UploadPaths;
import com.autohr.modules.auth.dto.SessionUserVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;
import com.autohr.modules.auth.service.AuthRedisSecurityStore;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class SchoolExamRecordingService {
    private final JdbcTemplate jdbc;
    private final SchoolExamService exams;
    @jakarta.annotation.Resource private AuthRedisSecurityStore securityStore;
    @Value("${school.recording.max-total-bytes:536870912}") private long maxTotalBytes = 536870912L;
    @Value("${school.recording.max-segments:720}") private int maxSegments = 720;
    @Value("${school.recording.uploads-per-minute:20}") private int uploadsPerMinute = 20;
    @Value("${school.recording.min-free-bytes:1073741824}") private long minFreeBytes = 1073741824L;
    @Value("${school.recording.directory:}") private String recordingDirectory;

    @Transactional
    public void upload(Long processId, int segmentNo, Long userId, MultipartFile file) {
        if (segmentNo < 0 || segmentNo >= maxSegments) throw new BusinessException("录像分段编号无效或超过段数配额");
        // Acquire the write lock before reads, avoiding SQLite read-to-write
        // upgrades when simultaneous uploads target the same process.
        jdbc.update("UPDATE school_exam_process SET id=id WHERE id=?", processId);
        Map<String, Object> policy = exams.studentMonitoringPolicy(processId, userId);
        if (((Number) policy.get("camera_enabled")).intValue() != 1
                && ((Number) policy.get("screen_recording_enabled")).intValue() != 1) {
            throw new BusinessException("该考试未启用录像");
        }
        if (file == null || file.isEmpty() || file.getSize() < 4 || file.getSize() > 100L * 1024 * 1024) {
            throw new BusinessException("录像分段大小必须在4字节至100MB之间");
        }
        securityStore.enforceRateLimit("exam-recording-user", String.valueOf(userId), uploadsPerMinute, 60,
                "录像上传过于频繁，请稍后重试");
        try (var input = file.getInputStream()) {
            byte[] header = input.readNBytes(4);
            if (!java.util.Arrays.equals(header, new byte[]{0x1a, 0x45, (byte) 0xdf, (byte) 0xa3})) {
                throw new BusinessException("仅支持有效的WebM录像");
            }
            Path directory = directory();
            Files.createDirectories(directory);
            String name = "school-exam-" + processId + "-" + segmentNo + ".webm";
            Path target = directory.resolve(name).normalize();
            if (!target.startsWith(directory) || Files.isSymbolicLink(target)) throw new BusinessException("录像路径无效");
            long total = 0;
            int count = 0;
            // Count actual files, including files left behind after a DB failure
            // and segments created before quotas were introduced.
            try (var files = Files.newDirectoryStream(directory, "school-exam-" + processId + "-*.webm")) {
                for (Path path : files) { total += Files.size(path); count++; }
            }
            long previousSize = Files.exists(target) ? Files.size(target) : 0;
            if (!Files.exists(target) && count >= maxSegments) throw new BusinessException("录像段数配额已用尽");
            long available = maxTotalBytes - total + previousSize;
            if (file.getSize() > available) throw new BusinessException("该考试录像总量超过配额");
            long free = Files.getFileStore(directory).getUsableSpace();
            if (free - file.getSize() < minFreeBytes) {
                log.warn("Recording disk reserve reached: usableBytes={}, reserveBytes={}, processId={}", free, minFreeBytes, processId);
                throw new BusinessException("录像存储空间不足，请联系管理员");
            }
            Path temp = Files.createTempFile(directory, "exam-", ".webm");
            try {
                // Bound the bytes read as well as the multipart metadata.
                try (var output = Files.newOutputStream(temp)) {
                    output.write(header);
                    long written = header.length;
                    byte[] buffer = new byte[8192];
                    int length;
                    while ((length = input.read(buffer)) != -1) {
                        written += length;
                        if (written > Math.min(available, 100L * 1024 * 1024) || written > file.getSize())
                            throw new BusinessException("录像分段超过配额或声明大小");
                        output.write(buffer, 0, length);
                    }
                }
                Files.move(temp, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                jdbc.update("DELETE FROM school_exam_recording WHERE process_id=? AND segment_no=?", processId, segmentNo);
                jdbc.update("INSERT INTO school_exam_recording(process_id,segment_no,file_name) VALUES(?,?,?)", processId, segmentNo, name);
            } finally {
                Files.deleteIfExists(temp);
            }
        } catch (IOException ex) {
            throw new BusinessException("录像保存失败");
        }
    }

    public List<Map<String, Object>> list(Long processId, SessionUserVO actor) {
        exams.adminAttemptDetails(processId, actor);
        return jdbc.queryForList("SELECT segment_no AS segmentNo,created_at AS createdAt FROM school_exam_recording WHERE process_id=? ORDER BY segment_no", processId);
    }

    public ResponseEntity<Resource> open(Long processId, int segmentNo, SessionUserVO actor) {
        exams.adminAttemptDetails(processId, actor);
        List<String> names = jdbc.queryForList("SELECT file_name FROM school_exam_recording WHERE process_id=? AND segment_no=?", String.class, processId, segmentNo);
        if (names.isEmpty()) throw new BusinessException("录像分段不存在");
        String name = names.get(0);
        return FileDownloadSupport.buildInlineResponse(directory().resolve(name).toString(),
                directory(), name, "video/webm", "录像文件不可访问");
    }

    private Path directory() {
        return recordingDirectory == null || recordingDirectory.isBlank() ? UploadPaths.RECORDING_DIR
                : Path.of(recordingDirectory).toAbsolutePath().normalize();
    }
}
