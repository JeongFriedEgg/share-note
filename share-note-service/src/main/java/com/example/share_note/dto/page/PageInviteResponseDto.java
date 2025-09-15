package com.example.share_note.dto.page;

import com.example.share_note.domain.PagePermission;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PageInviteResponseDto {
    private String pageId;
    private String userId;
    private String permission;

    public static PageInviteResponseDto from(PagePermission pagePermission) {
        return PageInviteResponseDto.builder()
                .pageId(pagePermission.getPageId().toString())
                .userId(pagePermission.getUserId().toString())
                .permission(pagePermission.getPermission())
                .build();
    }
}
