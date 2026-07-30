package com.sni.bilanga.security.admin.user.service.implementation;



import com.sni.bilanga.config.properties.AppProperties;
import com.sni.bilanga.exception.customs.BadRequestException;
import com.sni.bilanga.exception.customs.ResourceAlreadyExistException;
import com.sni.bilanga.exception.customs.ResourceNotFoundException;
import com.sni.bilanga.exception.customs.ValidationException;
import com.sni.bilanga.security.admin.user.dto.request.UserRequest;
import com.sni.bilanga.security.admin.user.dto.response.UserResponse;
import com.sni.bilanga.security.admin.user.mapper.interfaces.UserMapper;
import com.sni.bilanga.security.admin.user.model.Users;
import com.sni.bilanga.security.admin.user.repository.UserRepository;
import com.sni.bilanga.security.admin.user.service.interfaces.UserService;
import com.sni.bilanga.templateResponse.PaginatedResponse;
import com.sni.bilanga.utils.validation.ValidationUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@RequiredArgsConstructor
@Transactional
@Service
@Slf4j
public class UserServiceImpl implements UserService {

    private final UserRepository userRepo;
    private final AppProperties.Security securityConfig;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final int  DEFAULT_GENERATOR_LENGTH = 8;


    @Transactional
    public Users createUser(UserRequest request){

        log.debug("Creating user with email {}",request.getEmail());
        List<String> err = validateUser(request);
        try{
            if(!err.isEmpty()){
                log.debug("Invalid user request");
                throw new ValidationException("Invalid user request", err);
            }

            if(userRepo.existsByEmail(request.getEmail())){
                log.debug("User with email {} already exist", request.getEmail());
                throw new ResourceAlreadyExistException("User with email " + request.getEmail() + " already exist");
            }

            Users user =  userMapper.toEntity(request);
            user.setIsAccountEnabled(true);
            user.setIsAccountLocked(false);
            user.setIsAccountExpired(false);
            user.setFailedLoginAttempts(0);
            user.setUserId(generateUserIdentifier(DEFAULT_GENERATOR_LENGTH));
            UserResponse response = userMapper.toDTO(user);

                user.setPasswordHash(passwordEncoder.encode(request.getPassword()));


            userRepo.save(user);
            return user;

        }catch (ValidationException ex){
            throw new BadRequestException("Invalid user request", ex);
        }catch (ResourceAlreadyExistException ex){
            throw new BadRequestException("User with email " + request.getEmail() + " already exists", ex);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse getUserByEmail(String email){

        if(!userRepo.existsByEmail(email)){
            throw new ResourceNotFoundException("User with email " + email + " not found");
        }

        return userMapper.toDTO(userRepo.findByEmail(email)
                .orElseThrow(()-> new ResourceNotFoundException("No user found")));
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserResponse> getAllActiveUsers(){

        return  userRepo.findAllActiveUsers().stream()
                .map(userMapper::toDTO)
                .toList();
    }

    @Override
    public Users getUserByIdForService(Long id){
        return userRepo.findById(id)
                .orElseThrow(()-> new ResourceNotFoundException("No user found"));
    }

    @Override
    public Users getUserByUserIdForService(String userId) {
        if (!StringUtils.hasText(userId)) {
            throw new ResourceNotFoundException("No user found");
        }
        return userRepo.findByUserId(userId.trim())
                .orElseThrow(() -> new ResourceNotFoundException("No user found"));
    }

    @Override
    public Users getUserByEmailForService(String email) {
        return userRepo.findByEmail(email)
                .orElseThrow(()-> new ResourceNotFoundException("No user found"));
    }

    @Override
    @Transactional(readOnly = true)
    public PaginatedResponse<UserResponse> getPaginatedUsers(Pageable pageable){

        Page<Users> users = userRepo.findAllByDeletedFalse(pageable);
        return new PaginatedResponse<>(users.map(userMapper::toDTO));

    }

    @Override
    public void softDeleteUser(String email){

        if(!userRepo.existsByEmail(email)){
            throw new ResourceNotFoundException("User with email " + email + " not found");
        }

        Users user = userRepo.findByEmail(email)
                .orElseThrow(()-> new ResourceNotFoundException("No user found"));
        user.setDeleted(true);
        user.setIsAccountEnabled(false);
        user.setIsAccountLocked(true);
        user.setIsAccountExpired(false);
        userRepo.save(user);
    }

    @Override
    public void deleteUser(Long id){

        Users user = userRepo.findById(id)
                .orElseThrow(()-> new ResourceNotFoundException("No user found"));
        userRepo.delete(user);
    }

    @Override
    @Transactional
    public void activateUser(String email){

        if(!userRepo.existsByEmail(email)){
            throw new ResourceNotFoundException("User with email " + email + " not found");
        }

        Users user = userRepo.findByEmail(email).orElseThrow(
                ()-> new ResourceNotFoundException("User with email " + email + " not found"));
        user.setIsAccountEnabled(true);
        user.setIsAccountLocked(false);
        user.setIsAccountExpired(false);
        userRepo.save(user);
    }

    @Override
    public void deactivateUser(String email){
        if(!userRepo.existsByEmail(email)){
            throw new ResourceNotFoundException("User with email " + email + " not found");
        }

        Users user = userRepo.findByEmail(email).orElseThrow(
                ()-> new ResourceNotFoundException("User with email " + email + " not found"));
        user.setIsAccountEnabled(false);
        user.setIsAccountLocked(true);
        user.setIsAccountExpired(false);
        userRepo.save(user);
    }

    @Override
    public boolean userExists(String email){
        return userRepo.existsByEmail(email);
    }

    @Override
    public boolean isUserActive(String email){

        if(!userRepo.existsByEmail(email)){
            throw new ResourceNotFoundException("User with email " + email + " not found");
        }

        Users user = userRepo.findByEmail(email).orElseThrow(
                ()-> new ResourceNotFoundException("User with email " + email + " not found"));
        return user.getIsAccountEnabled();
    }

    @Override
    @Transactional
    public void updateUserPassword(String email, String oldPassword, String newPassword){

        if(!userRepo.existsByEmail(email)){
            throw new ResourceNotFoundException("User with email " + email + " not found");
        }

        //We check if the old password is correct
        Users user = userRepo.findByEmail(email).orElseThrow(
                ()-> new ResourceNotFoundException("User with email " + email + " not found"));
        if(!passwordEncoder.matches(oldPassword, user.getPasswordHash())){
            throw new BadRequestException("Old password is incorrect");
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepo.save(user);
    }

    @Override
    @Transactional
    public void resetUserPassword(Users user, String newPassword){
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepo.save(user);
    }

    @Override
    @Transactional
    public String updateUserPasswordByAdmin(String email, String newPassword, boolean isGenerated){

        if(!userRepo.existsByEmail(email)){
            throw new ResourceNotFoundException("User with email " + email + " not found");
        }

        Users user = userRepo.findByEmail(email).orElseThrow(
                ()-> new ResourceNotFoundException("User with email " + email + " not found"));


        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepo.save(user);
        return email;
    }

    @Override
    @Transactional
    public void updateLastLogin(String email) {
        if (!userRepo.existsByEmail(email)) {
            throw new ResourceNotFoundException("User with email " + email + " not found");
        }

        Users user = userRepo.findByEmail(email).orElseThrow(
                () -> new ResourceNotFoundException("User with email " + email + " not found"));
        user.setLastLogin(LocalDateTime.now());
        //userRepo.save(user);
    }

    @Override
    @Transactional
    public void recordLoginSuccess(String email) {
        userRepo.findByEmailIgnoreCase(email).ifPresent(user -> {
            user.setFailedLoginAttempts(0);
            user.setLastLogin(LocalDateTime.now());
            //userRepo.save(user);
        });
    }

    @Override
    @Transactional
    public void recordLoginFailure(String email) {
        userRepo.findByEmailIgnoreCase(email).ifPresent(user -> {
            int failures = user.getFailedLoginAttempts() == null ? 0 : user.getFailedLoginAttempts();
            int nextFailures = failures + 1;
            user.setFailedLoginAttempts(nextFailures);
            if (nextFailures >= securityConfig.getFailedLogin().getMaxAttempts()) {
                user.setIsAccountLocked(true);
                log.warn("User {} locked after {} failed login attempts", email, nextFailures);
            }
           // userRepo.save(user);
        });
    }



    @Override
    @Transactional
    public void unlockAccount(String email) {
        Users user = userRepo.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User with email " + email + " not found"));
        user.setIsAccountLocked(false);
        user.setFailedLoginAttempts(0);
        userRepo.save(user);
    }

    private List<String> validateUser(UserRequest request){

        List<String> err = new ArrayList<>();

        if(!ValidationUtils.validateEmail(request.getEmail())){
            err.add("Invalid user request, the email is not valid");
        }

        if (!request.isGeneratePassword() && !StringUtils.hasText(request.getPassword())) {
            err.add("Invalid user request, password is required when generatePassword is false");
        }

     return err;
    }

    private String generateUserIdentifier(int length){
        String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("ddMMyy"));
        String random = generateRandomString(length);
        return "USR" + "-" + date + "-" + random;
    }

    private String generateRandomString(int length){
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }

}
