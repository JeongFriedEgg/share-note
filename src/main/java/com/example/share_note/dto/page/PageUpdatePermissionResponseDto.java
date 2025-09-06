package com.example.share_note.dto.page;

import com.example.share_note.domain.PagePermission;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PageUpdatePermissionResponseDto {
    private Long permissionId;
    private Long pageId;
    private Long userId;
    private String permission;

    public static PageUpdatePermissionResponseDto from(PagePermission pagePermission) {
        return PageUpdatePermissionResponseDto.builder()
                .permissionId(pagePermission.getId())
                .pageId(pagePermission.getPageId())
                .userId(pagePermission.getUserId())
                .permission(pagePermission.getPermission())
                .build();
    }
}
