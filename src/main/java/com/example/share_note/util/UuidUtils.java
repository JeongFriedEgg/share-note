package com.example.share_note.util;

import com.example.share_note.exception.ErrorCode;
import com.example.share_note.exception.UuidException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class UuidUtils {

    public UUID generate() {
        try {
            return UUID.randomUUID();
        } catch (Exception e) {
            log.error("UUID generation failed: {}", e.getMessage(), e);
            throw new UuidException(ErrorCode.UUID_GENERATE_FAIL);
        }
    }

    public UUID fromString(String uuidString) {
        if (uuidString == null || uuidString.trim().isEmpty()) {
            log.error("UUID string is null or empty");
            throw new UuidException(ErrorCode.UUID_INVALID_FORMAT);
        }

        try {
            return UUID.fromString(uuidString.trim());
        } catch (IllegalArgumentException e) {
            log.error("Transform UUID string to UUID failed - Invalid format: {}", uuidString, e);
            throw new UuidException(ErrorCode.UUID_PARSE_FAIL);
        }
    }

    public String fromUUID(UUID uuid) {
        if (uuid == null) {
            log.error("UUID is null");
            throw new UuidException(ErrorCode.UUID_INVALID_FORMAT);
        }

        try {
            return uuid.toString();
        } catch (Exception e) {
            log.error("Transform UUID to string failed: uuid={}, error={}", uuid, e.getMessage(), e);
            throw new UuidException(ErrorCode.UUID_PARSE_FAIL);
        }
    }
}
