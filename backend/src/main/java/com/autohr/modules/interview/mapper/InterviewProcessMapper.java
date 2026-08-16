package com.autohr.modules.interview.mapper;

import com.autohr.modules.interview.entity.InterviewProcess;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface InterviewProcessMapper extends BaseMapper<InterviewProcess> {

    @Update("UPDATE interview_process SET anti_cheat_switch_count = COALESCE(anti_cheat_switch_count, 0) + 1, "
            + "updated_at = CURRENT_TIMESTAMP WHERE id = #{processId} AND overall_status = 'IN_PROGRESS' "
            + "AND current_stage = 'AI' AND stage_status = 'IN_PROGRESS'")
    int incrementAntiCheatSwitchCount(@Param("processId") Long processId);
}
