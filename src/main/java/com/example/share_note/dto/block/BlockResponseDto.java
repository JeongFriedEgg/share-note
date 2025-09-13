package com.example.share_note.dto.block;

import com.example.share_note.domain.Block;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BlockResponseDto {
    private String id;
    private String pageId;
    private String parentBlockId;
    private String type;
    private String content;
    private Integer position;
    private boolean isArchived;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdBy;
    private String lastEditedBy;

    public static BlockResponseDto from(Block block) {
        return BlockResponseDto.builder()
                .id(block.getId().toString())
                .pageId(block.getPageId().toString())
                .parentBlockId(block.getParentBlockId() != null ? block.getParentBlockId().toString() : null)
                .type(block.getType())
                .content(block.getContent())
                .position(block.getPosition())
                .isArchived(block.isArchived())
                .createdAt(block.getCreatedAt())
                .updatedAt(block.getUpdatedAt())
                .createdBy(block.getCreatedBy().toString())
                .lastEditedBy(block.getLastEditedBy().toString())
                .build();
    }
}
