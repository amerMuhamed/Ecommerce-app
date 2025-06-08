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
        String path = request.getRequestURI();
        if (path.startsWith("/api/auth")
                || path.startsWith("/swagger-ui")
                || path.startsWith("/v3/api-docs")
                || path.startsWith("/swagger-resources")
                || path.startsWith("/webjars")) {
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
                        response.getWriter().write(
                                new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(
                                        java.util.Map.of(
                                                "error", "InvalidToken",
                                                "message", "Token not found or revoked."
                                        )
                                )
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
            response.getWriter().write(
                    new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(
                            java.util.Map.of(
                                    "error", e.getClass().getSimpleName(),
                                    "message", e.getMessage()
                            )
                    )
            );

        } catch (Exception e) {
            log.error("Unexpected error: {}", e.getMessage());
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.setContentType("application/json");
            response.getWriter().write(
                    new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(
                            java.util.Map.of(
                                    "error", "Server Error",
                                    "message", e.getMessage()
                            )
                    )
            );
        }
    }

}
