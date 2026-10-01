package com.autohr.modules.school.service;

import com.autohr.common.exception.BusinessException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Objects;

/** Shared execution guard; reading historical answers does not require an open window. */
public final class ExamWindowGuard {
    private ExamWindowGuard() { }

    public static void requireOpen(JdbcTemplate jdbc, Long processId, boolean lockExam) {
        if (jdbc == null) throw new BusinessException("无法校验考试开放状态");
        var ids = jdbc.queryForList("SELECT exam_id FROM school_exam_attempt WHERE process_id=?", Long.class, processId);
        if (ids.isEmpty()) throw new BusinessException("考试记录不存在");
        // A write lock inside the answer transaction orders grade commits against
        // teacher edits on SQLite, PostgreSQL and MySQL without locking across LLM calls.
        if (lockExam) jdbc.update("UPDATE school_exam SET id=id WHERE id=?", ids.get(0));
        var rows = jdbc.queryForList("SELECT e.status,e.class_id AS examClassId,e.publish_start AS publishStart,"
                + "e.publish_end AS publishEnd,s.class_id AS studentClassId,s.status AS studentStatus,c.status AS classStatus "
                + "FROM school_exam_attempt a JOIN school_exam e ON e.id=a.exam_id "
                + "JOIN school_student s ON s.id=a.student_id JOIN school_class c ON c.id=s.class_id WHERE a.process_id=?", processId);
        if (rows.isEmpty()) throw new BusinessException("考试记录不存在");
        var row = rows.get(0);
        if (!"PUBLISHED".equals(row.get("status"))
                || ((Number) row.get("studentStatus")).intValue() != 1
                || ((Number) row.get("classStatus")).intValue() != 1
                || (row.get("examClassId") != null && !Objects.equals(
                        ((Number) row.get("examClassId")).longValue(), ((Number) row.get("studentClassId")).longValue()))) {
            throw new BusinessException("该考试当前不可参加");
        }
        var now = LocalDateTime.now();
        var start = dateTime(row.get("publishStart"));
        var end = dateTime(row.get("publishEnd"));
        if ((start != null && now.isBefore(start)) || (end != null && !now.isBefore(end))) {
            throw new BusinessException("当前不在考试开放时间内");
        }
    }

    private static LocalDateTime dateTime(Object value) {
        if (value == null) return null;
        if (value instanceof LocalDateTime time) return time;
        if (value instanceof Timestamp timestamp) return timestamp.toLocalDateTime();
        return LocalDateTime.parse(value.toString().replace(' ', 'T'));
    }
}
