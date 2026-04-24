package com.spring.eCommerce.security;

import com.spring.eCommerce.entity.TokenInfo;
import com.spring.eCommerce.service.token.TokenInfoService;
import com.spring.eCommerce.service.user.UserService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.*;

@Log4j2
@Component
public class AuthFilter extends OncePerRequestFilter {
    @Autowired
    private UserService userService;
    @Autowired
    private JwtTokenUtils jwtTokenUtils;
    @Autowired
    private UserDetailsService userDetailsService;
    @Autowired
    private TokenInfoService tokenInfoService;
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        Set<String> openPaths = new HashSet<>(Arrays.asList(
                "/api/auth/login",
                "/api/auth/logout",
                "/api/auth/refresh",
                "/api/auth/registerUser",
                "/swagger-ui",
                "/v3/api-docs",
                "/swagger-resources",
                "/webjars"
        ));

        String path = request.getRequestURI();

        if (openPaths.stream().anyMatch(path::startsWith)) {
            filterChain.doFilter(request, response);
            return;
        }

        final String jwtTokenHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        log.info("path is->> " + request.getRequestURI());
        final SecurityContext securityContext = SecurityContextHolder.getContext();

        try {
            if (jwtTokenHeader != null && securityContext.getAuthentication() == null) {
                if (!jwtTokenHeader.startsWith("Bearer ")) {
                    throw new IllegalArgumentException("Authorization header must start with 'Bearer '");
                }

                String jwtToken = jwtTokenHeader.substring("Bearer ".length());

                if (jwtTokenUtils.validateToken(jwtToken)) {
                    TokenInfo tokenInfo = tokenInfoService.findByAccessToken(jwtToken);
                    if (tokenInfo == null) {
                        log.warn("Token not found in DB, possibly logged out. Rejecting request.");
                        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                        response.setContentType("application/json");
                        Map<String, String> errorResponse = new HashMap<>();
                        errorResponse.put("error", "InvalidToken");
                        errorResponse.put("message", "Token not found or revoked.");
                        response.getWriter().write(
                                new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(errorResponse)
                        );
                        return;
                    }
                    String username = jwtTokenUtils.getUsernameFromToken(jwtToken);
                    if (username != null) {
                        AppUserDetail appUserDetail = (AppUserDetail) userDetailsService.loadUserByUsername(username);
                        if (jwtTokenUtils.isTokenValid(jwtToken, appUserDetail)) {
                            UsernamePasswordAuthenticationToken passwordAuthenticationToken =
                                    new UsernamePasswordAuthenticationToken(appUserDetail, null, appUserDetail.getAuthorities());
                            passwordAuthenticationToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                            SecurityContextHolder.getContext().setAuthentication(passwordAuthenticationToken);
                        }
                    }
                }
            }

            filterChain.doFilter(request, response);

        } catch (io.jsonwebtoken.ExpiredJwtException | io.jsonwebtoken.MalformedJwtException |
                 io.jsonwebtoken.SignatureException | IllegalArgumentException e) {

            log.error("JWT Error: {}", e.getMessage());
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getClass().getSimpleName());
            errorResponse.put("message", e.getMessage());
            response.getWriter().write(
                    new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(errorResponse)
            );

        } catch (Exception e) {
            log.error("Unexpected error: {}", e.getMessage());
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.setContentType("application/json");
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", "Server Error");
            errorResponse.put("message", e.getMessage());
            response.getWriter().write(
                    new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(errorResponse)
            );
        }
    }

}
