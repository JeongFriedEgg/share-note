package com.example.share_note.service.impl;

import com.example.share_note.domain.Block;
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

        return ReactiveSecurityContextHolder.getContext()
                .map(securityContext -> (CustomUserDetails) securityContext.getAuthentication().getPrincipal())
                .switchIfEmpty(Mono.error(new UserException(ErrorCode.AUTHENTICATION_FAILED)))
                .flatMap(customUserDetails ->
                        reactivePageRepository.findByIdAndWorkspaceId(pageId, workspaceId)
                                .switchIfEmpty(Mono.error(new PageException(ErrorCode.PAGE_NOT_FOUND)))
                                .flatMap(page ->
                                        reactiveWorkspaceRepository.findById(workspaceId)
                                                .switchIfEmpty(Mono.error(new WorkspaceException(ErrorCode.WORKSPACE_NOT_FOUND)))
                                                .flatMap(workspace -> {
                                                    if (workspace.getCreatedBy().equals(customUserDetails.getId())) {
                                                        return Mono.just(page); // 소유자는 무조건 편집 가능
                                                    }

                                                    return reactiveWorkspaceMemberRepository.existsByWorkspaceIdAndUserId(workspaceId, customUserDetails.getId())
                                                            .flatMap(isMember -> {
                                                                if (!isMember) {
                                                                    return Mono.error(new WorkspaceMemberException(ErrorCode.MEMBER_NOT_FOUND));
                                                                }

                                                                return reactivePagePermissionRepository.findByPageIdAndUserId(pageId, customUserDetails.getId())
                                                                        .flatMap(permission -> {
                                                                            PagePermissionType permissionType = PagePermissionType.valueOf(permission.getPermission());
                                                                            if (permissionType.getLevel() >= PagePermissionType.EDIT.getLevel()) {
                                                                                return Mono.just(page);
                                                                            }
                                                                            return Mono.error(new PagePermissionException(ErrorCode.PAGE_PERMISSION_DENIED));
                                                                        })
                                                                        .switchIfEmpty(Mono.error(new PagePermissionException(ErrorCode.PAGE_PERMISSION_DENIED)));
                                                            });
                                                })
                                )
                                .flatMap(page -> {
                                    // 부모 블록이 있는 경우 존재 확인
                                    if (request.getParentBlockId() != null) {
                                        return reactiveBlockRepository.findByIdAndPageId(uuidUtils.fromString(request.getParentBlockId()), pageId)
                                                .switchIfEmpty(Mono.error(new BlockException(ErrorCode.PARENT_BLOCK_NOT_FOUND)))
                                                .then(Mono.just(page));
                                    }
                                    return Mono.just(page);
                                })
                                .flatMap(page ->
                                        reactiveBlockRepository.save(Block.builder()
                                                .pageId(pageId)
                                                .parentBlockId(uuidUtils.fromString(request.getParentBlockId()))
                                                .type(request.getType())
                                                .content(request.getContent())
                                                .position(request.getPosition() != null ? request.getPosition() : 0)
                                                .isArchived(false)
                                                .createdAt(LocalDateTime.now())
                                                .updatedAt(LocalDateTime.now())
                                                .createdBy(customUserDetails.getId())
                                                .lastEditedBy(customUserDetails.getId())
                                                .build()
                                        )
                                )
                )
                .map(BlockCreateResponseDto::from)
                .onErrorMap(throwable -> {
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

        return ReactiveSecurityContextHolder.getContext()
                .map(securityContext -> (CustomUserDetails) securityContext.getAuthentication().getPrincipal())
                .switchIfEmpty(Mono.error(new UserException(ErrorCode.AUTHENTICATION_FAILED)))
                .flatMap(customUserDetails ->
                        reactivePageRepository.findByIdAndWorkspaceId(pageId, workspaceId)
                                .switchIfEmpty(Mono.error(new PageException(ErrorCode.PAGE_NOT_FOUND)))
                                .flatMap(page ->
                                        reactiveWorkspaceRepository.findById(workspaceId)
                                                .switchIfEmpty(Mono.error(new WorkspaceException(ErrorCode.WORKSPACE_NOT_FOUND)))
                                                .flatMap(workspace -> {
                                                    if (workspace.getCreatedBy().equals(customUserDetails.getId())) {
                                                        return Mono.just(page); // 소유자는 무조건 읽기 가능
                                                    }

                                                    // 워크스페이스 멤버이면서 페이지 읽기 권한이 있는지 확인
                                                    return reactiveWorkspaceMemberRepository.existsByWorkspaceIdAndUserId(workspaceId, customUserDetails.getId())
                                                            .flatMap(isMember -> {
                                                                if (isMember) {
                                                                    return reactivePagePermissionRepository.findByPageIdAndUserId(pageId, customUserDetails.getId())
                                                                            .flatMap(permission -> {
                                                                                PagePermissionType permissionType = PagePermissionType.valueOf(permission.getPermission());
                                                                                if (permissionType.getLevel() >= PagePermissionType.READ.getLevel()) {
                                                                                    return Mono.just(page);
                                                                                }
                                                                                return Mono.error(new PagePermissionException(ErrorCode.PAGE_PERMISSION_DENIED));
                                                                            })
                                                                            .switchIfEmpty(Mono.error(new PagePermissionException(ErrorCode.PAGE_PERMISSION_DENIED)));
                                                                } else {
                                                                    // 워크스페이스 멤버가 아닌 경우 페이지 공개 여부 확인
                                                                    if (page.isPublic()) {
                                                                        return Mono.just(page);
                                                                    }
                                                                    return Mono.error(new PagePermissionException(ErrorCode.PAGE_PERMISSION_DENIED));
                                                                }
                                                            });
                                                })
                                )
                                .flatMap(page ->
                                        reactiveBlockRepository.findAllByPageIdAndIsArchivedFalseOrderByPositionAsc(pageId)
                                                .map(block ->
                                                        BlockListItemResponseDto.builder()
                                                                .blockId(uuidUtils.fromUUID(block.getId()))
                                                                .parentBlockId(uuidUtils.fromUUID(block.getParentBlockId()))
                                                                .type(block.getType())
                                                                .position(block.getPosition())
                                                                .build()
                                                )
                                                .collectList()
                                                .map(BlockListResponseDto::new)
                                )
                )
                .onErrorMap(throwable -> {
                    if (throwable instanceof UserException ||
                            throwable instanceof PageException ||
                            throwable instanceof PagePermissionException ||
                            throwable instanceof WorkspaceException ||
                            throwable instanceof BlockException ||
                            throwable instanceof UuidException) {
                        return throwable;
                    }
                    return new BlockException(ErrorCode.UNEXPECTED_ERROR);
                });
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

        return ReactiveSecurityContextHolder.getContext()
                .map(securityContext -> (CustomUserDetails) securityContext.getAuthentication().getPrincipal())
                .switchIfEmpty(Mono.error(new UserException(ErrorCode.AUTHENTICATION_FAILED)))
                .flatMap(customUserDetails ->
                        // 블록 존재 확인
                        reactiveBlockRepository.findByIdAndPageId(blockId, pageId)
                                .switchIfEmpty(Mono.error(new BlockException(ErrorCode.BLOCK_NOT_FOUND)))
                                .flatMap(block ->
                                        // 페이지 존재 확인
                                        reactivePageRepository.findByIdAndWorkspaceId(pageId, workspaceId)
                                                .switchIfEmpty(Mono.error(new PageException(ErrorCode.PAGE_NOT_FOUND)))
                                                .flatMap(page ->
                                                        // 워크스페이스 소유자인지 확
                                                        reactiveWorkspaceRepository.findById(workspaceId)
                                                                .switchIfEmpty(Mono.error(new WorkspaceException(ErrorCode.WORKSPACE_NOT_FOUND)))
                                                                .flatMap(workspace -> {
                                                                    if (workspace.getCreatedBy().equals(customUserDetails.getId())) {
                                                                        return Mono.just(block); // 소유자는 무조건 읽기 가능
                                                                    }

                                                                    // 워크스페이스 멤버이면서 페이지 읽기 권한이 있는지 확인
                                                                    return reactiveWorkspaceMemberRepository.existsByWorkspaceIdAndUserId(workspaceId, customUserDetails.getId())
                                                                            .flatMap(isMember -> {
                                                                                if (isMember) {
                                                                                    return reactivePagePermissionRepository.findByPageIdAndUserId(pageId, customUserDetails.getId())
                                                                                            .flatMap(permission -> {
                                                                                                PagePermissionType permissionType = PagePermissionType.valueOf(permission.getPermission());
                                                                                                if (permissionType.getLevel() >= PagePermissionType.READ.getLevel()) {
                                                                                                    return Mono.just(block);
                                                                                                }
                                                                                                return Mono.error(new PagePermissionException(ErrorCode.PAGE_PERMISSION_DENIED));
                                                                                            })
                                                                                            .switchIfEmpty(Mono.error(new PagePermissionException(ErrorCode.PAGE_PERMISSION_DENIED)));
                                                                                } else {
                                                                                    // 워크스페이스 멤버가 아닌 경우 페이지 공개 여부 확인
                                                                                    if (page.isPublic()) {
                                                                                        return Mono.just(block);
                                                                                    }
                                                                                    return Mono.error(new PagePermissionException(ErrorCode.PAGE_PERMISSION_DENIED));
                                                                                }
                                                                            });
                                                                })
                                                )
                                )
                )
                .map(BlockResponseDto::from)
                .onErrorMap(throwable -> {
                    if (throwable instanceof UserException ||
                            throwable instanceof PageException ||
                            throwable instanceof PagePermissionException ||
                            throwable instanceof WorkspaceException ||
                            throwable instanceof BlockException ||
                            throwable instanceof UuidException) {
                        return throwable;
                    }
                    return new BlockException(ErrorCode.UNEXPECTED_ERROR);
                });
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
    public Mono<BlockResponseDto> updateBlock(String workspaceIdStr, String pageIdStr, String blockIdStr, BlockUpdateRequestDto request) {
        UUID workspaceId = uuidUtils.fromString(workspaceIdStr);
        UUID pageId = uuidUtils.fromString(pageIdStr);
        UUID blockId = uuidUtils.fromString(blockIdStr);

        return ReactiveSecurityContextHolder.getContext()
                .map(securityContext -> (CustomUserDetails) securityContext.getAuthentication().getPrincipal())
                .switchIfEmpty(Mono.error(new UserException(ErrorCode.AUTHENTICATION_FAILED)))
                .flatMap(customUserDetails ->
                        // 블록 존재 확인
                        reactiveBlockRepository.findByIdAndPageId(blockId, pageId)
                                .switchIfEmpty(Mono.error(new BlockException(ErrorCode.BLOCK_NOT_FOUND)))
                                .flatMap(block ->
                                        // 페이지 존재 확인
                                        reactivePageRepository.findByIdAndWorkspaceId(pageId, workspaceId)
                                                .switchIfEmpty(Mono.error(new PageException(ErrorCode.PAGE_NOT_FOUND)))
                                                .flatMap(page ->
                                                        // 워크스페이스 소유자인지 확인
                                                        reactiveWorkspaceRepository.findById(workspaceId)
                                                                .switchIfEmpty(Mono.error(new WorkspaceException(ErrorCode.WORKSPACE_NOT_FOUND)))
                                                                .flatMap(workspace -> {
                                                                    if (workspace.getCreatedBy().equals(customUserDetails.getId())) {
                                                                        return Mono.just(block); // 소유자는 무조건 편집 가능
                                                                    }

                                                                    // 워크스페이스 멤버이면서 페이지 편집 권한이 있는지 확인
                                                                    return reactiveWorkspaceMemberRepository.existsByWorkspaceIdAndUserId(workspaceId, customUserDetails.getId())
                                                                            .flatMap(isMember -> {
                                                                                if (!isMember) {
                                                                                    return Mono.error(new WorkspaceMemberException(ErrorCode.MEMBER_NOT_FOUND));
                                                                                }

                                                                                return reactivePagePermissionRepository.findByPageIdAndUserId(pageId, customUserDetails.getId())
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
                                                .flatMap(blockToUpdate -> {
                                                    // 블록 수정
                                                    if (request.getType() != null) {
                                                        blockToUpdate.setType(request.getType());
                                                    }
                                                    if (request.getContent() != null) {
                                                        blockToUpdate.setContent(request.getContent());
                                                    }
                                                    if (request.getPosition() != null) {
                                                        blockToUpdate.setPosition(request.getPosition());
                                                    }
                                                    blockToUpdate.setUpdatedAt(LocalDateTime.now());
                                                    blockToUpdate.setLastEditedBy(customUserDetails.getId());

                                                    return reactiveBlockRepository.save(blockToUpdate);
                                                })
                                )
                )
                .map(BlockResponseDto::from)
                .onErrorMap(throwable -> {
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

        return ReactiveSecurityContextHolder.getContext()
                .map(securityContext -> (CustomUserDetails) securityContext.getAuthentication().getPrincipal())
                .switchIfEmpty(Mono.error(new UserException(ErrorCode.AUTHENTICATION_FAILED)))
                .flatMap(customUserDetails ->
                        // 블록 존재 확인
                        reactiveBlockRepository.findByIdAndPageId(blockId, pageId)
                                .switchIfEmpty(Mono.error(new BlockException(ErrorCode.BLOCK_NOT_FOUND)))
                                .flatMap(block ->
                                        reactivePageRepository.findByIdAndWorkspaceId(pageId, workspaceId)
                                                .switchIfEmpty(Mono.error(new PageException(ErrorCode.PAGE_NOT_FOUND)))
                                                .flatMap(page ->
                                                        reactiveWorkspaceRepository.findById(workspaceId)
                                                                .switchIfEmpty(Mono.error(new WorkspaceException(ErrorCode.WORKSPACE_NOT_FOUND)))
                                                                .flatMap(workspace -> {
                                                                    if (workspace.getCreatedBy().equals(customUserDetails.getId())) {
                                                                        return Mono.just(block);
                                                                    }

                                                                    return reactiveWorkspaceMemberRepository.existsByWorkspaceIdAndUserId(workspaceId, customUserDetails.getId())
                                                                            .flatMap(isMember -> {
                                                                                if (!isMember) {
                                                                                    return Mono.error(new WorkspaceMemberException(ErrorCode.MEMBER_NOT_FOUND));
                                                                                }

                                                                                return reactivePagePermissionRepository.findByPageIdAndUserId(pageId, customUserDetails.getId())
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
                                                .flatMap(blockToMove -> {
                                                    // 자기 자신을 부모로 설정하는 것 방지
                                                    if (request.getNewParentBlockId() != null && request.getNewParentBlockId().equals(blockId.toString())) {
                                                        return Mono.error(new BlockException(ErrorCode.CANNOT_MOVE_TO_SELF));
                                                    }

                                                    // 새 부모 블록이 있는 경우 존재 확인
                                                    if (request.getNewParentBlockId() != null) {
                                                        return reactiveBlockRepository.findByIdAndPageId(uuidUtils.fromString(request.getNewParentBlockId()), pageId)
                                                                .switchIfEmpty(Mono.error(new BlockException(ErrorCode.PARENT_BLOCK_NOT_FOUND)))
                                                                .then(Mono.just(blockToMove));
                                                    }
                                                    return Mono.just(blockToMove);
                                                })
                                                .flatMap(blockToMove -> {
                                                    blockToMove.setParentBlockId(uuidUtils.fromString(request.getNewParentBlockId()));
                                                    blockToMove.setPosition(request.getNewPosition());
                                                    blockToMove.setUpdatedAt(LocalDateTime.now());
                                                    blockToMove.setLastEditedBy(customUserDetails.getId());

                                                    return reactiveBlockRepository.save(blockToMove);
                                                })
                                )
                )
                .map(BlockResponseDto::from)
                .onErrorMap(throwable -> {
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

        return ReactiveSecurityContextHolder.getContext()
                .map(securityContext -> (CustomUserDetails) securityContext.getAuthentication().getPrincipal())
                .switchIfEmpty(Mono.error(new UserException(ErrorCode.AUTHENTICATION_FAILED)))
                .flatMap(customUserDetails ->
                        reactiveBlockRepository.findByIdAndPageId(blockId, pageId)
                                .switchIfEmpty(Mono.error(new BlockException(ErrorCode.BLOCK_NOT_FOUND)))
                                .flatMap(block ->
                                        reactivePageRepository.findByIdAndWorkspaceId(pageId, workspaceId)
                                                .switchIfEmpty(Mono.error(new PageException(ErrorCode.PAGE_NOT_FOUND)))
                                                .flatMap(page ->
                                                        reactiveWorkspaceRepository.findById(workspaceId)
                                                                .switchIfEmpty(Mono.error(new WorkspaceException(ErrorCode.WORKSPACE_NOT_FOUND)))
                                                                .flatMap(workspace -> {
                                                                    if (workspace.getCreatedBy().equals(customUserDetails.getId())) {
                                                                        return Mono.just(block);
                                                                    }

                                                                    return reactiveWorkspaceMemberRepository.existsByWorkspaceIdAndUserId(workspaceId, customUserDetails.getId())
                                                                            .flatMap(isMember -> {
                                                                                if (!isMember) {
                                                                                    return Mono.error(new WorkspaceMemberException(ErrorCode.MEMBER_NOT_FOUND));
                                                                                }

                                                                                return reactivePagePermissionRepository.findByPageIdAndUserId(pageId, customUserDetails.getId())
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
                                )
                                .flatMap(block ->
                                        // CTE를 사용하여 블록과 하위 블록을 일괄적으로 보관 처리
                                        reactiveBlockRepository.updateArchiveStatusForBlockTree(blockId, true, customUserDetails.getId())
                                                .thenReturn(BlockStatusResponseDto.builder().blockId(blockId.toString()).isArchived(true).build())
                                )
                )
                .onErrorMap(throwable -> {
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

        return ReactiveSecurityContextHolder.getContext()
                .map(securityContext -> (CustomUserDetails) securityContext.getAuthentication().getPrincipal())
                .switchIfEmpty(Mono.error(new UserException(ErrorCode.AUTHENTICATION_FAILED)))
                .flatMap(customUserDetails ->
                        reactiveBlockRepository.findByIdAndPageId(blockId, pageId)
                                .switchIfEmpty(Mono.error(new BlockException(ErrorCode.BLOCK_NOT_FOUND)))
                                .flatMap(block ->
                                        reactivePageRepository.findByIdAndWorkspaceId(pageId, workspaceId)
                                                .switchIfEmpty(Mono.error(new PageException(ErrorCode.PAGE_NOT_FOUND)))
                                                .flatMap(page ->
                                                        reactiveWorkspaceRepository.findById(workspaceId)
                                                                .switchIfEmpty(Mono.error(new WorkspaceException(ErrorCode.WORKSPACE_NOT_FOUND)))
                                                                .flatMap(workspace -> {
                                                                    if (workspace.getCreatedBy().equals(customUserDetails.getId())) {
                                                                        return Mono.just(block);
                                                                    }

                                                                    return reactiveWorkspaceMemberRepository.existsByWorkspaceIdAndUserId(workspaceId, customUserDetails.getId())
                                                                            .flatMap(isMember -> {
                                                                                if (!isMember) {
                                                                                    return Mono.error(new WorkspaceMemberException(ErrorCode.MEMBER_NOT_FOUND));
                                                                                }

                                                                                return reactivePagePermissionRepository.findByPageIdAndUserId(pageId, customUserDetails.getId())
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
                                )
                                .flatMap(block ->
                                        // CTE를 사용하여 블록과 하위 블록을 일괄적으로 복원 처리
                                        reactiveBlockRepository.updateArchiveStatusForBlockTree(blockId, false, customUserDetails.getId())
                                                .thenReturn(BlockStatusResponseDto.builder().blockId(blockId.toString()).isArchived(false).build())
                                )
                )
                .onErrorMap(throwable -> {
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
}
