package com.example.share_note.exception;

import com.example.share_note.exception.user.UserLoginException;
import com.example.share_note.exception.user.UserRegistrationException;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(UserRegistrationException.class)
    public ResponseEntity<ErrorResponseDto> handleUserRegistrationException(UserRegistrationException ex) {
        ErrorCode errorCode = ex.getErrorCode();
        HttpStatus httpStatus = HttpStatus.CONFLICT;

        ErrorResponseDto errorResponse = ErrorResponseDto.builder()
                .message(errorCode.getMessage())
                .status(httpStatus)
                .code(errorCode.getCode())
                .timestamp(LocalDateTime.now())
                .build();

        return new ResponseEntity<>(errorResponse, httpStatus);
    }

    @ExceptionHandler(UserLoginException.class)
    public ResponseEntity<ErrorResponseDto> handleUserLoginException(UserLoginException ex) {
        ErrorCode errorCode = ex.getErrorCode();
        HttpStatus httpStatus = HttpStatus.UNAUTHORIZED;

        ErrorResponseDto errorResponse = ErrorResponseDto.builder()
                .message(errorCode.getMessage())
                .status(httpStatus)
                .code(errorCode.getCode())
                .timestamp(LocalDateTime.now())
                .build();

        return new ResponseEntity<>(errorResponse, httpStatus);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponseDto> handleValidationExceptions(MethodArgumentNotValidException ex) {
        String errorMessage = ex.getBindingResult().getFieldError().getDefaultMessage();
        HttpStatus httpStatus = HttpStatus.BAD_REQUEST;

        ErrorResponseDto errorResponse = ErrorResponseDto.builder()
                .message(errorMessage)
                .status(httpStatus)
                .code(ErrorCode.UNEXPECTED_ERROR.getCode())
                .timestamp(LocalDateTime.now())
                .build();

        return new ResponseEntity<>(errorResponse, httpStatus);
    }

    @ExceptionHandler(WorkspaceException.class)
    public ResponseEntity<ErrorResponseDto> handleWorkspaceException(WorkspaceException ex) {
        ErrorCode errorCode = ex.getErrorCode();
        HttpStatus httpStatus;

        switch (errorCode) {
            case WORKSPACE_NOT_FOUND:
                httpStatus = HttpStatus.NOT_FOUND;
                break;
            case WORKSPACE_PERMISSION_DENIED:
            case MEMBER_NOT_FOUND:
                httpStatus = HttpStatus.FORBIDDEN;
                break;
            case INVALID_WORKSPACE_NAME:
            case AUTHENTICATION_FAILED:
                httpStatus = HttpStatus.BAD_REQUEST;
                break;
            default:
                httpStatus = HttpStatus.INTERNAL_SERVER_ERROR;
                break;
        }

        ErrorResponseDto errorResponse = ErrorResponseDto.builder()
                .message(errorCode.getMessage())
                .status(httpStatus)
                .code(errorCode.getCode())
                .timestamp(LocalDateTime.now())
                .build();

        return new ResponseEntity<>(errorResponse, httpStatus);
    }

    @ExceptionHandler(WorkspaceMemberException.class)
    public ResponseEntity<ErrorResponseDto> handleWorkspaceMemberException(WorkspaceMemberException ex) {
        ErrorCode errorCode = ex.getErrorCode();
        HttpStatus httpStatus;

        switch (errorCode) {
            case MEMBER_ALREADY_EXISTS:
                httpStatus = HttpStatus.CONFLICT;
                break;
            case MEMBER_NOT_FOUND:
                httpStatus = HttpStatus.NOT_FOUND;
                break;
            case CANNOT_CHANGE_OWNER_ROLE:
            case CANNOT_REMOVE_OWNER:
            case INVITED_USER_NOT_WORKSPACE_MEMBER:
                httpStatus = HttpStatus.FORBIDDEN;
                break;
            default:
                httpStatus = HttpStatus.INTERNAL_SERVER_ERROR;
                break;
        }

        ErrorResponseDto errorResponse = ErrorResponseDto.builder()
                .message(errorCode.getMessage())
                .status(httpStatus)
                .code(errorCode.getCode())
                .timestamp(LocalDateTime.now())
                .build();

        return new ResponseEntity<>(errorResponse, httpStatus);
    }

    @ExceptionHandler(PageException.class)
    public ResponseEntity<ErrorResponseDto> handlePageException(PageException ex) {
        ErrorCode errorCode = ex.getErrorCode();
        HttpStatus httpStatus;

        switch (errorCode) {
            case PAGE_NOT_FOUND:
            case PARENT_PAGE_NOT_FOUND:
            case PAGE_PERMISSION_NOT_FOUND:
                httpStatus = HttpStatus.NOT_FOUND;
                break;
            case PAGE_PERMISSION_DENIED:
            case PARENT_PAGE_PERMISSION_DENIED:
            case CANNOT_CHANGE_OWNER_PERMISSION:
                httpStatus = HttpStatus.FORBIDDEN;
                break;
            case INVITED_USER_NOT_WORKSPACE_MEMBER:
                httpStatus = HttpStatus.BAD_REQUEST;
                break;
            case AUTHENTICATION_FAILED:
                httpStatus = HttpStatus.UNAUTHORIZED;
                break;
            default:
                httpStatus = HttpStatus.INTERNAL_SERVER_ERROR;
                break;
        }

        ErrorResponseDto errorResponse = ErrorResponseDto.builder()
                .message(errorCode.getMessage())
                .status(httpStatus)
                .code(errorCode.getCode())
                .timestamp(LocalDateTime.now())
                .build();

        return new ResponseEntity<>(errorResponse, httpStatus);
    }

    // JWT 토큰 관련 예외 처리
    @ExceptionHandler({JwtException.class, MalformedJwtException.class, ExpiredJwtException.class,
            UnsupportedJwtException.class, IllegalArgumentException.class})
    public ResponseEntity<ErrorResponseDto> handleJwtException(Exception ex) {
        ErrorCode errorCode;
        HttpStatus httpStatus = HttpStatus.UNAUTHORIZED;

        if (ex instanceof ExpiredJwtException) {
            errorCode = ErrorCode.EXPIRED_TOKEN;
        } else if (ex instanceof MalformedJwtException || ex instanceof UnsupportedJwtException) {
            errorCode = ErrorCode.INVALID_TOKEN;
        } else if (ex instanceof SecurityException) {
            errorCode = ErrorCode.INVALID_SIGNATURE;
        } else {
            errorCode = ErrorCode.INVALID_TOKEN;
        }

        ErrorResponseDto errorResponse = ErrorResponseDto.builder()
                .message(errorCode.getMessage())
                .status(httpStatus)
                .code(errorCode.getCode())
                .timestamp(LocalDateTime.now())
                .build();

        return new ResponseEntity<>(errorResponse, httpStatus);
    }

    // 일반적인 런타임 예외 처리
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ErrorResponseDto> handleRuntimeException(RuntimeException ex) {
        ErrorCode errorCode = ErrorCode.UNEXPECTED_ERROR;
        HttpStatus httpStatus = HttpStatus.INTERNAL_SERVER_ERROR;

        ErrorResponseDto errorResponse = ErrorResponseDto.builder()
                .message(errorCode.getMessage())
                .status(httpStatus)
                .code(errorCode.getCode())
                .timestamp(LocalDateTime.now())
                .build();

        // 로그에 실제 예외 정보 기록 (운영에서 디버깅 용도)
        // log.error("Unexpected error occurred", ex);

        return new ResponseEntity<>(errorResponse, httpStatus);
    }

    // 모든 예외를 처리하는 최종 핸들러
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponseDto> handleGeneralException(Exception ex) {
        ErrorCode errorCode = ErrorCode.UNEXPECTED_ERROR;
        HttpStatus httpStatus = HttpStatus.INTERNAL_SERVER_ERROR;

        ErrorResponseDto errorResponse = ErrorResponseDto.builder()
                .message(errorCode.getMessage())
                .status(httpStatus)
                .code(errorCode.getCode())
                .timestamp(LocalDateTime.now())
                .build();

        // 로그에 실제 예외 정보 기록 (운영에서 디버깅 용도)
        // log.error("General exception occurred", ex);

        return new ResponseEntity<>(errorResponse, httpStatus);
    }
}
