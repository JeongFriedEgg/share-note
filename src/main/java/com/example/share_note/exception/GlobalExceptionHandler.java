package com.example.share_note.exception;

import com.example.share_note.exception.user.UserLoginException;
import com.example.share_note.exception.user.UserRegistrationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(UserRegistrationException.class)
    public ResponseEntity<ErrorResponseDto> handleUserRegistrationException(UserRegistrationException ex) {
        ErrorCode errorCode = ex.getErrorCode();
        HttpStatus httpStatus = HttpStatus.BAD_REQUEST;

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
}
