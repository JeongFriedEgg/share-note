package com.example.share_note.dto.page;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PagePublicStatusUpdateRequestDto {
    private Boolean isPublic;
}
