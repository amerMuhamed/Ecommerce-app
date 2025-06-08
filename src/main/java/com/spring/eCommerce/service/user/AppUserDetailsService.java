package com.spring.eCommerce.service.user;

import com.spring.eCommerce.entity.AppUser;
import com.spring.eCommerce.repository.UserRepo;
import com.spring.eCommerce.security.AppUserDetail;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
@RequiredArgsConstructor
@Service
public class AppUserDetailsService implements UserDetailsService {
    private final UserRepo userRepo;
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        AppUser user=userRepo.findByUsername(username).orElseThrow(()->new UsernameNotFoundException("User not found with Selected User name "+username));

        return new AppUserDetail(user);
    }

}
