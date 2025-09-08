package com.example.share_note.dto.block;


import lombok.*;

@Getter
@Builder
public class BlockMoveRequestDto {
    private Long newParentBlockId;
    private Integer newPosition;
}
