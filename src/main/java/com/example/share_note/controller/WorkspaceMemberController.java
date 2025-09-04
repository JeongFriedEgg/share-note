package com.example.share_note.controller;

import com.example.share_note.dto.workspacemember.*;
import com.example.share_note.service.WorkspaceMemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/workspaces/{workspaceId}/members")
@RequiredArgsConstructor
public class WorkspaceMemberController {
    private final WorkspaceMemberService workspaceMemberService;

    @PostMapping("/invite")
    public Mono<ResponseEntity<WorkspaceMemberResponseDto>> inviteMember(
            @PathVariable Long workspaceId,
            @RequestBody WorkspaceMemberInviteRequestDto request) {
        return workspaceMemberService.inviteMember(workspaceId, request)
                .map(response ->
                        ResponseEntity.status(HttpStatus.CREATED).body(response));
    }

    @GetMapping
    public Mono<ResponseEntity<WorkspaceMemberListResponseDto>> getWorkspaceMembers(
            @PathVariable Long workspaceId) {
        return workspaceMemberService.getWorkspaceMembers(workspaceId)
                .map(ResponseEntity::ok);
    }

    @PutMapping("/role")
    public Mono<ResponseEntity<WorkspaceMemberResponseDto>> updateMemberRole(
            @PathVariable Long workspaceId,
            @RequestBody  WorkspaceMemberRoleUpdateRequestDto request) {
        return workspaceMemberService.updateMemberRole(workspaceId, request)
                .map(ResponseEntity::ok);
    }

    @DeleteMapping("/{userId}")
    public Mono<ResponseEntity<Void>> removeMember(
            @PathVariable Long workspaceId,
            @PathVariable Long userId) {
        return workspaceMemberService.removeMember(workspaceId, userId)
                .then(Mono.just(ResponseEntity.noContent().build()));
    }
}
