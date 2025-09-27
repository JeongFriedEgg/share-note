package com.sharenote.redistribution.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum MigrationStatus {
    READY("준비"),
    MIGRATING("마이그레이션 중"),
    MIGRATED("마이그레이션 완료"),
    FAILED("실패");

    private final String description;
}
