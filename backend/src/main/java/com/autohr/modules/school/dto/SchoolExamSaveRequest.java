package com.autohr.modules.school.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SchoolExamSaveRequest {
    private Long id;

    @NotBlank(message = "考试代码不能为空")
    @Size(max = 64)
    private String examCode;

    @NotBlank(message = "考试名称不能为空")
    @Size(max = 128)
    private String examName;

    private Long classId;
    private Long knowledgeBaseId;
    private Long processTemplateId;

    @Size(max = 2000)
    private String instructions;

    @Min(value = 1, message = "答题轮数至少为1")
    @Max(value = 20, message = "答题轮数不能超过20")
    private Integer questionRounds;

    @Min(value = 0, message = "及格分不能低于0")
    @Max(value = 100, message = "及格分不能高于100")
    private Integer passingScore;
    private LocalDateTime publishStart;
    private LocalDateTime publishEnd;
    private String status;
}
