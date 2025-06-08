package com.spring.eCommerce.service;

import com.spring.eCommerce.dto.JWTResponse;
import com.spring.eCommerce.entity.AppUser;
import com.spring.eCommerce.entity.Role;
import com.spring.eCommerce.entity.TokenInfo;
import com.spring.eCommerce.repository.RoleRepo;
import com.spring.eCommerce.security.AppUserDetail;
import com.spring.eCommerce.security.JwtTokenUtils;
import com.spring.eCommerce.service.token.TokenInfoService;
import com.spring.eCommerce.service.user.UserService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Set;
import java.util.UUID;


@Log4j2
@Service
@RequiredArgsConstructor
public class AuthService {
    @Autowired
    private final AuthenticationManager auth;

    @Autowired
    private final HttpServletRequest request;

    @Autowired
    private final JwtTokenUtils jwtTokenUtils;

    @Autowired
    private final TokenInfoService tokenInfoService;

    @Autowired
    private final UserService userService;

    @Autowired
    private final RoleRepo roleRepo;

    public JWTResponse login(String login, String password) {
        Authentication authentication = auth.authenticate(
                new UsernamePasswordAuthenticationToken(login, password)
        );
        log.debug("valid userDetails credentials");
        AppUserDetail appUserDetail = (AppUserDetail) authentication.getPrincipal();

        SecurityContextHolder.getContext().setAuthentication(authentication);
        log.debug("SecurityContextHolder updated. User: {}", login);
        TokenInfo tokenInfo = createLoginToken(appUserDetail);
        return JWTResponse.builder().accessToken(tokenInfo.getAccessToken()).refreshToken(tokenInfo.getRefreshToken()).build();
    }

    public TokenInfo createLoginToken(AppUserDetail appUser) {
        String userAgent = request.getHeader(HttpHeaders.USER_AGENT);

        InetAddress ip = null;
        try {
            ip = InetAddress.getLocalHost();
        } catch (UnknownHostException e) {
            e.printStackTrace();
            log.error("error in getting ip address", e);
        }

        String accessTokenId = UUID.randomUUID().toString();
        String accessToken = JwtTokenUtils.generateToken(appUser.getUsername(), accessTokenId, false);
        log.info("access token is {}", accessToken);
        String refreshTokenId = UUID.randomUUID().toString();
        String refreshToken = JwtTokenUtils.generateToken(appUser.getUsername(), refreshTokenId, true);
        log.info("refresh token is {}", refreshToken);

        TokenInfo tokenInfo = new TokenInfo(accessToken, refreshToken);
        AppUser appUser1 = userService.findById(appUser.getId());
        tokenInfo.setAppUser(appUser1);
        tokenInfo.setLocalIpAddress(ip.getHostAddress());
        tokenInfo.setUserAgentText(userAgent);
        tokenInfo.setRemoteIpAddress(request.getRemoteAddr());
        appUser1.getDeviceTokens().add(tokenInfo);
        return tokenInfoService.save(tokenInfo);
    }

    public JWTResponse refreshAccessToken(String refreshToken) {
        if (jwtTokenUtils.isTokenExpired(refreshToken)) {
            return null;
        }
        String username = jwtTokenUtils.getUsernameFromToken(refreshToken);
        TokenInfo tokenInfoOptional = tokenInfoService.findByRefreshToken(refreshToken);
        if (tokenInfoOptional == null) {
            return null;
        }
        return new JWTResponse(jwtTokenUtils.generateToken(username, UUID.randomUUID().toString(), false), refreshToken);

    }

    public void logout(String refreshToken) {
        System.out.println("Refresh token from request: " + refreshToken);
        TokenInfo tokenInfo = tokenInfoService.findByRefreshToken(refreshToken);
        if (tokenInfo != null) {
            System.out.println("Found token in DB, deleting...");
            tokenInfoService.deleteById(tokenInfo.getId());
        } else {
            System.out.println("Token not found in DB!");
        }
    }
@Transactional
    public void registerAsUser(AppUser appUser) {
        Role userRole = roleRepo.findByName("user")
                .orElseGet(() -> {
                    Role newRole = new Role(null, "user");
                    return roleRepo.save(newRole);
                });
        appUser.setRoles(Set.of(userRole));
        userService.save(appUser);
    }
    @Transactional
    public void registerAsAdmin(AppUser appUser) {
        Role adminRole = roleRepo.findByName("admin")
                .orElseGet(() -> {
                    Role newRole = new Role(null, "admin");
                    return roleRepo.save(newRole);
                });


        appUser.setRoles(Set.of(adminRole));
        userService.save(appUser);
    }


}








