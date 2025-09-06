package com.example.share_note.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PagePermissionType {
    READ(1),
    COMMENT(2),
    EDIT(3),
    FULL_ACCESS(4);

    private final int level;
}
