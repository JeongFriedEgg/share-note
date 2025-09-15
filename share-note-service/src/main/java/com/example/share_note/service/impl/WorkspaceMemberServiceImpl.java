package com.example.share_note.service.impl;

import com.example.share_note.domain.WorkspaceMember;
import com.example.share_note.dto.CustomUserDetails;
import com.example.share_note.dto.workspacemember.*;
import com.example.share_note.enums.WorkspaceRole;
import com.example.share_note.exception.ErrorCode;
import com.example.share_note.exception.WorkspaceException;
import com.example.share_note.exception.WorkspaceMemberException;
import com.example.share_note.repository.ReactiveWorkspaceMemberRepository;
import com.example.share_note.repository.ReactiveWorkspaceRepository;
import com.example.share_note.service.WorkspaceMemberService;
import com.example.share_note.util.UuidUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WorkspaceMemberServiceImpl implements WorkspaceMemberService {
    private final ReactiveWorkspaceMemberRepository reactiveWorkspaceMemberRepository;
    private final ReactiveWorkspaceRepository reactiveWorkspaceRepository;
    private final UuidUtils uuidUtils;

    public Mono<WorkspaceMemberResponseDto> inviteMember(String workspaceIdStr, WorkspaceMemberInviteRequestDto request) {
        UUID workspaceId = uuidUtils.fromString(workspaceIdStr);
        UUID userId = uuidUtils.fromString(request.getUserId());

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
                                                    .existsByWorkspaceIdAndUserId(workspaceId, userId)
                                                    .flatMap(exists -> {
                                                        if (exists) {
                                                            return Mono.error(new WorkspaceMemberException(ErrorCode.MEMBER_ALREADY_EXISTS));
                                                        }
                                                        WorkspaceMember newMember = WorkspaceMember.builder()
                                                                .workspaceId(workspaceId)
                                                                .userId(userId)
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

    public Mono<WorkspaceMemberListResponseDto> getWorkspaceMembers(String workspaceIdStr) {
        UUID workspaceId = uuidUtils.fromString(workspaceIdStr);

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

    public Mono<WorkspaceMemberResponseDto> updateMemberRole(String workspaceIdStr, WorkspaceMemberRoleUpdateRequestDto request) {
        UUID workspaceId = uuidUtils.fromString(workspaceIdStr);

        return getCurrentUserFromContext()
                .flatMap(userDetails ->
                        checkAdminPermission(userDetails.getId(), workspaceId)
                                .flatMap(hasPermission -> {
                                    if (!hasPermission) {
                                        return Mono.error(new WorkspaceException(ErrorCode.WORKSPACE_PERMISSION_DENIED));
                                    }
                                    return reactiveWorkspaceMemberRepository
                                            .findByWorkspaceIdAndUserId(workspaceId, uuidUtils.fromString(request.getUserId()))
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

    public Mono<Void> removeMember(String workspaceIdStr, String userIdStr) {
        UUID workspaceId = uuidUtils.fromString(workspaceIdStr);
        UUID userId = uuidUtils.fromString(userIdStr);

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

    private Mono<Boolean> checkAdminPermission(UUID userId, UUID workspaceId) {
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

    private Mono<Boolean> checkMemberPermission(UUID userId, UUID workspaceId) {
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
