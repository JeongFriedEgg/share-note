package com.example.share_note.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;

@Getter
public class AuthenticationFailedException extends BadCredentialsException {
    private final ErrorCode errorCode;

    public AuthenticationFailedException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }
}
