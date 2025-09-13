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
    private String id;
    private String workspaceId;
    private String parentPageId;
    private String title;
    private String icon;
    private String cover;
    private String properties;
    private Boolean isPublic;
    private Boolean isArchived;
    private Boolean isTemplate;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdBy;
    private String lastEditedBy;

    public static PageResponseDto from(Page page) {
        return PageResponseDto.builder()
                .id(page.getId().toString())
                .workspaceId(page.getWorkspaceId().toString())
                .parentPageId(page.getParentPageId() != null ? page.getParentPageId().toString() : null)
                .title(page.getTitle())
                .icon(page.getIcon())
                .cover(page.getCover())
                .properties(page.getProperties())
                .isPublic(page.isPublic())
                .isArchived(page.isArchived())
                .isTemplate(page.isTemplate())
                .createdAt(page.getCreatedAt())
                .updatedAt(page.getUpdatedAt())
                .createdBy(page.getCreatedBy().toString())
                .lastEditedBy(page.getLastEditedBy().toString())
                .build();
    }
}
