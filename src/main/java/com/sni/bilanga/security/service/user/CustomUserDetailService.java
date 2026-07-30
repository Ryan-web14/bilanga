package com.sni.bilanga.security.service.user;


import com.sni.bilanga.security.admin.user.model.UserPrincipal;
import com.sni.bilanga.security.admin.user.model.Users;
import com.sni.bilanga.security.admin.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class CustomUserDetailService implements UserDetailsService {

    private final UserRepository userRepo;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        log.debug("Loading user by username: {}", email);

        try{
            Users user = userRepo.findByEmailIgnoreCase(email)
                    .orElseThrow(() -> new UsernameNotFoundException("User not found with email : " + email));
            log.info("User successfully found with email: {}", email);
            return new UserPrincipal(user);
        }catch(Exception e){
            log.info("User not found with email: {}", email);
            throw new UsernameNotFoundException("User not found with email : " + email);
        }
    }
}
