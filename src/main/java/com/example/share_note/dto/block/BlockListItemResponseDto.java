package com.example.share_note.dto.block;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class BlockListItemResponseDto {
    private Long blockId;
    private Long parentBlockId;
    private String type;
    private Integer position;
}
