package com.example.share_note.dto.page;

import com.example.share_note.domain.PagePermission;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PageInviteResponseDto {
    private Long pageId;
    private Long userId;
    private String permission;

    public static PageInviteResponseDto from(PagePermission pagePermission) {
        return PageInviteResponseDto.builder()
                .pageId(pagePermission.getPageId())
                .userId(pagePermission.getUserId())
                .permission(pagePermission.getPermission())
                .build();
    }
}
