package com.example.share_note.service;

import com.example.share_note.dto.workspacemember.WorkspaceMemberInviteRequestDto;
import com.example.share_note.dto.workspacemember.WorkspaceMemberListResponseDto;
import com.example.share_note.dto.workspacemember.WorkspaceMemberResponseDto;
import com.example.share_note.dto.workspacemember.WorkspaceMemberRoleUpdateRequestDto;
import reactor.core.publisher.Mono;

public interface WorkspaceMemberService {
    Mono<WorkspaceMemberResponseDto> inviteMember(String workspaceIdStr, WorkspaceMemberInviteRequestDto request);

    Mono<WorkspaceMemberListResponseDto> getWorkspaceMembers(String workspaceIdStr);

    Mono<WorkspaceMemberResponseDto> updateMemberRole(String workspaceIdStr, WorkspaceMemberRoleUpdateRequestDto request);

    Mono<Void> removeMember(String workspaceIdStr, String userIdStr);
}
