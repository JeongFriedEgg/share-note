package com.example.share_note.dto.block;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BlockStatusResponseDto {
    private Long blockId;
    private Boolean isArchived;
}