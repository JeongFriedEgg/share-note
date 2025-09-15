package com.example.share_note.dto.page;

import com.example.share_note.domain.Page;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PageStatusResponseDto {
    private String pageId;
    private Boolean isArchived;

    public static PageStatusResponseDto from(Page page) {
        return PageStatusResponseDto.builder()
                .pageId(page.getId().toString())
                .isArchived(page.isArchived())
                .build();
    }
}
