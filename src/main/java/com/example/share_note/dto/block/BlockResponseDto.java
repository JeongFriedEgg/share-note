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
    private Long id;
    private Long pageId;
    private Long parentBlockId;
    private String type;
    private String content;
    private Integer position;
    private boolean isArchived;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long createdBy;
    private Long lastEditedBy;

    public static BlockResponseDto from(Block block) {
        return BlockResponseDto.builder()
                .id(block.getId())
                .pageId(block.getPageId())
                .parentBlockId(block.getParentBlockId())
                .type(block.getType())
                .content(block.getContent())
                .position(block.getPosition())
                .isArchived(block.isArchived())
                .createdAt(block.getCreatedAt())
                .updatedAt(block.getUpdatedAt())
                .createdBy(block.getCreatedBy())
                .lastEditedBy(block.getLastEditedBy())
                .build();
    }
}
