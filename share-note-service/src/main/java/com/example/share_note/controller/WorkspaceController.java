package com.example.share_note.controller;

import com.example.share_note.dto.workspace.WorkspaceCreateRequestDto;
import com.example.share_note.dto.workspace.WorkspaceCreateResponseDto;
import com.example.share_note.dto.workspace.WorkspaceUpdateRequestDto;
import com.example.share_note.dto.workspace.WorkspaceUpdateResponseDto;
import com.example.share_note.service.WorkspaceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/workspaces")
@RequiredArgsConstructor
public class WorkspaceController {
    private final WorkspaceService workspaceService;

    @PostMapping
    public Mono<ResponseEntity<WorkspaceCreateResponseDto>> createWorkspace(
            @RequestBody WorkspaceCreateRequestDto request) {
        return workspaceService.createWorkspace(request)
                .map(response ->
                        ResponseEntity.status(HttpStatus.CREATED).body(response)
                );
    }

    @PutMapping
    public Mono<ResponseEntity<WorkspaceUpdateResponseDto>> updateWorkspace(
            @RequestBody WorkspaceUpdateRequestDto request) {
        return workspaceService.updateWorkspace(request)
                .map(ResponseEntity::ok);
    }

    @DeleteMapping("/{workspaceId}")
    public Mono<ResponseEntity<Void>> deleteWorkspace(@PathVariable String workspaceId) {
        return workspaceService.deleteWorkspace(workspaceId)
                .then(Mono.just(ResponseEntity.status(HttpStatus.NO_CONTENT).build()));
    }
}
