package com.example.share_note.dto.page;

import com.example.share_note.domain.Page;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PageCreateResponseDto {
    private Long pageId;
    private Long workspaceId;
    private LocalDateTime createdAt;
    private Long createdBy;

    public static PageCreateResponseDto from(Page page) {
        return PageCreateResponseDto.builder()
                .pageId(page.getId())
                .workspaceId(page.getWorkspaceId())
                .createdAt(page.getCreatedAt())
                .createdBy(page.getCreatedBy())
                .build();
    }
}
