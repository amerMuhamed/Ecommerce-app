package com.spring.eCommerce.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spring.eCommerce.dto.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.Serializable;

@Log4j2
@Component
@RequiredArgsConstructor
public class JwtUnAuthResponse implements AuthenticationEntryPoint, Serializable {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException ex) throws IOException {
        log.warn("{} {} -> unauthorized: {}", request.getMethod(), request.getRequestURI(), ex.getClass().getSimpleName());

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        ApiResponse body = new ApiResponse("Unauthorized", HttpServletResponse.SC_UNAUTHORIZED);
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
