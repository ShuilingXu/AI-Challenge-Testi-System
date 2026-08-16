package com.autohr.modules.interview.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class IceServerVO {
    private List<String> urls;
    private String username;
    private String credential;
}
