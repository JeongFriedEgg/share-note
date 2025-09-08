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
    private Long id;
    private Long pageId;
    private Long parentBlockId;
    private String type;
    private String content;
    private Integer position;
    private boolean isArchived;
    private LocalDateTime createdAt;
    private Long createdBy;

    public static BlockCreateResponseDto from(Block block) {
        return BlockCreateResponseDto.builder()
                .id(block.getId())
                .pageId(block.getPageId())
                .parentBlockId(block.getParentBlockId())
                .type(block.getType())
                .content(block.getContent())
                .position(block.getPosition())
                .isArchived(block.isArchived())
                .createdAt(block.getCreatedAt())
                .createdBy(block.getCreatedBy())
                .build();
    }
}
