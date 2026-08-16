package com.autohr.modules.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class CaptchaVO {
    private String captchaId;
    private String imageBase64;
}
