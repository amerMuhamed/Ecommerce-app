package com.spring.eCommerce.security;

import com.spring.eCommerce.entity.AppUser;
import lombok.Getter;
import lombok.Setter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Getter
@Setter
public class AppUserDetail implements UserDetails {
    private Long id;
    private String username;
    private String password;
    private String fullName;
    private List<GrantedAuthority> authorities;

    public AppUserDetail() {
    }

    public AppUserDetail(AppUser appUser) {
        super();
        this.id = appUser.getId();
        this.fullName = appUser.getFullName();
        this.username = appUser.getUsername();
        this.password = appUser.getPassword();
        List<GrantedAuthority> authorities = new ArrayList<>();
        if (!appUser.getRoles().isEmpty()) {
            appUser.getRoles().forEach(role -> {
                authorities.add(new SimpleGrantedAuthority(role.getName()));
            });
        }
this.authorities=authorities;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return UserDetails.super.isAccountNonExpired();
    }

    @Override
    public boolean isAccountNonLocked() {
        return UserDetails.super.isAccountNonLocked();
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return UserDetails.super.isCredentialsNonExpired();
    }

    @Override
    public boolean isEnabled() {
        return UserDetails.super.isEnabled();
    }
}
