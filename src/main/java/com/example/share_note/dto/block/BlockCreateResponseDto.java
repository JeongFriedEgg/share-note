package com.example.share_note.dto.block;

import com.example.share_note.domain.Block;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BlockCreateResponseDto {
    private String id;
    private String pageId;
    private String parentBlockId;
    private String type;
    private String content;
    private Integer position;
    private boolean isArchived;
    private LocalDateTime createdAt;
    private String createdBy;

    public static BlockCreateResponseDto from(Block block) {
        return BlockCreateResponseDto.builder()
                .id(block.getId().toString())
                .pageId(block.getPageId().toString())
                .parentBlockId(block.getParentBlockId() != null ? block.getParentBlockId().toString() : null)
                .type(block.getType())
                .content(block.getContent())
                .position(block.getPosition())
                .isArchived(block.isArchived())
                .createdAt(block.getCreatedAt())
                .createdBy(block.getCreatedBy().toString())
                .build();
    }
}
