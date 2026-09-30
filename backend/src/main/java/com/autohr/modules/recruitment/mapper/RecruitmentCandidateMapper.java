package com.autohr.modules.recruitment.mapper;

import com.autohr.modules.recruitment.entity.RecruitmentCandidate;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;

public interface RecruitmentCandidateMapper extends BaseMapper<RecruitmentCandidate> {

    /**
     * School examination databases intentionally keep only the candidate fields
     * needed by the exam flow. Avoid the HR entity's optional columns here.
     */
    @Select("SELECT id, job_id AS jobId, full_name AS fullName, mobile_phone AS mobilePhone, "
            + "major, application_status AS applicationStatus, interview_stage_status AS interviewStageStatus, "
            + "interviewee_user_id AS intervieweeUserId, interview_process_id AS interviewProcessId, "
            + "created_at AS createdAt, updated_at AS updatedAt "
            + "FROM school_exam_candidate WHERE id=#{id}")
    RecruitmentCandidate selectSchoolCandidateById(Long id);

    @Select("SELECT id, job_id AS jobId, full_name AS fullName, mobile_phone AS mobilePhone, "
            + "major, application_status AS applicationStatus, interview_stage_status AS interviewStageStatus, "
            + "interviewee_user_id AS intervieweeUserId, interview_process_id AS interviewProcessId, "
            + "created_at AS createdAt, updated_at AS updatedAt "
            + "FROM school_exam_candidate WHERE job_id=#{jobId} AND interviewee_user_id=#{userId} LIMIT 1")
    RecruitmentCandidate selectSchoolCandidate(Long jobId, Long userId);

    @Insert("INSERT INTO school_exam_candidate "
            + "(job_id, full_name, mobile_phone, major, application_status, interview_stage_status, interviewee_user_id) "
            + "VALUES (#{jobId}, #{fullName}, #{mobilePhone}, #{major}, #{applicationStatus}, #{interviewStageStatus}, #{intervieweeUserId})")
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insertSchoolCandidate(RecruitmentCandidate candidate);

    @org.apache.ibatis.annotations.Update("UPDATE school_exam_candidate SET interview_process_id=#{interviewProcessId}, "
            + "application_status=#{applicationStatus}, interview_stage_status=#{interviewStageStatus}, updated_at=CURRENT_TIMESTAMP "
            + "WHERE id=#{id}")
    int updateSchoolCandidate(RecruitmentCandidate candidate);
}
