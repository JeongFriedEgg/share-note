package com.example.share_note.service;

import com.example.share_note.dto.workspace.WorkspaceCreateRequestDto;
import com.example.share_note.dto.workspace.WorkspaceCreateResponseDto;
import com.example.share_note.dto.workspace.WorkspaceUpdateRequestDto;
import com.example.share_note.dto.workspace.WorkspaceUpdateResponseDto;
import reactor.core.publisher.Mono;

public interface WorkspaceService {
    Mono<WorkspaceCreateResponseDto> createWorkspace(WorkspaceCreateRequestDto request);

    Mono<WorkspaceUpdateResponseDto> updateWorkspace(WorkspaceUpdateRequestDto request);

    Mono<Void> deleteWorkspace(String workspaceId);
}
