package com.sni.bilanga.security.admin.provisioning.service.interfaces;


import com.sni.bilanga.security.admin.user.dto.request.UserRequest;
import com.sni.bilanga.security.admin.user.model.Users;

public interface UserProvisioningService {

    Users initializeGlobalAdmin(UserRequest request);

    Users createStaff(UserRequest request, String assignedBy);


}
