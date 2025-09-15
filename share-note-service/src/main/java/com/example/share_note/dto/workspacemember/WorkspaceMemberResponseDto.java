package com.example.share_note.dto.workspacemember;

import com.example.share_note.domain.WorkspaceMember;
import com.example.share_note.enums.WorkspaceRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkspaceMemberResponseDto {
    private String id;
    private String workspaceId;
    private String userId;
    private WorkspaceRole role;
    private LocalDateTime joinedAt;

    public static WorkspaceMemberResponseDto from(WorkspaceMember member) {
        return WorkspaceMemberResponseDto.builder()
                .id(member.getId().toString())
                .workspaceId(member.getWorkspaceId().toString())
                .userId(member.getUserId().toString())
                .role(member.getRole())
                .joinedAt(member.getJoinedAt())
                .build();
    }
}

