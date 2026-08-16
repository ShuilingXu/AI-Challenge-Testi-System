package com.autohr.modules.auth.dto;

import lombok.Data;
import com.fasterxml.jackson.annotation.JsonInclude;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LoginResponse {
    private String token;
    private SessionUserVO user;
}
