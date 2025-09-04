package com.example.share_note.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class WorkspaceMemberException extends RuntimeException {
    private final ErrorCode errorCode;
}
