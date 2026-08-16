package com.autohr.modules.auth.service;

import com.autohr.common.api.PageQuery;
import com.autohr.common.api.PageResponse;
import com.autohr.modules.auth.dto.CandidateProfileUpdateRequest;
import com.autohr.modules.auth.dto.CandidateRegisterRequest;
import com.autohr.modules.auth.dto.LoginRequest;
import com.autohr.modules.auth.dto.LoginResponse;
import com.autohr.modules.auth.dto.PasswordChangeRequest;
import com.autohr.modules.auth.dto.PasswordResetRequest;
import com.autohr.modules.auth.dto.SessionUserVO;
import com.autohr.modules.auth.dto.UserAdminUpdateRequest;

public interface AuthService {
    LoginResponse login(LoginRequest request);
    SessionUserVO registerCandidate(CandidateRegisterRequest request);
    SessionUserVO getCurrentUser();
    SessionUserVO updateCandidateProfile(Long userId, CandidateProfileUpdateRequest request);
    SessionUserVO loadUserByUsername(String username);
    PageResponse<SessionUserVO> listUsers(String roleCode, Integer status, String keyword,
                                          String operatorRoleCode, PageQuery pageQuery);
    SessionUserVO updateUserByAdmin(Long id, UserAdminUpdateRequest request, String operatorRoleCode);
    void deleteUserByAdmin(Long id, Long operatorId, String operatorRoleCode);
    boolean canResetPassword(String mobilePhone, String email);
    void resetPassword(PasswordResetRequest request);
    LoginResponse changePassword(String username, PasswordChangeRequest request);
    void logout(String username);
}
