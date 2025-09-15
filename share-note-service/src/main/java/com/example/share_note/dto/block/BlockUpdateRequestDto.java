package com.example.share_note.dto.block;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BlockUpdateRequestDto {
    private String type;
    private String content;
    private Integer position;
}
