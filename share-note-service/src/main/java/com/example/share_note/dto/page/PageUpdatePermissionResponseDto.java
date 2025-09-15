package com.example.share_note.dto.page;

import com.example.share_note.domain.PagePermission;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PageUpdatePermissionResponseDto {
    private String permissionId;
    private String pageId;
    private String userId;
    private String permission;

    public static PageUpdatePermissionResponseDto from(PagePermission pagePermission) {
        return PageUpdatePermissionResponseDto.builder()
                .permissionId(pagePermission.getId().toString())
                .pageId(pagePermission.getPageId().toString())
                .userId(pagePermission.getUserId().toString())
                .permission(pagePermission.getPermission())
                .build();
    }
}
