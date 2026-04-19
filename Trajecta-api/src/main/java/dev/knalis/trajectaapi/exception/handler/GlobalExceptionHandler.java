package dev.knalis.trajectaapi.exception.handler;

import dev.knalis.trajectaapi.dto.common.ApiResponse;
import dev.knalis.trajectaapi.exception.*;
import io.jsonwebtoken.ExpiredJwtException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.net.UnknownHostException;

@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {

    private ResponseEntity<ApiResponse<Void>> buildResponse(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(ApiResponse.failure(status.value(), code, message));
    }
    
    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFoundException() {
        return buildResponse(HttpStatus.NOT_FOUND, ApiErrorCodes.NOT_FOUND, "The requested resource was not found.");
    }
    
    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadRequestException() {
        return buildResponse(HttpStatus.BAD_REQUEST, ApiErrorCodes.BAD_REQUEST, "The request was invalid or cannot be processed.");
    }
    
    @ExceptionHandler(FieldAlreadyExistException.class)
    public ResponseEntity<ApiResponse<Void>> handleFieldAlreadyExistException() {
        return buildResponse(HttpStatus.BAD_REQUEST, ApiErrorCodes.FIELD_ALREADY_EXISTS, "The field already exists.");
    }
    
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadCredentialsException() {
        return buildResponse(HttpStatus.UNAUTHORIZED, ApiErrorCodes.BAD_CREDENTIALS, "Invalid username or password.");
    }

    @ExceptionHandler({AuthenticationException.class, AuthenticationCredentialsNotFoundException.class})
    public ResponseEntity<ApiResponse<Void>> handleAuthenticationException() {
        return buildResponse(HttpStatus.UNAUTHORIZED, ApiErrorCodes.UNAUTHORIZED, "Authentication is required to access this resource.");
    }
    
    @ExceptionHandler(RateLimitException.class)
    public ResponseEntity<ApiResponse<Void>> handleRateLimitException() {
        return buildResponse(HttpStatus.TOO_MANY_REQUESTS, ApiErrorCodes.RATE_LIMIT, "Too many requests. Please try again later.");
    }
    
    @ExceptionHandler(PermissionDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handlePermissionDeniedException() {
        return buildResponse(HttpStatus.FORBIDDEN, ApiErrorCodes.FORBIDDEN, "You do not have permission to perform this action.");
    }
    
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDeniedException() {
        return buildResponse(HttpStatus.FORBIDDEN, ApiErrorCodes.FORBIDDEN, "You do not have permission to access this resource.");
    }
    
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotSupportedException() {
        return buildResponse(
                HttpStatus.METHOD_NOT_ALLOWED,
                ApiErrorCodes.METHOD_NOT_ALLOWED,
                "This request method is not supported for this resource. Please use a different HTTP method."
        );
    }
    
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleMessageNotReadableException() {
        return buildResponse(
                HttpStatus.BAD_REQUEST,
                ApiErrorCodes.MESSAGE_NOT_READABLE,
                "The request body could not be read or is invalid. Please check the request format."
        );
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodArgumentTypeMismatch() {
        return buildResponse(
                HttpStatus.BAD_REQUEST,
                ApiErrorCodes.ARGUMENT_TYPE_MISMATCH,
                "One or more request parameters have an invalid type."
        );
    }
    
    @ExceptionHandler(ExpiredJwtException.class)
    public ResponseEntity<ApiResponse<Void>> handleExpiredJwtException() {
        return buildResponse(
                HttpStatus.UNAUTHORIZED,
                ApiErrorCodes.TOKEN_EXPIRED,
                "Your session has expired. Please log in again."
        );
    }
    
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationExceptions() {
        return buildResponse(HttpStatus.BAD_REQUEST, ApiErrorCodes.VALIDATION_FAILED, "Validation failed for request payload.");
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleMaxUploadSizeExceededException() {
        return buildResponse(
                HttpStatus.PAYLOAD_TOO_LARGE,
                ApiErrorCodes.FILE_TOO_LARGE,
                "Uploaded file exceeds the maximum allowed size."
        );
    }
    
    @ExceptionHandler(InternalServerException.class)
    public ResponseEntity<ApiResponse<Void>> handleInternalServerException(InternalServerException ex) {
        if (hasUnknownHostCause(ex)) {
            log.warn("External dependency host is unreachable", ex.getCause());
            return buildResponse(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    ApiErrorCodes.DEPENDENCY_UNAVAILABLE,
                    "Required external service is temporarily unavailable. Please try again later."
            );
        }

        log.error("Internal server error occurred", ex.getCause());
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, ApiErrorCodes.INTERNAL_SERVER_ERROR, "Internal server error.");
    }
    
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGenericException(Exception ex) {
        log.error("Unexpected error occurred", ex);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, ApiErrorCodes.UNEXPECTED_ERROR, "An unexpected error occurred. Please try again later.");
    }

    private boolean hasUnknownHostCause(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof UnknownHostException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

}


