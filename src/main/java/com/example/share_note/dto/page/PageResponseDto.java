package com.example.share_note.dto.page;

import com.example.share_note.domain.Page;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PageResponseDto {
    private Long id;
    private Long workspaceId;
    private Long parentPageId;
    private String title;
    private String icon;
    private String cover;
    private String properties;
    private Boolean isPublic;
    private Boolean isArchived;
    private Boolean isTemplate;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long createdBy;
    private Long lastEditedBy;

    public static PageResponseDto from(Page page) {
        return PageResponseDto.builder()
                .id(page.getId())
                .workspaceId(page.getWorkspaceId())
                .parentPageId(page.getParentPageId())
                .title(page.getTitle())
                .icon(page.getIcon())
                .cover(page.getCover())
                .properties(page.getProperties())
                .isPublic(page.isPublic())
                .isArchived(page.isArchived())
                .isTemplate(page.isTemplate())
                .createdAt(page.getCreatedAt())
                .updatedAt(page.getUpdatedAt())
                .createdBy(page.getCreatedBy())
                .lastEditedBy(page.getLastEditedBy())
                .build();
    }
}
