package com.example.share_note.dto.page;

import com.example.share_note.domain.Page;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PageStatusResponseDto {
    private Long pageId;
    private Boolean isArchived;

    public static PageStatusResponseDto from(Page page) {
        return PageStatusResponseDto.builder()
                .pageId(page.getId())
                .isArchived(page.isArchived())
                .build();
    }
}
