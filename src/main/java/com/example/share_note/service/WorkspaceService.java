package com.example.share_note.service;

import com.example.share_note.domain.Workspace;
import com.example.share_note.dto.CustomUserDetails;
import com.example.share_note.dto.workspace.WorkspaceCreateRequestDto;
import com.example.share_note.dto.workspace.WorkspaceCreateResponseDto;
import com.example.share_note.dto.workspace.WorkspaceUpdateRequestDto;
import com.example.share_note.dto.workspace.WorkspaceUpdateResponseDto;
import com.example.share_note.exception.ErrorCode;
import com.example.share_note.exception.WorkspaceException;
import com.example.share_note.repository.ReactiveWorkspaceRepository;
import com.example.share_note.util.UuidUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class WorkspaceService {
    private final ReactiveWorkspaceRepository reactiveWorkspaceRepository;
    private final UuidUtils uuidUtils;

    public Mono<WorkspaceCreateResponseDto> createWorkspace(WorkspaceCreateRequestDto request) {
        return ReactiveSecurityContextHolder.getContext()
                .map(securityContext ->
                        (CustomUserDetails) securityContext.getAuthentication().getPrincipal())
                .flatMap(userDetails -> {
                    Workspace workspace = Workspace.builder()
                            .name(request.getName())
                            .description(request.getDescription())
                            .createdBy(userDetails.getId())
                            .createdAt(LocalDateTime.now())
                            .updatedAt(LocalDateTime.now())
                            .build();
                    return reactiveWorkspaceRepository.save(workspace)
                            .map(savedWorkspace -> WorkspaceCreateResponseDto.builder()
                                    .name(savedWorkspace.getName())
                                    .description(savedWorkspace.getDescription())
                                    .createdAt(savedWorkspace.getCreatedAt())
                                    .build());
                });
    }

    public Mono<WorkspaceUpdateResponseDto> updateWorkspace(WorkspaceUpdateRequestDto request) {
        return ReactiveSecurityContextHolder.getContext()
                .map(securityContext ->
                        (CustomUserDetails) securityContext.getAuthentication().getPrincipal())
                .flatMap(userDetails ->
                        reactiveWorkspaceRepository.findById(uuidUtils.fromString(request.getWorkspaceId()))
                                .switchIfEmpty(Mono.error(new WorkspaceException(ErrorCode.WORKSPACE_NOT_FOUND)))
                                .flatMap(existingWorkspace -> {
                                    if (!existingWorkspace.getCreatedBy().equals(userDetails.getId())) {
                                        return Mono.error(new WorkspaceException(ErrorCode.WORKSPACE_PERMISSION_DENIED));
                                    }

                                    if (!StringUtils.hasText(request.getName())) {
                                        return Mono.error(new WorkspaceException(ErrorCode.INVALID_WORKSPACE_NAME));
                                    }

                                    return reactiveWorkspaceRepository.save(
                                            Workspace.builder()
                                                    .name(request.getName())
                                                    .description(request.getDescription() != null ?
                                                            request.getDescription() : existingWorkspace.getDescription())
                                                    .createdAt(existingWorkspace.getCreatedAt())
                                                    .updatedAt(LocalDateTime.now())
                                                    .createdBy(existingWorkspace.getCreatedBy())
                                            .build());
                                })
                )
                .map(updatedWorkspace -> WorkspaceUpdateResponseDto.builder()
                        .name(updatedWorkspace.getName())
                        .description(updatedWorkspace.getDescription())
                        .updatedAt(updatedWorkspace.getUpdatedAt())
                        .build());
    }

    public Mono<Void> deleteWorkspace(String workspaceId) {
        return ReactiveSecurityContextHolder.getContext()
                .map(securityContext ->
                        (CustomUserDetails) securityContext.getAuthentication().getPrincipal())
                .flatMap(userDetails ->
                        reactiveWorkspaceRepository.findById(uuidUtils.fromString(workspaceId))
                                .switchIfEmpty(Mono.error(new WorkspaceException(ErrorCode.WORKSPACE_NOT_FOUND)))
                                .flatMap(existingWorkspace -> {
                                    if (!existingWorkspace.getCreatedBy().equals(userDetails.getId())) {
                                        return Mono.error(new WorkspaceException(ErrorCode.WORKSPACE_PERMISSION_DENIED));
                                    }
                                    return reactiveWorkspaceRepository.delete(existingWorkspace);
                                })
                );
    }
}
