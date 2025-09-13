package com.example.share_note.exception;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler({
            UserException.class,
            WorkspaceException.class,
            WorkspaceMemberException.class,
            PageException.class,
            PagePermissionException.class,
            BlockException.class,
            UuidException.class
    })
    public ResponseEntity<ErrorResponseDto> handleCustomException(ApiException ex) {
        ErrorCode errorCode = ex.getErrorCode();
        HttpStatus httpStatus = errorCode.getHttpStatus();

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
        ErrorCode errorCode = ErrorCode.UNEXPECTED_ERROR;

        ErrorResponseDto errorResponse = ErrorResponseDto.builder()
                .message(errorMessage)
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

        if (ex instanceof ExpiredJwtException) {
            errorCode = ErrorCode.EXPIRED_TOKEN;
        } else if (ex instanceof MalformedJwtException || ex instanceof UnsupportedJwtException) {
            errorCode = ErrorCode.INVALID_TOKEN;
        } else if (ex instanceof SecurityException) {
            errorCode = ErrorCode.INVALID_SIGNATURE;
        } else {
            errorCode = ErrorCode.INVALID_TOKEN;
        }

        HttpStatus httpStatus = errorCode.getHttpStatus();

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
        log.error("Unhandled RuntimeException: ", ex);
        ErrorCode errorCode = ErrorCode.UNEXPECTED_ERROR;
        HttpStatus httpStatus = HttpStatus.INTERNAL_SERVER_ERROR;

        ErrorResponseDto errorResponse = ErrorResponseDto.builder()
                .message(errorCode.getMessage())
                .status(httpStatus)
                .code(errorCode.getCode())
                .timestamp(LocalDateTime.now())
                .build();


        return new ResponseEntity<>(errorResponse, httpStatus);
    }

    // 모든 예외를 처리하는 최종 핸들러
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponseDto> handleGeneralException(Exception ex) {
        log.error("Unhandled Exception: ", ex);
        ErrorCode errorCode = ErrorCode.UNEXPECTED_ERROR;
        HttpStatus httpStatus = HttpStatus.INTERNAL_SERVER_ERROR;

        ErrorResponseDto errorResponse = ErrorResponseDto.builder()
                .message(errorCode.getMessage())
                .status(httpStatus)
                .code(errorCode.getCode())
                .timestamp(LocalDateTime.now())
                .build();


        return new ResponseEntity<>(errorResponse, httpStatus);
    }
}
