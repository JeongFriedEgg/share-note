package com.example.share_note.dto.block;

import lombok.*;

@Getter
@Builder
public class BlockCreateRequestDto {
    private Long pageId;
    private Long parentBlockId;
    private String type;
    private String content;
    private Integer position;
}

