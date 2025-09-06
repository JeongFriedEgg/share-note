package com.example.share_note.dto.page;

import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PageListResponseDto {
    private List<PageListItemResponseDto> pages;
}
