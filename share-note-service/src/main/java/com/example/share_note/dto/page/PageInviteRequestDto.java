package com.example.share_note.dto.page;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PageInviteRequestDto {
    private String userId;
    private String permissionType;
}
