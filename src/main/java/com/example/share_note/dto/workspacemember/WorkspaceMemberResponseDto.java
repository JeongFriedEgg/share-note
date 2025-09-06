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
    private Long id;
    private Long workspaceId;
    private Long userId;
    private WorkspaceRole role;
    private LocalDateTime joinedAt;

    public static WorkspaceMemberResponseDto from(WorkspaceMember member) {
        return WorkspaceMemberResponseDto.builder()
                .id(member.getId())
                .workspaceId(member.getWorkspaceId())
                .userId(member.getUserId())
                .role(member.getRole())
                .joinedAt(member.getJoinedAt())
                .build();
    }
}

