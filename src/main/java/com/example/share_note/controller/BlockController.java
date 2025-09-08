package com.example.share_note.controller;

import com.example.share_note.dto.block.*;
import com.example.share_note.service.BlockService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/workspaces/{workspaceId}/pages/{pageId}/blocks")
@RequiredArgsConstructor
public class BlockController {

    private final BlockService blockService;

    @PostMapping
    public Mono<ResponseEntity<BlockCreateResponseDto>> createBlock(
            @PathVariable Long workspaceId,
            @PathVariable Long pageId,
            @RequestBody BlockCreateRequestDto request) {
        return blockService.createBlock(workspaceId, pageId, request)
                .map(response ->
                        ResponseEntity.status(HttpStatus.CREATED).body(response));
    }

    @GetMapping
    public Mono<ResponseEntity<BlockListResponseDto>> getBlocks(
            @PathVariable Long workspaceId,
            @PathVariable Long pageId) {
        return blockService.getBlocks(workspaceId, pageId)
                .map(ResponseEntity::ok);
    }

    @GetMapping("/{blockId}")
    public Mono<ResponseEntity<BlockResponseDto>> getBlock(
            @PathVariable Long workspaceId,
            @PathVariable Long pageId,
            @PathVariable Long blockId) {
        return blockService.getBlock(workspaceId, pageId, blockId)
                .map(ResponseEntity::ok);
    }

    @PutMapping("/{blockId}")
    public Mono<ResponseEntity<BlockResponseDto>> updateBlock(
            @PathVariable Long workspaceId,
            @PathVariable Long pageId,
            @PathVariable Long blockId,
            @RequestBody BlockUpdateRequestDto request) {
        return blockService.updateBlock(workspaceId, pageId, blockId, request)
                .map(ResponseEntity::ok);
    }

    @PutMapping("/{blockId}/move")
    public Mono<ResponseEntity<BlockResponseDto>> moveBlock(
            @PathVariable Long workspaceId,
            @PathVariable Long pageId,
            @PathVariable Long blockId,
            @RequestBody BlockMoveRequestDto request) {
        return blockService.moveBlock(workspaceId, pageId, blockId, request)
                .map(ResponseEntity::ok);
    }

    @PutMapping("/{blockId}/archive")
    public Mono<ResponseEntity<BlockStatusResponseDto>> archiveBlock(
            @PathVariable Long workspaceId,
            @PathVariable Long pageId,
            @PathVariable Long blockId) {
        return blockService.archiveBlock(workspaceId, pageId, blockId)
                .map(ResponseEntity::ok);
    }

    @PutMapping("/{blockId}/restore")
    public Mono<ResponseEntity<BlockStatusResponseDto>> restoreBlock(
            @PathVariable Long workspaceId,
            @PathVariable Long pageId,
            @PathVariable Long blockId) {
        return blockService.restoreBlock(workspaceId, pageId, blockId)
                .map(ResponseEntity::ok);
    }
}
