package com.example.share_note.service;

import com.example.share_note.domain.WorkspaceMember;
import com.example.share_note.dto.CustomUserDetails;
import com.example.share_note.dto.workspacemember.*;
import com.example.share_note.enums.WorkspaceRole;
import com.example.share_note.exception.ErrorCode;
import com.example.share_note.exception.WorkspaceException;
import com.example.share_note.exception.WorkspaceMemberException;
import com.example.share_note.repository.ReactiveWorkspaceMemberRepository;
import com.example.share_note.repository.ReactiveWorkspaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class WorkspaceMemberService {
    private final ReactiveWorkspaceMemberRepository reactiveWorkspaceMemberRepository;
    private final ReactiveWorkspaceRepository reactiveWorkspaceRepository;

    public Mono<WorkspaceMemberResponseDto> inviteMember(Long workspaceId, WorkspaceMemberInviteRequestDto request) {
        return getCurrentUserFromContext()
                .flatMap(userDetails ->
                        reactiveWorkspaceRepository.findById(workspaceId)
                                .switchIfEmpty(Mono.error(new WorkspaceException(ErrorCode.WORKSPACE_NOT_FOUND)))
                                .flatMap(workspace -> checkAdminPermission(userDetails.getId(), workspaceId)
                                        .flatMap(hasPermission -> {
                                            if (!hasPermission) {
                                                return Mono.error(new WorkspaceException(ErrorCode.WORKSPACE_PERMISSION_DENIED));
                                            }
                                            return reactiveWorkspaceMemberRepository
                                                    .existsByWorkspaceIdAndUserId(workspaceId, request.getUserId())
                                                    .flatMap(exists -> {
                                                        if (exists) {
                                                            return Mono.error(new WorkspaceMemberException(ErrorCode.MEMBER_ALREADY_EXISTS));
                                                        }
                                                        WorkspaceMember newMember = WorkspaceMember.builder()
                                                                .workspaceId(workspaceId)
                                                                .userId(request.getUserId())
                                                                .role(request.getRole())
                                                                .joinedAt(LocalDateTime.now())
                                                                .build();
                                                        return reactiveWorkspaceMemberRepository.save(newMember);
                                                    });
                                        })
                                )
                )
                .map(WorkspaceMemberResponseDto::from);
    }

    public Mono<WorkspaceMemberListResponseDto> getWorkspaceMembers(Long workspaceId) {
        return getCurrentUserFromContext()
                .flatMap(userDetails ->
                        checkMemberPermission(userDetails.getId(), workspaceId)
                                .flatMap(hasPermission -> {
                                    if (!hasPermission) {
                                        return Mono.error(new WorkspaceException(ErrorCode.WORKSPACE_PERMISSION_DENIED));
                                    }
                                    return reactiveWorkspaceMemberRepository.findByWorkspaceId(workspaceId)
                                            .map(WorkspaceMemberResponseDto::from)
                                            .collectList()
                                            .map(memberList ->
                                                    WorkspaceMemberListResponseDto.builder()
                                                            .members(memberList)
                                                            .totalCount(memberList.size())
                                                            .build());
                                })
                );
    }

    public Mono<WorkspaceMemberResponseDto> updateMemberRole(Long workspaceId, WorkspaceMemberRoleUpdateRequestDto request) {
        return getCurrentUserFromContext()
                .flatMap(userDetails ->
                        checkAdminPermission(userDetails.getId(), workspaceId)
                                .flatMap(hasPermission -> {
                                    if (!hasPermission) {
                                        return Mono.error(new WorkspaceException(ErrorCode.WORKSPACE_PERMISSION_DENIED));
                                    }
                                    return reactiveWorkspaceMemberRepository
                                            .findByWorkspaceIdAndUserId(workspaceId, request.getUserId())
                                            .switchIfEmpty(Mono.error(new WorkspaceMemberException(ErrorCode.MEMBER_NOT_FOUND)))
                                            .flatMap(existingMember -> {
                                                if (existingMember.getRole() == WorkspaceRole.OWNER) {
                                                    return Mono.error(new WorkspaceMemberException(ErrorCode.CANNOT_CHANGE_OWNER_ROLE));
                                                }

                                                WorkspaceMember updatedMember = WorkspaceMember.builder()
                                                        .id(existingMember.getId())
                                                        .workspaceId(existingMember.getWorkspaceId())
                                                        .userId(existingMember.getUserId())
                                                        .role(request.getRole())
                                                        .joinedAt(existingMember.getJoinedAt())
                                                        .build();

                                                return reactiveWorkspaceMemberRepository.save(updatedMember);
                                            });
                                })
                )
                .map(WorkspaceMemberResponseDto::from);
    }

    public Mono<Void> removeMember(Long workspaceId, Long userId) {
        return getCurrentUserFromContext()
                .flatMap(currentUser ->
                        reactiveWorkspaceMemberRepository
                                .findByWorkspaceIdAndUserId(workspaceId, currentUser.getId())
                                .switchIfEmpty(Mono.error(new WorkspaceMemberException(ErrorCode.MEMBER_NOT_FOUND)))
                                .flatMap(requestingMember -> {
                                    if (requestingMember.getRole() != WorkspaceRole.OWNER) {
                                        return Mono.error(new WorkspaceException(ErrorCode.WORKSPACE_PERMISSION_DENIED));
                                    }
                                    if (currentUser.getId().equals(userId)) {
                                        return Mono.error(new WorkspaceMemberException(ErrorCode.CANNOT_REMOVE_OWNER));
                                    }
                                    return reactiveWorkspaceMemberRepository.existsByWorkspaceIdAndUserId(workspaceId, userId)
                                            .flatMap(exists -> {
                                                if (!exists) {
                                                    return Mono.error(new WorkspaceMemberException(ErrorCode.MEMBER_NOT_FOUND));
                                                }
                                                return reactiveWorkspaceMemberRepository.deleteByWorkspaceIdAndUserId(workspaceId, userId);
                                            });
                                })
                );
    }

    private Mono<CustomUserDetails> getCurrentUserFromContext() {
        return ReactiveSecurityContextHolder.getContext()
                .map(securityContext -> (CustomUserDetails) securityContext.getAuthentication().getPrincipal());
    }

    private Mono<Boolean> checkAdminPermission(Long userId, Long workspaceId) {
        return reactiveWorkspaceRepository.findById(workspaceId)
                .flatMap(workspace -> {
                    if (workspace.getCreatedBy().equals(userId)) {
                        return Mono.just(true);
                    }
                    return reactiveWorkspaceMemberRepository
                            .findByWorkspaceIdAndUserId(workspaceId, userId)
                            .map(member -> member.getRole() == WorkspaceRole.ADMIN)
                            .defaultIfEmpty(false);
                });
    }

    private Mono<Boolean> checkMemberPermission(Long userId, Long workspaceId) {
        return reactiveWorkspaceRepository.findById(workspaceId)
                .flatMap(workspace -> {
                    if (workspace.getCreatedBy().equals(userId)) {
                        return Mono.just(true);
                    }
                    return reactiveWorkspaceMemberRepository
                            .existsByWorkspaceIdAndUserId(workspaceId, userId);
                });
    }
}
