package com.example.share_note.exception;

import com.example.share_note.exception.user.UserLoginException;
import com.example.share_note.exception.user.UserRegistrationException;
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

        if (errorCode == ErrorCode.WORKSPACE_NOT_FOUND) {
            httpStatus = HttpStatus.NOT_FOUND;
        } else if (errorCode == ErrorCode.PERMISSION_DENIED) {
            httpStatus = HttpStatus.FORBIDDEN;
        } else if (errorCode == ErrorCode.INVALID_WORKSPACE_NAME) {
            httpStatus = HttpStatus.BAD_REQUEST;  // 400 Bad Request
        } else {
            httpStatus = HttpStatus.INTERNAL_SERVER_ERROR;
        }

        ErrorResponseDto errorResponse = ErrorResponseDto.builder()
                .message(errorCode.getMessage())
                .status(httpStatus)
                .code(errorCode.getCode())
                .timestamp(LocalDateTime.now())
                .build();

        return new ResponseEntity<>(errorResponse, httpStatus);
    }
}
