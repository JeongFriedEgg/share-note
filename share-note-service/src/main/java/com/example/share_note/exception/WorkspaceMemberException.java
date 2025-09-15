package com.example.share_note.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class WorkspaceMemberException extends RuntimeException implements ApiException {
    private final ErrorCode errorCode;
}
