package com.example.share_note.dto.block;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class BlockListResponseDto {
    private List<BlockListItemResponseDto> blocks;
}