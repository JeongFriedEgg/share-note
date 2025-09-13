package com.example.share_note.dto.block;

import lombok.*;

@Getter
@Builder
public class BlockCreateRequestDto {
    private String pageId;
    private String parentBlockId;
    private String type;
    private String content;
    private Integer position;
}

