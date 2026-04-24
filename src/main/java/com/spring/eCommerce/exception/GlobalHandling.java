package com.spring.eCommerce.exception;

import com.spring.eCommerce.dto.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.log4j.Log4j2;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.stream.Collectors;

@Log4j2
@ControllerAdvice
public class GlobalHandling {

    private static ResponseEntity<Object> build(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(new ApiResponse(message, status.value()));
    }

    private static String req(HttpServletRequest request) {
        if (request == null) return "";
        return request.getMethod() + " " + request.getRequestURI()
                + " IP=" + request.getRemoteAddr();
    }

    // ---------- Security / Auth ----------

    @ExceptionHandler(DisabledException.class)
    public ResponseEntity<Object> handleDisabledException(DisabledException ex, HttpServletRequest request) {
        log.warn("{} -> disabled user", req(request));
        return build(HttpStatus.FORBIDDEN, "This user is disabled");
    }

    @ExceptionHandler(LockedException.class)
    public ResponseEntity<Object> handleLockedException(LockedException ex, HttpServletRequest request) {
        log.warn("{} -> locked user", req(request));
        return build(HttpStatus.LOCKED, "This user is locked");
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<Object> handleBadCredentialsException(BadCredentialsException ex, HttpServletRequest request) {
        log.warn("{} -> bad credentials", req(request));
        return build(HttpStatus.UNAUTHORIZED, "Invalid credentials");
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Object> handleAuthenticationException(AuthenticationException ex, HttpServletRequest request) {
        log.warn("{} -> authentication failed: {}", req(request), ex.getClass().getSimpleName());
        return build(HttpStatus.UNAUTHORIZED, "Unauthorized");
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Object> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        log.warn("{} -> access denied", req(request));
        return build(HttpStatus.FORBIDDEN, "You do not have permission to access this resource");
    }

    @ExceptionHandler(TokenException.class)
    public ResponseEntity<Object> handleToken(TokenException ex, HttpServletRequest request) {
        return build(HttpStatus.UNAUTHORIZED, ex.getMessage());
    }

    // ---------- Validation / Bad request ----------

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex, HttpServletRequest request) {
        String details = ex.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .collect(Collectors.joining(", "));
        log.warn("{} -> validation failed: {}", req(request), details);
        return build(HttpStatus.BAD_REQUEST, details.trim().isEmpty() ? "Validation failed" : details);
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<Object> handleBindException(BindException ex, HttpServletRequest request) {
        String details = ex.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .collect(Collectors.joining(", "));
        log.warn("{} -> bind/validation failed: {}", req(request), details);
        return build(HttpStatus.BAD_REQUEST, details.trim().isEmpty() ? "Validation failed" : details);
    }

    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class,
            IllegalArgumentException.class
    })
    public ResponseEntity<Object> handleBadRequest(Exception ex, HttpServletRequest request) {
        log.warn("{} -> bad request: {}", req(request), ex.getMessage());
        return build(HttpStatus.BAD_REQUEST, "Invalid request");
    }

    // ---------- Business / persistence ----------

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Object> handleIllegalState(IllegalStateException ex, HttpServletRequest request) {
        log.warn("{} -> business rule: {}", req(request), ex.getMessage());
        return build(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Object> handleDataIntegrity(DataIntegrityViolationException ex, HttpServletRequest request) {
        log.warn("{} -> data integrity violation: {}", req(request), ex.getMostSpecificCause() != null ? ex.getMostSpecificCause().getMessage() : ex.getMessage());
        return build(HttpStatus.CONFLICT, "Conflict: data already exists or violates constraints");
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Object> handleRuntime(RuntimeException ex, HttpServletRequest request) {
        // Keep this AFTER more specific handlers.
        log.error("{} -> runtime exception: {}", req(request), ex.getMessage(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected runtime error");
    }

    // ---------- Catch-all ----------

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleGeneralException(Exception ex, HttpServletRequest request) {
        log.error("{} -> unexpected server error: {}", req(request), ex.getMessage(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected server error");
    }
}
