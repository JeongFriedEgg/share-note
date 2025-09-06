package com.example.share_note.dto.page;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PageListItemResponseDto {
    private Long pageId;
    private String title;
    private String icon;
}
