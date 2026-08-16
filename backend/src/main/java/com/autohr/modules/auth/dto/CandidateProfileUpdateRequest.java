package com.autohr.modules.auth.dto;

import lombok.Data;

@Data
public class CandidateProfileUpdateRequest {
    private String displayName;
    private String mobilePhone;
    private String email;
}
