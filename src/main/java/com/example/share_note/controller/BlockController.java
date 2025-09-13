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
            @PathVariable String workspaceId,
            @PathVariable String pageId,
            @RequestBody BlockCreateRequestDto request) {
        return blockService.createBlock(workspaceId, pageId, request)
                .map(response ->
                        ResponseEntity.status(HttpStatus.CREATED).body(response));
    }

    @GetMapping
    public Mono<ResponseEntity<BlockListResponseDto>> getBlocks(
            @PathVariable String workspaceId,
            @PathVariable String pageId) {
        return blockService.getBlocks(workspaceId, pageId)
                .map(ResponseEntity::ok);
    }

    @GetMapping("/{blockId}")
    public Mono<ResponseEntity<BlockResponseDto>> getBlock(
            @PathVariable String workspaceId,
            @PathVariable String pageId,
            @PathVariable String blockId) {
        return blockService.getBlock(workspaceId, pageId, blockId)
                .map(ResponseEntity::ok);
    }

    @PutMapping("/{blockId}")
    public Mono<ResponseEntity<BlockResponseDto>> updateBlock(
            @PathVariable String workspaceId,
            @PathVariable String pageId,
            @PathVariable String blockId,
            @RequestBody BlockUpdateRequestDto request) {
        return blockService.updateBlock(workspaceId, pageId, blockId, request)
                .map(ResponseEntity::ok);
    }

    @PutMapping("/{blockId}/move")
    public Mono<ResponseEntity<BlockResponseDto>> moveBlock(
            @PathVariable String workspaceId,
            @PathVariable String pageId,
            @PathVariable String blockId,
            @RequestBody BlockMoveRequestDto request) {
        return blockService.moveBlock(workspaceId, pageId, blockId, request)
                .map(ResponseEntity::ok);
    }

    @PutMapping("/{blockId}/archive")
    public Mono<ResponseEntity<BlockStatusResponseDto>> archiveBlock(
            @PathVariable String workspaceId,
            @PathVariable String pageId,
            @PathVariable String blockId) {
        return blockService.archiveBlock(workspaceId, pageId, blockId)
                .map(ResponseEntity::ok);
    }

    @PutMapping("/{blockId}/restore")
    public Mono<ResponseEntity<BlockStatusResponseDto>> restoreBlock(
            @PathVariable String workspaceId,
            @PathVariable String pageId,
            @PathVariable String blockId) {
        return blockService.restoreBlock(workspaceId, pageId, blockId)
                .map(ResponseEntity::ok);
    }
}
