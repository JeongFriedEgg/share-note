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
    private String pageId;
    private String workspaceId;
    private LocalDateTime createdAt;
    private String createdBy;

    public static PageCreateResponseDto from(Page page) {
        return PageCreateResponseDto.builder()
                .pageId(page.getId().toString())
                .workspaceId(page.getWorkspaceId().toString())
                .createdAt(page.getCreatedAt())
                .createdBy(page.getCreatedBy().toString())
                .build();
    }
}
