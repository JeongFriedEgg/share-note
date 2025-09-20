package com.sharenote.redistribution.util;

import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class ValidationUtils {
    public static boolean isValidUUID(String uuid) {
        try {
            UUID.fromString(uuid);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public static void validatePageId(UUID pageId) {
        if (pageId == null) {
            throw new IllegalArgumentException("페이지 ID는 필수입니다.");
        }
    }
}