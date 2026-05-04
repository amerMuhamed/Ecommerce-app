package com.spring.eCommerce.service.authentication;

import com.spring.eCommerce.dto.JWTResponse;
import com.spring.eCommerce.entity.AppUser;
import com.spring.eCommerce.entity.Role;
import com.spring.eCommerce.entity.TokenInfo;
import com.spring.eCommerce.exception.TokenException;
import com.spring.eCommerce.repository.RoleRepo;
import com.spring.eCommerce.security.AppUserDetail;
import com.spring.eCommerce.security.JwtTokenUtils;
import com.spring.eCommerce.service.token.TokenInfoService;
import com.spring.eCommerce.service.user.UserService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.UUID;


@Service
@RequiredArgsConstructor
@Log4j2

public class AuthService {

    private final AuthenticationManager auth;
    private final HttpServletRequest request;
    private final JwtTokenUtils jwtTokenUtils;
    private final TokenInfoService tokenInfoService;
    private final UserService userService;
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
        String accessToken = jwtTokenUtils.generateToken(appUser.getUsername(), accessTokenId, false);
        String refreshTokenId = UUID.randomUUID().toString();
        String refreshToken = jwtTokenUtils.generateToken(appUser.getUsername(), refreshTokenId, true);
        TokenInfo tokenInfo = new TokenInfo(accessToken, refreshToken);
        AppUser appUser1 = userService.findById(appUser.getId());
        tokenInfo.setAppUser(appUser1);
        tokenInfo.setLocalIpAddress(ip != null ? ip.getHostAddress() : "unknown");
        tokenInfo.setUserAgentText(userAgent);
        tokenInfo.setRemoteIpAddress(request.getRemoteAddr());
        appUser1.getDeviceTokens().add(tokenInfo);
        return tokenInfoService.save(tokenInfo);
    }

    @Transactional
    public JWTResponse refreshAccessToken(String refreshToken) {

        if (!jwtTokenUtils.validateToken(refreshToken)) {
            log.warn("Invalid refresh token provided");
            throw new RuntimeException("Invalid token");
        }
        if (jwtTokenUtils.isTokenExpired(refreshToken)) {
            log.warn("Refresh token expired");
            throw new TokenException("Refresh token expired");
        }
        if (!jwtTokenUtils.isRefreshToken(refreshToken)) {
            log.warn("Token is not a refresh token type");
            throw new RuntimeException("Invalid token type");
        }
        TokenInfo oldTokenInfo = tokenInfoService.findByRefreshToken(refreshToken);
        if (oldTokenInfo == null) {
            log.warn("Refresh token not found in database");
            throw new RuntimeException("Refresh token not found");
        }
        String username = jwtTokenUtils.getUsernameFromToken(refreshToken);

        String newAccessToken = jwtTokenUtils.generateToken(username, UUID.randomUUID().toString(), false);
        String newRefreshToken = jwtTokenUtils.generateToken(username, UUID.randomUUID().toString(), true);

        oldTokenInfo.setAccessToken(newAccessToken);
        oldTokenInfo.setRefreshToken(newRefreshToken);
        tokenInfoService.save(oldTokenInfo);

        log.info("Access token refreshed for user: {}", username);

        // Cleanup expired tokens after refresh
        tokenInfoService.deleteExpiredTokens();

        return new JWTResponse(newAccessToken, newRefreshToken);
    }

    @Transactional
    public void logout(String accessToken) {
        TokenInfo tokenInfo = tokenInfoService.findByAccessToken(accessToken);
        if (tokenInfo != null) {
            String username = jwtTokenUtils.getUsernameFromToken(accessToken);
            tokenInfoService.deleteById(tokenInfo.getId());
            log.info("User {} logged out successfully", username);
        } else {
            log.warn("Access token not found in database");
            throw new TokenException("Token revocation failed");
        }

        // Cleanup expired tokens after logout
        tokenInfoService.deleteExpiredTokens();
    }

    @Transactional
    public void registerAsUser(AppUser appUser) {
        Role userRole = roleRepo.findByName("user")
                .orElseGet(() -> {
                    Role newRole = new Role(null, "user");
                    return roleRepo.save(newRole);
                });
        appUser.setRoles(Collections.singleton(userRole));
        userService.save(appUser);
    }

    @Transactional
    public void registerAsAdmin(AppUser appUser) {
        Role adminRole = roleRepo.findByName("admin")
                .orElseGet(() -> {
                    Role newRole = new Role(null, "admin");
                    return roleRepo.save(newRole);
                });

        appUser.setRoles(Collections.singleton(adminRole));
        userService.save(appUser);
    }

}








