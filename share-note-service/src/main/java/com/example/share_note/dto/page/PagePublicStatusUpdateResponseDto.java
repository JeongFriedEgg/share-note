package com.example.share_note.dto.page;

import com.example.share_note.domain.Page;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PagePublicStatusUpdateResponseDto {
    private String pageId;
    private Boolean isPublic;

    public static PagePublicStatusUpdateResponseDto from(Page page) {
        return PagePublicStatusUpdateResponseDto.builder()
                .pageId(page.getId().toString())
                .isPublic(page.isPublic())
                .build();
    }
}
