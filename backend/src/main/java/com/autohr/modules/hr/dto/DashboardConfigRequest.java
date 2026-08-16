package com.autohr.modules.hr.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class DashboardConfigRequest { @NotBlank @Size(max = 10000) private String configJson; }
