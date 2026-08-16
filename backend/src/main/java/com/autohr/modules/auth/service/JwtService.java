package com.autohr.modules.auth.service;

import com.autohr.modules.auth.entity.SysUser;
import io.jsonwebtoken.Claims;

public interface JwtService {
    String generateToken(SysUser user);
    Claims parseToken(String token);
    String extractUsername(String token);
}
