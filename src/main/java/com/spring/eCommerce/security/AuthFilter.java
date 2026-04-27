package com.spring.eCommerce.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spring.eCommerce.dto.ApiResponse;
import com.spring.eCommerce.entity.TokenInfo;
import com.spring.eCommerce.service.token.TokenInfoService;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.SignatureException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

@Log4j2
@Component
@RequiredArgsConstructor
public class AuthFilter extends OncePerRequestFilter {

    private final JwtTokenUtils jwtTokenUtils;
    private final UserDetailsService userDetailsService;
    private final TokenInfoService tokenInfoService;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        Set<String> openPaths = new HashSet<>(Arrays.asList(
                "/api/auth/login",
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
        log.debug("{} {}", request.getMethod(), request.getRequestURI());
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
                        log.warn("{} {} -> token revoked or not found", request.getMethod(), request.getRequestURI());
                        writeJson(response, HttpServletResponse.SC_UNAUTHORIZED,
                                new ApiResponse("Unauthorized", HttpServletResponse.SC_UNAUTHORIZED));
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

        } catch (ExpiredJwtException | MalformedJwtException | SignatureException e) {
            log.warn("{} {} -> jwt rejected: {}", request.getMethod(), request.getRequestURI(), e.getClass().getSimpleName());
            writeJson(response, HttpServletResponse.SC_UNAUTHORIZED,
                    new ApiResponse("Unauthorized", HttpServletResponse.SC_UNAUTHORIZED));

        } catch (IllegalArgumentException e) {
            log.warn("{} {} -> invalid authorization header", request.getMethod(), request.getRequestURI());
            writeJson(response, HttpServletResponse.SC_UNAUTHORIZED,
                    new ApiResponse("Unauthorized", HttpServletResponse.SC_UNAUTHORIZED));

        } catch (Exception e) {
            log.error("{} {} -> unexpected error", request.getMethod(), request.getRequestURI(), e);
            writeJson(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    new ApiResponse("Unexpected server error", HttpServletResponse.SC_INTERNAL_SERVER_ERROR));
        }
    }

    private void writeJson(HttpServletResponse response, int status, ApiResponse body) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(new ObjectMapper().writeValueAsString(body));
    }

}
