package com.example.share_note.service;

import com.example.share_note.dto.block.*;
import reactor.core.publisher.Mono;

public interface BlockService {
    Mono<BlockCreateResponseDto> createBlock(String workspaceIdStr, String pageIdStr, BlockCreateRequestDto request);

    Mono<BlockListResponseDto> getBlocks(String workspaceIdStr, String pageIdStr);

    Mono<BlockResponseDto> getBlock(String workspaceIdStr, String pageIdStr, String blockIdStr);

    Mono<BlockResponseDto> updateBlock(String workspaceIdStr, String pageIdStr, String blockIdStr, BlockUpdateRequestDto request);

    Mono<BlockResponseDto> moveBlock(String workspaceIdStr, String pageIdStr, String blockIdStr, BlockMoveRequestDto request);

    Mono<BlockStatusResponseDto> archiveBlock(String workspaceIdStr, String pageIdStr, String blockIdStr);

    Mono<BlockStatusResponseDto> restoreBlock(String workspaceIdStr, String pageIdStr, String blockIdStr);
}
