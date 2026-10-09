package com.spring.eCommerce.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spring.eCommerce.dto.api.ApiResponse;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Log4j2
@Component
@RequiredArgsConstructor
public class CustomAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    private static boolean acceptsHtml(HttpServletRequest request) {
        String accept = request.getHeader("Accept");
        return accept != null && accept.contains("text/html");
    }

    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException, ServletException {
        log.warn("{} {} -> access denied", request.getMethod(), request.getRequestURI());

        // Browser navigation to protected pages (e.g. /admin/** as customer) shows
        // the friendly 403 page; API clients keep the JSON 403 contract.
        if (!request.getRequestURI().startsWith("/api/") && acceptsHtml(request)) {
            response.sendRedirect(request.getContextPath() + "/error?forbidden");
            return;
        }

        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        ApiResponse<?> body = new ApiResponse<>(
                false,
                "You do not have permission to access this resource",
                null
        );

        response.getWriter().write(objectMapper.writeValueAsString(body));
        response.getWriter().flush();
    }
}