package com.autohr.modules.school.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ScoreReviewRequest {
    @NotNull
    @Min(0)
    @Max(100)
    private Integer score;
    @Size(max = 2000)
    private String note;
}
