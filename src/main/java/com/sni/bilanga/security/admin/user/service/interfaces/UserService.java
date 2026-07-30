package com.sni.bilanga.security.admin.user.service.interfaces;


import com.sni.bilanga.security.admin.user.dto.request.UserRequest;
import com.sni.bilanga.security.admin.user.dto.response.UserResponse;
import com.sni.bilanga.security.admin.user.model.Users;
import com.sni.bilanga.templateResponse.PaginatedResponse;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface UserService {

    Users createUser(UserRequest request);

    UserResponse getUserByEmail(String email);
    List<UserResponse> getAllActiveUsers();
    Users getUserByIdForService(Long id);
    Users getUserByUserIdForService(String userId);
    Users getUserByEmailForService(String email);
    PaginatedResponse<UserResponse> getPaginatedUsers(Pageable pageable);

    void updateUserPassword(String email, String oldPassword, String newPassword);
    String updateUserPasswordByAdmin(String email, String newPassword, boolean isGenerated);
    void updateLastLogin(String email);
    void recordLoginSuccess(String email);
    void recordLoginFailure(String email);
     void resetUserPassword(Users user, String newPassword);

    void softDeleteUser(String email);
    void deleteUser(Long id);

    void activateUser(String email);
    void deactivateUser(String email);
    boolean userExists(String email);
    boolean isUserActive(String email);

    void unlockAccount(String email);


    //TODO implement later
    // Search and filter
//    List<UserResponse> searchUsers(String keyword);
//    List<UserResponse> filterUsersByRole(Long roleId);
//    List<UserResponse> filterUsersByStatus(UserStatus)
}
