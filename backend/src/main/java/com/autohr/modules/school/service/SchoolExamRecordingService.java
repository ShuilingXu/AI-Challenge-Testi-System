package com.autohr.modules.school.service;

import com.autohr.common.exception.BusinessException;
import com.autohr.common.file.FileDownloadSupport;
import com.autohr.common.file.UploadPaths;
import com.autohr.modules.auth.dto.SessionUserVO;
import lombok.RequiredArgsConstructor;
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
public class SchoolExamRecordingService {
    private final JdbcTemplate jdbc;
    private final SchoolExamService exams;

    public void upload(Long processId, int segmentNo, Long userId, MultipartFile file) {
        if (segmentNo < 0 || segmentNo > 10000) throw new BusinessException("录像分段编号无效");
        Map<String, Object> policy = exams.studentMonitoringPolicy(processId, userId);
        if (((Number) policy.get("camera_enabled")).intValue() != 1
                && ((Number) policy.get("screen_recording_enabled")).intValue() != 1) {
            throw new BusinessException("该考试未启用录像");
        }
        if (file == null || file.isEmpty() || file.getSize() > 100L * 1024 * 1024) {
            throw new BusinessException("录像分段不能为空或超过100MB");
        }
        try (var input = file.getInputStream()) {
            if (!java.util.Arrays.equals(input.readNBytes(4), new byte[]{0x1a, 0x45, (byte) 0xdf, (byte) 0xa3})) {
                throw new BusinessException("仅支持有效的WebM录像");
            }
            Files.createDirectories(UploadPaths.RECORDING_DIR);
            String name = "school-exam-" + processId + "-" + segmentNo + ".webm";
            Path target = UploadPaths.RECORDING_DIR.resolve(name).normalize();
            if (!target.startsWith(UploadPaths.RECORDING_DIR)) throw new BusinessException("录像路径无效");
            Path temp = Files.createTempFile(UploadPaths.RECORDING_DIR, "exam-", ".webm");
            try {
                file.transferTo(temp);
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
        return FileDownloadSupport.buildInlineResponse(UploadPaths.RECORDING_DIR.resolve(name).toString(),
                UploadPaths.RECORDING_DIR, name, "video/webm", "录像文件不可访问");
    }
}
