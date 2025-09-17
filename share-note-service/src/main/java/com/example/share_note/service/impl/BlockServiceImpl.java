package com.example.share_note.service.impl;

import com.example.share_note.domain.Block;
import com.example.share_note.domain.Page;
import com.example.share_note.dto.CustomUserDetails;
import com.example.share_note.dto.block.*;
import com.example.share_note.enums.PagePermissionType;
import com.example.share_note.exception.*;
import com.example.share_note.repository.*;
import com.example.share_note.service.BlockService;
import com.example.share_note.util.UuidUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class BlockServiceImpl implements BlockService {

    private final ReactiveBlockRepository reactiveBlockRepository;
    private final ReactivePageRepository reactivePageRepository;
    private final ReactivePagePermissionRepository reactivePagePermissionRepository;
    private final ReactiveWorkspaceRepository reactiveWorkspaceRepository;
    private final ReactiveWorkspaceMemberRepository reactiveWorkspaceMemberRepository;
    private final UuidUtils uuidUtils;

    /**
     * 블록 생성
     * <p>
     * 1. 인증 처리
     * 2. 페이지 존재 유무 확인
     * 3. 페이지에 대한 편집 권한 확인
     * 4. 부모 블록 존재 유무 확인 (있는 경우)
     * 5. 블록 생성 및 저장
     *
     * @param workspaceIdStr
     * @param pageIdStr
     * @param request
     * @return
     */
    public Mono<BlockCreateResponseDto> createBlock(String workspaceIdStr, String pageIdStr, BlockCreateRequestDto request) {
        UUID workspaceId = uuidUtils.fromString(workspaceIdStr);
        UUID pageId = uuidUtils.fromString(pageIdStr);

        return handleStandardExceptions(
                getCurrentUser()
                        .flatMap(user -> validateEditPermission(workspaceId, pageId, user.getId()))
                        .flatMap(page -> validateParentBlock(request.getParentBlockId(), pageId))
                        .then(getCurrentUser())
                        .flatMap(user -> createAndSaveBlock(pageId, request, user.getId()))
                        .map(BlockCreateResponseDto::from)
        );
    }

    /**
     * 페이지의 블록 목록 조회
     * <p>
     * 1. 인증 처리
     * 2. 페이지 존재 유무 및 읽기 권한 확인
     * 3. 블록 목록 조회 (보관되지 않은 블록만)
     *
     * @param workspaceIdStr
     * @param pageIdStr
     * @return
     */
    public Mono<BlockListResponseDto> getBlocks(String workspaceIdStr, String pageIdStr) {
        UUID workspaceId = uuidUtils.fromString(workspaceIdStr);
        UUID pageId = uuidUtils.fromString(pageIdStr);

        return handleStandardExceptions(
                getCurrentUser()
                        .flatMap(user -> validateReadPermission(workspaceId, pageId, user.getId()))
                        .flatMap(page ->
                                reactiveBlockRepository.findAllByPageIdAndIsArchivedFalseOrderByPositionAsc(pageId)
                                        .map(block -> BlockListItemResponseDto.builder()
                                                .blockId(uuidUtils.fromUUID(block.getId()))
                                                .parentBlockId(uuidUtils.fromUUID(block.getParentBlockId()))
                                                .type(block.getType())
                                                .position(block.getPosition())
                                                .build())
                                        .collectList()
                                        .map(BlockListResponseDto::new)
                        )
        );
    }

    /**
     * 특정 블록 조회
     * <p>
     * 1. 인증 처리
     * 2. 블록 존재 유무 확인
     * 3. 페이지 읽기 권한 확인
     *
     * @param workspaceIdStr
     * @param pageIdStr
     * @param blockIdStr
     * @return
     */
    public Mono<BlockResponseDto> getBlock(String workspaceIdStr, String pageIdStr, String blockIdStr) {
        UUID workspaceId = uuidUtils.fromString(workspaceIdStr);
        UUID pageId = uuidUtils.fromString(pageIdStr);
        UUID blockId = uuidUtils.fromString(blockIdStr);

        return handleStandardExceptions(
                getCurrentUser()
                        .flatMap(user ->
                                findBlockByIdAndPageId(blockId, pageId)
                                        .flatMap(block -> validateReadPermission(workspaceId, pageId, user.getId())
                                                .thenReturn(block)
                                        )
                        )
                        .map(BlockResponseDto::from)
        );
    }

    /**
     * 블록 수정
     * <p>
     * 1. 인증 처리
     * 2. 블록 존재 유무 확인
     * 3. 페이지 편집 권한 확인
     * 4. 블록 수정 및 저장
     *
     * @param workspaceIdStr
     * @param pageIdStr
     * @param blockIdStr
     * @param request
     * @return
     */
    public Mono<BlockResponseDto> updateBlock(String workspaceIdStr, String pageIdStr,
                                              String blockIdStr, BlockUpdateRequestDto request) {
        UUID workspaceId = uuidUtils.fromString(workspaceIdStr);
        UUID pageId = uuidUtils.fromString(pageIdStr);
        UUID blockId = uuidUtils.fromString(blockIdStr);

        return handleStandardExceptions(
                getCurrentUser()
                        .flatMap(user ->
                                findBlockByIdAndPageId(blockId, pageId)
                                        .flatMap(block ->
                                                validateEditPermission(workspaceId, pageId, user.getId())
                                                        .thenReturn(block)
                                        )
                                        .map(block -> updateBlockFields(block, request, user.getId()))
                                        .flatMap(reactiveBlockRepository::save)
                        )
                        .map(BlockResponseDto::from)
        );
    }

    /**
     * 블록 위치 변경 (같은 페이지 내에서)
     * <p>
     * 1. 인증 처리
     * 2. 블록 존재 유무 확인
     * 3. 페이지 편집 권한 확인
     * 4. 새 부모 블록 존재 유무 확인 (있는 경우)
     * 5. 블록 위치 변경
     *
     * @param workspaceIdStr
     * @param pageIdStr
     * @param blockIdStr
     * @param request
     * @return
     */
    public Mono<BlockResponseDto> moveBlock(String workspaceIdStr, String pageIdStr, String blockIdStr, BlockMoveRequestDto request) {
        UUID workspaceId = uuidUtils.fromString(workspaceIdStr);
        UUID pageId = uuidUtils.fromString(pageIdStr);
        UUID blockId = uuidUtils.fromString(blockIdStr);

        return handleStandardExceptions(
                getCurrentUser()
                        .flatMap(user ->
                                findBlockByIdAndPageId(blockId, pageId)
                                        .flatMap(block -> validateEditPermission(workspaceId, pageId, user.getId())
                                                .then(validateNotSelfParent(blockId, request.getNewParentBlockId()))
                                                .then(validateParentBlock(request.getNewParentBlockId(), pageId))
                                                .thenReturn(block)
                                        )
                                        .map(block -> updateBlockPosition(block, request, user.getId()))
                                        .flatMap(reactiveBlockRepository::save)
                        )
                        .map(BlockResponseDto::from)
        );
    }

    /**
     * 블록 보관 (soft delete)
     * <p>
     * 1. 인증 처리
     * 2. 블록 존재 유무 확인
     * 3. 페이지 편집 권한 확인
     * 4. 블록과 모든 하위 블록을 보관 처리
     *
     * @param workspaceIdStr
     * @param pageIdStr
     * @param blockIdStr
     * @return
     */
    public Mono<BlockStatusResponseDto> archiveBlock(String workspaceIdStr, String pageIdStr, String blockIdStr) {
        UUID workspaceId = uuidUtils.fromString(workspaceIdStr);
        UUID pageId = uuidUtils.fromString(pageIdStr);
        UUID blockId = uuidUtils.fromString(blockIdStr);

        return handleStandardExceptions(
                getCurrentUser()
                        .flatMap(customUserDetails ->
                                validateBlockAndEditPermissionForArchive(workspaceId, pageId, blockId, customUserDetails.getId())
                                        .flatMap(block ->
                                                updateArchiveStatus(blockId, true, customUserDetails.getId())
                                        )
                        )
        );
    }

    /**
     * 블록 복원
     * <p>
     * 1. 인증 처리
     * 2. 블록 존재 유무 확인
     * 3. 페이지 편집 권한 확인
     * 4. 블록과 모든 하위 블록을 복원 처리
     *
     * @param workspaceIdStr
     * @param pageIdStr
     * @param blockIdStr
     * @return
     */
    public Mono<BlockStatusResponseDto> restoreBlock(String workspaceIdStr, String pageIdStr, String blockIdStr) {
        UUID workspaceId = uuidUtils.fromString(workspaceIdStr);
        UUID pageId = uuidUtils.fromString(pageIdStr);
        UUID blockId = uuidUtils.fromString(blockIdStr);

        return handleStandardExceptions(getCurrentUser()
                .flatMap(customUserDetails ->
                        validateBlockAndEditPermissionForArchive(workspaceId, pageId, blockId, customUserDetails.getId())
                                .flatMap(block -> updateArchiveStatus(blockId, false, customUserDetails.getId()))
                )
        );
    }

    /**
     * 현재 인증된 사용자 정보 조회
     */
    private Mono<CustomUserDetails> getCurrentUser() {
        return ReactiveSecurityContextHolder.getContext()
                .map(securityContext -> (CustomUserDetails) securityContext.getAuthentication().getPrincipal())
                .switchIfEmpty(Mono.error(new UserException(ErrorCode.AUTHENTICATION_FAILED)));
    }

    /**
     * 페이지 읽기 권한 확인
     * 워크스페이스 소유자, 멤버 권한, 공개 페이지 여부 종합 검증
     */
    private Mono<Page> validateReadPermission(UUID workspaceId, UUID pageId, UUID userId) {
        return reactivePageRepository.findByIdAndWorkspaceId(pageId, workspaceId)
                .switchIfEmpty(Mono.error(new PageException(ErrorCode.PAGE_NOT_FOUND)))
                .flatMap(page ->
                        reactiveWorkspaceRepository.findById(workspaceId)
                                .switchIfEmpty(Mono.error(new WorkspaceException(ErrorCode.WORKSPACE_NOT_FOUND)))
                                .flatMap(workspace -> {
                                    // 워크스페이스 소유자는 모든 페이지 읽기 가능
                                    if (workspace.getCreatedBy().equals(userId)) {
                                        return Mono.just(page);
                                    }

                                    return checkMemberPermissionOrPublic(workspaceId, pageId, userId, page, PagePermissionType.READ)
                                            .thenReturn(page);
                                })
                );
    }

    /**
     * 페이지 편집 권한 확인
     * 워크스페이스 소유자 또는 편집 권한이 있는 멤버만 허용
     */
    private Mono<Page> validateEditPermission(UUID workspaceId, UUID pageId, UUID userId) {
        return reactivePageRepository.findByIdAndWorkspaceId(pageId, workspaceId)
                .switchIfEmpty(Mono.error(new PageException(ErrorCode.PAGE_NOT_FOUND)))
                .flatMap(page ->
                        reactiveWorkspaceRepository.findById(workspaceId)
                                .switchIfEmpty(Mono.error(new WorkspaceException(ErrorCode.WORKSPACE_NOT_FOUND)))
                                .flatMap(workspace -> {
                                    // 워크스페이스 소유자는 모든 페이지 편집 가능
                                    if (workspace.getCreatedBy().equals(userId)) {
                                        return Mono.just(page);
                                    }
                                    return checkMemberEditPermission(workspaceId, pageId, userId)
                                            .thenReturn(page); // Boolean을 무시하고 Page 반환
                                })
                );
    }

    /**
     * 멤버 권한 또는 공개 페이지 확인 (읽기용)
     */
    private Mono<Boolean> checkMemberPermissionOrPublic(UUID workspaceId, UUID pageId, UUID userId,
                                                        Page page, PagePermissionType requiredPermission) {
        return reactiveWorkspaceMemberRepository.existsByWorkspaceIdAndUserId(workspaceId, userId)
                .flatMap(isMember -> {
                    if (isMember) {
                        return checkPagePermission(pageId, userId, requiredPermission);
                    } else {
                        // 멤버가 아닌 경우 공개 페이지 여부 확인
                        if (page.isPublic()) {
                            return Mono.just(true);
                        }
                        return Mono.error(new PagePermissionException(ErrorCode.PAGE_PERMISSION_DENIED));
                    }
                });
    }

    /**
     * 멤버 편집 권한 확인 (편집용)
     */
    private Mono<Boolean> checkMemberEditPermission(UUID workspaceId, UUID pageId, UUID userId) {
        return reactiveWorkspaceMemberRepository.existsByWorkspaceIdAndUserId(workspaceId, userId)
                .flatMap(isMember -> {
                    if (!isMember) {
                        return Mono.error(new WorkspaceMemberException(ErrorCode.MEMBER_NOT_FOUND));
                    }
                    return checkPagePermission(pageId, userId, PagePermissionType.EDIT);
                });
    }

    /**
     * 페이지 권한 레벨 확인
     */
    private Mono<Boolean> checkPagePermission(UUID pageId, UUID userId, PagePermissionType requiredPermission) {
        return reactivePagePermissionRepository.findByPageIdAndUserId(pageId, userId)
                .flatMap(permission -> {
                    PagePermissionType permissionType = PagePermissionType.valueOf(permission.getPermission());
                    if (permissionType.getLevel() >= requiredPermission.getLevel()) {
                        return Mono.just(true);
                    }
                    return Mono.error(new PagePermissionException(ErrorCode.PAGE_PERMISSION_DENIED));
                })
                .switchIfEmpty(Mono.error(new PagePermissionException(ErrorCode.PAGE_PERMISSION_DENIED)));
    }

    /**
     * 블록 존재 여부 확인
     */
    private Mono<Block> findBlockByIdAndPageId(UUID blockId, UUID pageId) {
        return reactiveBlockRepository.findByIdAndPageId(blockId, pageId)
                .switchIfEmpty(Mono.error(new BlockException(ErrorCode.BLOCK_NOT_FOUND)));
    }

    /**
     * 부모 블록 존재 여부 확인
     */
    private Mono<Void> validateParentBlock(String parentBlockIdStr, UUID pageId) {
        if (parentBlockIdStr == null) {
            return Mono.empty();
        }

        UUID parentBlockId = uuidUtils.fromString(parentBlockIdStr);
        return reactiveBlockRepository.findByIdAndPageId(parentBlockId, pageId)
                .switchIfEmpty(Mono.error(new BlockException(ErrorCode.PARENT_BLOCK_NOT_FOUND)))
                .then();
    }

    /**
     * 순환 참조 방지 검증
     */
    private Mono<Void> validateNotSelfParent(UUID blockId, String newParentBlockIdStr) {
        if (newParentBlockIdStr != null && newParentBlockIdStr.equals(blockId.toString())) {
            return Mono.error(new BlockException(ErrorCode.CANNOT_MOVE_TO_SELF));
        }
        return Mono.empty();
    }

    /**
     * 표준 예외 매핑 처리
     */
    private <T> Mono<T> handleStandardExceptions(Mono<T> mono) {
        return mono.onErrorMap(throwable -> {
            if (throwable instanceof UserException ||
                    throwable instanceof PageException ||
                    throwable instanceof PagePermissionException ||
                    throwable instanceof WorkspaceException ||
                    throwable instanceof WorkspaceMemberException ||
                    throwable instanceof BlockException ||
                    throwable instanceof UuidException) {
                return throwable;
            }
            return new BlockException(ErrorCode.UNEXPECTED_ERROR);
        });
    }

    /**
     * 블록 생성
     */
    private Mono<Block> createAndSaveBlock(UUID pageId, BlockCreateRequestDto request, UUID userId) {
        return reactiveBlockRepository.save(Block.builder()
                .pageId(pageId)
                .parentBlockId(uuidUtils.fromString(request.getParentBlockId()))
                .type(request.getType())
                .content(request.getContent())
                .position(request.getPosition() != null ? request.getPosition() : 0)
                .isArchived(false)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .createdBy(userId)
                .lastEditedBy(userId)
                .build()
        );
    }

    /**
     * 블록 업데이트
     */
    private Block updateBlockFields(Block block, BlockUpdateRequestDto request, UUID userId) {
        if (request.getType() != null) {
            block.setType(request.getType());
        }
        if (request.getContent() != null) {
            block.setContent(request.getContent());
        }
        if (request.getPosition() != null) {
            block.setPosition(request.getPosition());
        }
        block.setUpdatedAt(LocalDateTime.now());
        block.setLastEditedBy(userId);

        return block;
    }

    /**
     * 블록 위치 변경
     */
    private Block updateBlockPosition(Block block, BlockMoveRequestDto request, UUID userId) {
        block.setParentBlockId(uuidUtils.fromString(request.getNewParentBlockId()));
        block.setPosition(request.getNewPosition());
        block.setUpdatedAt(LocalDateTime.now());
        block.setLastEditedBy(userId);

        return block;
    }

    /**
     * 블록 및 편집 권한 검증 (archiveBlock, restoreBlock 전용)
     * 기존 로직과 동일한 순서로 검증하여 테스트 호환성 보장
     */
    private Mono<Block> validateBlockAndEditPermissionForArchive(UUID workspaceId, UUID pageId, UUID blockId, UUID userId) {
        return reactiveBlockRepository.findByIdAndPageId(blockId, pageId)
                .switchIfEmpty(Mono.error(new BlockException(ErrorCode.BLOCK_NOT_FOUND)))
                .flatMap(block ->
                        reactivePageRepository.findByIdAndWorkspaceId(pageId, workspaceId)
                                .switchIfEmpty(Mono.error(new PageException(ErrorCode.PAGE_NOT_FOUND)))
                                .flatMap(page ->
                                        reactiveWorkspaceRepository.findById(workspaceId)
                                                .switchIfEmpty(Mono.error(new WorkspaceException(ErrorCode.WORKSPACE_NOT_FOUND)))
                                                .flatMap(workspace -> {
                                                    if (workspace.getCreatedBy().equals(userId)) {
                                                        return Mono.just(block);
                                                    }

                                                    return reactiveWorkspaceMemberRepository.existsByWorkspaceIdAndUserId(workspaceId, userId)
                                                            .flatMap(isMember -> {
                                                                if (!isMember) {
                                                                    return Mono.error(new WorkspaceMemberException(ErrorCode.MEMBER_NOT_FOUND));
                                                                }

                                                                return reactivePagePermissionRepository.findByPageIdAndUserId(pageId, userId)
                                                                        .flatMap(permission -> {
                                                                            PagePermissionType permissionType = PagePermissionType.valueOf(permission.getPermission());
                                                                            if (permissionType.getLevel() >= PagePermissionType.EDIT.getLevel()) {
                                                                                return Mono.just(block);
                                                                            }
                                                                            return Mono.error(new PagePermissionException(ErrorCode.PAGE_PERMISSION_DENIED));
                                                                        })
                                                                        .switchIfEmpty(Mono.error(new PagePermissionException(ErrorCode.PAGE_PERMISSION_DENIED)));
                                                            });
                                                })
                                )
                );
    }

    /**
     * 아카이브 상태 업데이트 공통 로직
     */
    private Mono<BlockStatusResponseDto> updateArchiveStatus(UUID blockId, boolean isArchived, UUID userId) {
        return reactiveBlockRepository.updateArchiveStatusForBlockTree(blockId, isArchived, userId)
                .thenReturn(BlockStatusResponseDto.builder()
                        .blockId(blockId.toString())
                        .isArchived(isArchived)
                        .build());
    }
}
