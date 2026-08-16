package com.autohr.modules.interview.mapper;

import com.autohr.modules.interview.entity.InterviewAiRecord;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

public interface InterviewAiRecordMapper extends BaseMapper<InterviewAiRecord> {

    @Update("UPDATE interview_ai_record SET answer_content = #{answerContent}, answer_status = 'PROCESSING', "
            + "answer_processing_token = #{token}, answer_lease_expires_at = #{leaseExpiresAt}, "
            + "answer_processing_attempts = COALESCE(answer_processing_attempts, 0) + 1, answer_error = NULL "
            + "WHERE id = #{recordId} AND ((answer_status = 'PENDING' AND answer_content IS NULL) "
            + "OR (answer_content = #{answerContent} AND (answer_status = 'FAILED' "
            + "OR (answer_status = 'PROCESSING' AND answer_lease_expires_at <= #{now}))))")
    int claimAnswer(@Param("recordId") Long recordId,
                    @Param("answerContent") String answerContent,
                    @Param("token") String token,
                    @Param("now") LocalDateTime now,
                    @Param("leaseExpiresAt") LocalDateTime leaseExpiresAt);

    @Update("UPDATE interview_ai_record SET interviewer_score = #{interviewerScore}, scorer_score = #{scorerScore}, "
            + "average_score = #{averageScore}, interviewer_comment = #{interviewerComment}, answer_status = 'COMPLETED', "
            + "answer_processing_token = NULL, answer_lease_expires_at = NULL, answer_error = NULL "
            + "WHERE id = #{recordId} AND answer_status = 'PROCESSING' AND answer_processing_token = #{token}")
    int completeAnswer(@Param("recordId") Long recordId,
                       @Param("token") String token,
                       @Param("interviewerScore") Integer interviewerScore,
                       @Param("scorerScore") Integer scorerScore,
                       @Param("averageScore") Integer averageScore,
                       @Param("interviewerComment") String interviewerComment);

    @Update("UPDATE interview_ai_record SET answer_status = 'FAILED', answer_processing_token = NULL, "
            + "answer_lease_expires_at = NULL, answer_error = #{errorId} "
            + "WHERE id = #{recordId} AND answer_status = 'PROCESSING' AND answer_processing_token = #{token}")
    int failAnswer(@Param("recordId") Long recordId,
                   @Param("token") String token,
                   @Param("errorId") String errorId);

    @Update("UPDATE interview_ai_record SET question_status = 'PROCESSING', question_generation_token = #{token}, "
            + "question_lease_expires_at = #{leaseExpiresAt}, question_generation_attempts = COALESCE(question_generation_attempts, 0) + 1, "
            + "question_generation_error = NULL WHERE id = #{recordId} AND (question_status = 'PENDING' "
            + "OR (question_status = 'FAILED' AND (question_next_retry_at IS NULL OR question_next_retry_at <= #{now})) "
            + "OR (question_status = 'PROCESSING' AND question_lease_expires_at <= #{now}))")
    int claimQuestionGeneration(@Param("recordId") Long recordId,
                                @Param("token") String token,
                                @Param("now") LocalDateTime now,
                                @Param("leaseExpiresAt") LocalDateTime leaseExpiresAt);

    @Update("UPDATE interview_ai_record SET question_content = #{questionContent}, question_status = 'READY', "
            + "question_generation_token = NULL, question_lease_expires_at = NULL, question_next_retry_at = NULL, "
            + "question_generation_error = NULL WHERE id = #{recordId} AND question_status = 'PROCESSING' "
            + "AND question_generation_token = #{token}")
    int completeQuestionGeneration(@Param("recordId") Long recordId,
                                   @Param("token") String token,
                                   @Param("questionContent") String questionContent);

    @Update("UPDATE interview_ai_record SET question_status = 'FAILED', question_generation_token = NULL, "
            + "question_lease_expires_at = NULL, question_next_retry_at = #{nextRetryAt}, question_generation_error = #{errorId} "
            + "WHERE id = #{recordId} AND question_status = 'PROCESSING' AND question_generation_token = #{token}")
    int failQuestionGeneration(@Param("recordId") Long recordId,
                               @Param("token") String token,
                               @Param("nextRetryAt") LocalDateTime nextRetryAt,
                               @Param("errorId") String errorId);

    @Update("UPDATE interview_ai_record SET question_status = 'CANCELLED', question_generation_token = NULL, "
            + "question_lease_expires_at = NULL WHERE id = #{recordId} AND question_status = 'PROCESSING' "
            + "AND question_generation_token = #{token}")
    int cancelQuestionGeneration(@Param("recordId") Long recordId, @Param("token") String token);
}
