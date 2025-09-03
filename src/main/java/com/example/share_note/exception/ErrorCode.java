package com.example.share_note.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {
    DUPLICATE_USERNAME("USER_001", "이미 사용 중인 아이디입니다."),
    DUPLICATE_EMAIL("USER_002","이미 사용 중인 이메일입니다."),

    USER_NOT_FOUND("AUTH_001", "사용자를 찾을 수 없습니다."),
    INVALID_PASSWORD("AUTH_002", "잘못된 비밀번호입니다."),

    AUTHENTICATION_FAILED("AUTH_003", "인증에 실패했습니다."),

    INVALID_TOKEN("TOKEN_001","유효하지 않은 토큰입니다."),
    EXPIRED_TOKEN("TOKEN_002", "토큰이 만료되었습니다."),
    INVALID_SIGNATURE("TOKEN_003", "토큰 서명이 유효하지 않습니다."),

    UNEXPECTED_ERROR("COMMON_001", "예기치 않은 오류가 발생했습니다."),

    WORKSPACE_NOT_FOUND("WORKSPACE_001","워크스페이스를 찾을 수 없습니다."),
    PERMISSION_DENIED("WORKSPACE_002","권한이 없습니다."),
    INVALID_WORKSPACE_NAME("WORKSPACE_003", "워크스페이스 이름은 공백이거나 비어 있을 수 없습니다.");

    private final String code;
    private final String message;
}
