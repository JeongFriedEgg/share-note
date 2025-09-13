package com.example.share_note.dto.block;


import lombok.*;

@Getter
@Builder
public class BlockMoveRequestDto {
    private String newParentBlockId;
    private Integer newPosition;
}
