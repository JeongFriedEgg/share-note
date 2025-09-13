package com.example.share_note.dto.page;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PageCreateRequestDto {
    private String parentPageId;
    private String title;
    private String icon;
    private String cover;
    private String properties;
}
