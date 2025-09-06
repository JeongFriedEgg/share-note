package com.example.share_note.dto.workspacemember;

import com.example.share_note.enums.WorkspaceRole;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class WorkspaceMemberInviteRequestDto {
    private Long userId;
    private WorkspaceRole role;
}
