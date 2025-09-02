package com.example.share_note.exception.user;

import com.example.share_note.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class UserLoginException extends RuntimeException {
    private final ErrorCode errorCode;
}
