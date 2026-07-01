package com.happyhearts.exception;

import com.happyhearts.dto.ApiResponse;
import com.happyhearts.exception.RfidCardNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@RestControllerAdvice
@RequiredArgsConstructor
@Slf4j
public class GlobalExceptionHandler {

    private final MessageSource messageSource;

    @ExceptionHandler(RfidCardNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleRfidNotFound(RfidCardNotFoundException ex, WebRequest request) {
        Locale locale = request.getLocale();
        String msg = messageSource.getMessage(ex.getMessageKey(), ex.getArgs(), ex.getMessageKey(), locale);
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.fail(msg));
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException ex, WebRequest request) {
        Locale locale = request.getLocale();
        String msg = messageSource.getMessage(ex.getMessageKey(), ex.getArgs(), ex.getMessageKey(), locale);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.fail(msg));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(ResourceNotFoundException ex, WebRequest request) {
        Locale locale = request.getLocale();
        String msg = messageSource.getMessage(
                ex.getMessageKey(),
                ex.getArgs(),
                ex.getMessageKey(),
                locale
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.fail(msg));
    }

    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleSpringAccessDenied(
            org.springframework.security.access.AccessDeniedException ex,
            WebRequest request
    ) {
        Locale locale = request.getLocale();
        String msg = messageSource.getMessage("error.access.denied", null, locale);
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.fail(msg));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAppAccessDenied(AccessDeniedException ex, WebRequest request) {
        Locale locale = request.getLocale();
        String msg = messageSource.getMessage(ex.getMessageKey(), ex.getArgs(), locale);
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.fail(msg));
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadCredentials(BadCredentialsException ex, WebRequest request) {
        Locale locale = request.getLocale();
        String msg = messageSource.getMessage("error.auth.invalid", null, locale);
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.fail(msg));
    }

    @ExceptionHandler(DisabledException.class)
    public ResponseEntity<ApiResponse<Void>> handleDisabled(DisabledException ex, WebRequest request) {
        Locale locale = request.getLocale();
        String msg = messageSource.getMessage("error.auth.account.disabled", null, locale);
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.fail(msg));
    }

    @ExceptionHandler(LockedException.class)
    public ResponseEntity<ApiResponse<Void>> handleLocked(LockedException ex, WebRequest request) {
        Locale locale = request.getLocale();
        String msg = messageSource.getMessage("error.auth.account.locked", null, locale);
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.fail(msg));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadable(HttpMessageNotReadableException ex, WebRequest request) {
        log.warn("Malformed JSON request: {}", ex.getMessage());
        Locale locale = request.getLocale();
        String msg = messageSource.getMessage("error.request.invalid", null, "Invalid request body.", locale);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.fail(msg));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex, WebRequest request) {
        Locale locale = request.getLocale();
        Map<String, String> errors = new LinkedHashMap<>();
        int globalIdx = 0;
        for (ObjectError error : ex.getBindingResult().getAllErrors()) {
            String text;
            try {
                text = messageSource.getMessage(error, locale);
            } catch (Exception e) {
                text = error.getDefaultMessage() != null ? error.getDefaultMessage() : error.toString();
            }
            if (error instanceof FieldError fe) {
                errors.put(fe.getField(), text);
            } else {
                String key = globalIdx == 0 ? "_global" : "_global[" + globalIdx + "]";
                globalIdx++;
                errors.put(key, text);
            }
        }
        String headline = messageSource.getMessage("error.validation", null, locale);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.failWithErrors(headline, errors));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneric(Exception ex, WebRequest request) {
        log.error("Unhandled error", ex);
        Locale locale = request.getLocale();
        String msg = messageSource.getMessage("error.internal", null, locale);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.fail(msg));
    }
}
