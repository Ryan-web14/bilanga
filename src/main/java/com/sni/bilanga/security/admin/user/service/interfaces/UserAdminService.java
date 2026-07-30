package com.sni.bilanga.security.admin.user.service.interfaces;


import com.sni.bilanga.security.admin.user.dto.request.AdminCreateUserRequest;
import com.sni.bilanga.security.admin.user.dto.request.AdminResetUserPasswordRequest;
import com.sni.bilanga.security.admin.user.dto.request.AdminUpdateUserRequest;
import com.sni.bilanga.security.admin.user.dto.response.AdminUserResponse;
import com.sni.bilanga.templateResponse.PaginatedResponse;
import org.springframework.data.domain.Pageable;

public interface UserAdminService {
    AdminUserResponse createUser(AdminCreateUserRequest request, String assignedBy);
    PaginatedResponse<AdminUserResponse> list(String email, Boolean enabled, Boolean locked, Boolean deleted, Pageable pageable);
    java.util.List<AdminUserResponse> searchByName(String query);
    AdminUserResponse getByCode(String userCode);
    AdminUserResponse getByEmail(String email);
    AdminUserResponse update(String userCode, AdminUpdateUserRequest request, String assignedBy);
    AdminUserResponse activate(String userCode);
    AdminUserResponse deactivate(String userCode);
    AdminUserResponse unlock(String userCode);
    AdminUserResponse resetPassword(String userCode, AdminResetUserPasswordRequest request);
    void initiatePasswordReset(String userId, String actor);
    void archive(String userCode);
}
