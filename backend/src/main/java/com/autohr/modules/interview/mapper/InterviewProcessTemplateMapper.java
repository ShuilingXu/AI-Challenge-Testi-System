package com.autohr.modules.interview.mapper;

import com.autohr.modules.interview.entity.InterviewProcessTemplate;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface InterviewProcessTemplateMapper extends BaseMapper<InterviewProcessTemplate> {

    @Update("UPDATE interview_process_template SET template_name = #{templateName}, description = #{description}, "
            + "status = #{status}, version = version + 1 WHERE id = #{id} AND version = #{version}")
    int updateWithVersion(@Param("id") Long id,
                          @Param("version") Integer version,
                          @Param("templateName") String templateName,
                          @Param("description") String description,
                          @Param("status") Integer status);

    @Update("DELETE FROM interview_process_template WHERE id = #{id} AND version = #{version}")
    int deleteWithVersion(@Param("id") Long id, @Param("version") Integer version);
}
