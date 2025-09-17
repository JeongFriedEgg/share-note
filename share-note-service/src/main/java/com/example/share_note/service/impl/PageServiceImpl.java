package com.example.share_note.service.impl;

import com.example.share_note.domain.Page;
import com.example.share_note.domain.PagePermission;
import com.example.share_note.domain.Workspace;
import com.example.share_note.dto.CustomUserDetails;
import com.example.share_note.dto.page.*;
import com.example.share_note.dto.page.PageListItemResponseDto;
import com.example.share_note.dto.page.PageListResponseDto;
import com.example.share_note.dto.page.PageUpdatePermissionResponseDto;
import com.example.share_note.dto.page.PageResponseDto;
import com.example.share_note.enums.PagePermissionType;
import com.example.share_note.exception.*;
import com.example.share_note.repository.*;
import com.example.share_note.service.PageService;
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
public class PageServiceImpl implements PageService {

    private final ReactivePageRepository reactivePageRepository;
    private final ReactivePagePermissionRepository reactivePagePermissionRepository;
    private final ReactiveWorkspaceRepository reactiveWorkspaceRepository;
    private final ReactiveWorkspaceMemberRepository reactiveWorkspaceMemberRepository;
    private final ReactiveBlockRepository reactiveBlockRepository;
    private final UuidUtils uuidUtils;

    /**
     * 페이지 생성
     * <p>
     * 1. 인증 처리
     * 2. 워크스페이스 존재 유무 확인
     * 3. 클라이언트가 워크스페이스의 소유자 또는 멤버인지 확인
     * 4. 부모 페이지의 존재 유무 확인
     * 5. 부모 페이지가 있는 경우 부모 페이지에 대한 권한 확인
     *
     * @param workspaceIdStr
     * @param request
     * @return
     */
    public Mono<PageCreateResponseDto> createPage(String workspaceIdStr, PageCreateRequestDto request) {
        UUID workspaceId = uuidUtils.fromString(workspaceIdStr);

        return handleStandardExceptions(
                getCurrentUser()
                        .flatMap(customUserDetails ->
                                findWorkspaceById(workspaceId)
                                        .flatMap(workspace -> {
                                            // 워크스페이스 소유자 또는 멤버인지 확인
                                            if (workspace.getCreatedBy().equals(customUserDetails.getId())) {
                                                return Mono.just(customUserDetails);
                                            }
                                            return reactiveWorkspaceMemberRepository.existsByWorkspaceIdAndUserId(workspaceId, customUserDetails.getId())
                                                    .flatMap(exists -> {
                                                        if (!exists) {
                                                            return Mono.error(new WorkspaceMemberException(ErrorCode.MEMBER_NOT_FOUND));
                                                        }
                                                        return Mono.just(customUserDetails);
                                                    });
                                        })
                                        .flatMap(customUserDetailsAfterAccessCheck -> {
                                            // 부모 페이지가 없는 경우는 바로 페이지 생성
                                            if (request.getParentPageId() == null) {
                                                return Mono.just(customUserDetailsAfterAccessCheck);
                                            }
                                            // 부모 페이지 존재 및 권한 확인
                                            return reactivePageRepository.findByIdAndWorkspaceId(uuidUtils.fromString(request.getParentPageId()), workspaceId)
                                                    .switchIfEmpty(Mono.error(new PageException(ErrorCode.PARENT_PAGE_NOT_FOUND)))
                                                    .flatMap(parentPage ->
                                                            reactivePagePermissionRepository.findByPageIdAndUserId(parentPage.getId(), customUserDetailsAfterAccessCheck.getId())
                                                                    .flatMap(permission -> {
                                                                        PagePermissionType permissionType = PagePermissionType.valueOf(permission.getPermission());
                                                                        if (permissionType.getLevel() >= PagePermissionType.EDIT.getLevel()) {
                                                                            return Mono.just(customUserDetailsAfterAccessCheck);
                                                                        } else {
                                                                            return Mono.error(new PagePermissionException(ErrorCode.PARENT_PAGE_PERMISSION_DENIED));
                                                                        }
                                                                    })
                                                                    .switchIfEmpty(Mono.error(new PagePermissionException(ErrorCode.PARENT_PAGE_PERMISSION_DENIED)))
                                                    );
                                        })
                                        .flatMap(finalCustomUserDetails ->
                                                createAndSavePage(workspaceId, request, finalCustomUserDetails.getId())
                                        )
                        )
                        .map(PageCreateResponseDto::from)
        );
    }

    /**
     * 페이지 목록 조회
     * <p>
     * 1. 인증 처리
     * 2. 워크스페이스 존재 유무 확인
     * 3. 클라이언트가 워크스페이스의 소유자 또는 멤버인지 확인
     * 4. 페이지 목록 조회(페이지 목록 조회는 모든 소유자, 멤벙에게 제공)
     *
     * @param workspaceIdStr
     * @return
     */
    public Mono<PageListResponseDto> getPages(String workspaceIdStr) {
        UUID workspaceId = uuidUtils.fromString(workspaceIdStr);

        return handleStandardExceptions(
                getCurrentUser()
                        .flatMap(user ->
                                findWorkspaceById(workspaceId)
                                        .flatMap(workspace -> validateWorkspaceAccess(workspaceId, user, workspace))
                                        .flatMap(validatedUser ->
                                                reactivePageRepository.findAllByWorkspaceIdAndParentPageIdIsNull(workspaceId)
                                                        .map(page -> PageListItemResponseDto.builder()
                                                                .pageId(page.getId().toString())
                                                                .title(page.getTitle())
                                                                .icon(page.getIcon())
                                                                .build())
                                                        .collectList()
                                                        .map(PageListResponseDto::new)
                                        )
                        )
        );
    }

    /**
     * 페이지 조회
     * <p>
     * 1. 인증 처리
     * 2. 워크스페이스 존재 유무 확인
     * 3. 클라이언트가 워크스페이스의 소유자 또는 멤버인지 확인
     * 4. 페이지 존재 유무 확인
     * - 클라이언트가 멤버이면 읽기 권한 확인 -> 읽기 권한이 있으면 페이지를 볼 수 있음
     * - 클라이언트가 멤버가 아니면 페이지의 공개 상태를 확인 -> 공개 상태이면 페이지를 볼 수 있음
     *
     * @param workspaceIdStr
     * @param pageIdStr
     * @return
     */
    public Mono<PageResponseDto> getPage(String workspaceIdStr, String pageIdStr) {
        UUID workspaceId = uuidUtils.fromString(workspaceIdStr);
        UUID pageId = uuidUtils.fromString(pageIdStr);

        return handleStandardExceptions(
                getCurrentUser()
                        .flatMap(user ->
                                findWorkspaceById(workspaceId)
                                        .flatMap(workspace -> checkWorkspaceMembership(workspaceId, user.getId(), workspace)
                                                .flatMap(isMember ->
                                                        findPageByIdAndWorkspaceId(pageId, workspaceId)
                                                                .flatMap(page -> validatePageReadAccess(pageId, user.getId(), isMember, page))
                                                )
                                        )
                        )
                        .map(PageResponseDto::from)
        );
    }

    /**
     * 페이지 수정
     * <p>
     * 1. 인증 처리
     * 2. 워크스페이스 존재 유무 확인
     * 3. 클라이언트가 워크스페이스의 소유자 또는 멤버인지 확인
     * 4. 페이지 존재 유무 확인
     * 5. 페이지 수정
     *
     * @param workspaceIdStr
     * @param pageIdStr
     * @param request
     * @return
     */
    public Mono<PageResponseDto> updatePage(String workspaceIdStr, String pageIdStr, PageUpdateRequestDto request) {
        UUID workspaceId = uuidUtils.fromString(workspaceIdStr);
        UUID pageId = uuidUtils.fromString(pageIdStr);

        return handleStandardExceptions(
                getCurrentUser()
                        .flatMap(user ->
                                findWorkspaceById(workspaceId)
                                        .flatMap(workspace ->
                                                findPageByIdAndWorkspaceId(pageId, workspaceId)
                                                        .flatMap(page -> validatePageEditAccess(workspaceId, pageId, user.getId(), workspace, page))
                                                        .map(page -> updatePageFields(page, request, user.getId()))
                                                        .flatMap(reactivePageRepository::save)
                                        )
                        )
                        .map(PageResponseDto::from)
        );
    }

    /**
     * 페이지에 멤버 초대 및 권한 설정(워크스페이스에 있는 멤버만)
     * <p>
     * 1. 인증 처리
     * 2. 워크스페이스 존재 유무 확인
     * 3. 클라이언트가 워크스페이스의 소유자 또는 멤버인지 확인
     * 4. 페이지 존재 유무 확인
     * 5. 클라이언트가 페이지에 대한 Full Access 권한이 있는지 확인
     * 6. 초대할 사용자가 워크스페이스 멤버인지 확인
     * 7. 초대할 사용자가 이미 페이지에 대한 권한이 있는지 확인
     * 8. 페이지 권한 저장
     *
     * @param workspaceIdStr
     * @param pageIdStr
     * @param request
     * @return
     */
    public Mono<PageInviteResponseDto> inviteMemberToPage(String workspaceIdStr, String pageIdStr, PageInviteRequestDto request) {
        UUID workspaceId = uuidUtils.fromString(workspaceIdStr);
        UUID pageId = uuidUtils.fromString(pageIdStr);
        UUID invitedUserId = uuidUtils.fromString(request.getUserId());

        return handleStandardExceptions(
                getCurrentUser()
                        .flatMap(user ->
                                findWorkspaceById(workspaceId)
                                        .flatMap(workspace ->
                                                findPageByIdAndWorkspaceId(pageId, workspaceId)
                                                        .flatMap(page -> validatePageFullAccess(pageId, user.getId(), workspace, page))
                                                        .flatMap(page ->
                                                                reactiveWorkspaceMemberRepository.existsByWorkspaceIdAndUserId(workspaceId, invitedUserId)
                                                                        .flatMap(isInvitedUserWorkspaceMember -> {
                                                                            if (!isInvitedUserWorkspaceMember) {
                                                                                return Mono.error(new WorkspaceMemberException(ErrorCode.INVITED_USER_NOT_WORKSPACE_MEMBER));
                                                                            }

                                                                            return reactivePagePermissionRepository.findByPageIdAndUserId(pageId, invitedUserId)
                                                                                    .flatMap(existingPermission -> {
                                                                                        existingPermission.setPermission(request.getPermissionType());
                                                                                        return reactivePagePermissionRepository.save(existingPermission)
                                                                                                .map(PageInviteResponseDto::from);
                                                                                    })
                                                                                    .switchIfEmpty(
                                                                                            reactivePagePermissionRepository.save(PagePermission.builder()
                                                                                                            .pageId(pageId)
                                                                                                            .userId(invitedUserId)
                                                                                                            .permission(request.getPermissionType())
                                                                                                            .build())
                                                                                                    .map(PageInviteResponseDto::from)
                                                                                    );
                                                                        })
                                                        )
                                        )
                        )
        );
    }


    /**
     * 페이지 멤버 권한 수정(전체, 편집, 댓글, 읽기 허용)
     * <p>
     * 1. 인증 처리
     * 2. 워크스페이스 존재 유무 확인
     * 3. 클라이언트가 워크스페이스의 소유자 또는 멤버인지 확인
     * 4. 페이지 존재 유무 확인
     * 5. 클라이언트가 페이지에 대한 Full Access 권한이 있는지 확인
     * 6. 수정할 대상 사용자가 워크스페이스 멤버인지 확인
     * 7. 수정할 대상 사용자가 페이지의 소유자(createdBy)가 아닌지 확인
     * 8. 페이지 권한 수정
     *
     * @param workspaceIdStr
     * @param pageIdStr
     * @param targetUserIdStr
     * @param request
     * @return
     */
    public Mono<PageUpdatePermissionResponseDto> updateMemberPagePermission(
            String workspaceIdStr, String pageIdStr, String targetUserIdStr, PageUpdatePermissionRequestDto request) {
        UUID workspaceId = uuidUtils.fromString(workspaceIdStr);
        UUID pageId = uuidUtils.fromString(pageIdStr);
        UUID targetUserId = uuidUtils.fromString(targetUserIdStr);

        return handleStandardExceptions(
                getCurrentUser()
                        .flatMap(user ->
                                findWorkspaceById(workspaceId)
                                        .flatMap(workspace ->
                                                findPageByIdAndWorkspaceId(pageId, workspaceId)
                                                        .flatMap(page -> {
                                                            // 워크스페이스 소유자가 아닌 경우 FULL_ACCESS 권한 확인
                                                            if (!workspace.getCreatedBy().equals(user.getId())) {
                                                                return validatePageFullAccess(pageId, user.getId(), workspace, page);
                                                            }
                                                            return Mono.just(page);
                                                        })
                                                        .flatMap(page ->
                                                                reactiveWorkspaceMemberRepository.existsByWorkspaceIdAndUserId(workspaceId, targetUserId)
                                                                        .flatMap(isTargetUserWorkspaceMember -> {
                                                                            if (!isTargetUserWorkspaceMember) {
                                                                                return Mono.error(new WorkspaceMemberException(ErrorCode.INVITED_USER_NOT_WORKSPACE_MEMBER));
                                                                            }
                                                                            if (page.getCreatedBy().equals(targetUserId)) {
                                                                                return Mono.error(new PagePermissionException(ErrorCode.CANNOT_CHANGE_OWNER_PERMISSION));
                                                                            }

                                                                            return reactivePagePermissionRepository.findByPageIdAndUserId(pageId, targetUserId)
                                                                                    .flatMap(existingPermission -> {
                                                                                        existingPermission.setPermission(request.getPermissionType());
                                                                                        return reactivePagePermissionRepository.save(existingPermission)
                                                                                                .map(PageUpdatePermissionResponseDto::from);
                                                                                    })
                                                                                    .switchIfEmpty(Mono.error(new PagePermissionException(ErrorCode.PAGE_PERMISSION_NOT_FOUND)));
                                                                        })
                                                        )
                                        )
                        )
        );
    }

    /**
     * 페이지 공개 상태 설정(초대받은 사용자만 or 링크가 있는 웹의 모든 사용자)
     * <p>
     * 1. 인증 처리
     * 2. 워크스페이스 존재 유무 확인
     * 3. 클라이언트가 워크스페이스의 소유자 또는 멤버인지 확인
     * 4. 페이지 존재 유무 확인
     * 5. 클라이언트가 페이지에 대한 Full Access 권한이 있는지 확인
     * 6. 페이지 공개 상태 수정
     *
     * @param workspaceIdStr
     * @param pageIdStr
     * @param request
     * @return
     */
    public Mono<PagePublicStatusUpdateResponseDto> updatePagePublicStatus(
            String workspaceIdStr, String pageIdStr, PagePublicStatusUpdateRequestDto request) {
        UUID workspaceId = uuidUtils.fromString(workspaceIdStr);
        UUID pageId = uuidUtils.fromString(pageIdStr);

        return handleStandardExceptions(
                getCurrentUser()
                        .flatMap(user ->
                                findWorkspaceById(workspaceId)
                                        .flatMap(workspace ->
                                                findPageByIdAndWorkspaceId(pageId, workspaceId)
                                                        .flatMap(page -> {
                                                            // 워크스페이스 소유자가 아닌 경우 FULL_ACCESS 권한 확인
                                                            if (!workspace.getCreatedBy().equals(user.getId())) {
                                                                return validatePageFullAccess(pageId, user.getId(), workspace, page);
                                                            }
                                                            return Mono.just(page);
                                                        })
                                                        .flatMap(page -> {
                                                            page.setPublic(request.getIsPublic());
                                                            page.setUpdatedAt(LocalDateTime.now());
                                                            page.setLastEditedBy(user.getId());
                                                            return reactivePageRepository.save(page)
                                                                    .map(PagePublicStatusUpdateResponseDto::from);
                                                        })
                                        )
                        )
        );
    }


    /**
     * 페이지 보관(soft delete)
     * <p>
     * 1. 인증 및 권한 확인
     * 2. 페이지 존재 유무 확인
     * 3. 페이지 소유자(createdBy)가 아닌 경우, Full Access 권한이 있는지 확인
     * 4. 현재 페이지와 모든 하위 페이지 및 블록을 보관 처리
     *
     * @param workspaceIdStr
     * @param pageIdStr
     * @return
     */
    public Mono<PageStatusResponseDto> archivePage(String workspaceIdStr, String pageIdStr) {
        UUID workspaceId = uuidUtils.fromString(workspaceIdStr);
        UUID pageId = uuidUtils.fromString(pageIdStr);

        return handleStandardExceptions(
                getCurrentUser()
                        .flatMap(user ->
                                findWorkspaceById(workspaceId)
                                        .flatMap(workspace ->
                                                findPageByIdAndWorkspaceId(pageId, workspaceId)
                                                        .flatMap(page -> {
                                                            if (page.getCreatedBy().equals(user.getId())) {
                                                                return Mono.just(page);
                                                            }
                                                            return reactivePagePermissionRepository.findByPageIdAndUserId(pageId, user.getId())
                                                                    .flatMap(permission -> {
                                                                        PagePermissionType permissionType = PagePermissionType.valueOf(permission.getPermission());
                                                                        if (permissionType != PagePermissionType.FULL_ACCESS) {
                                                                            return Mono.error(new PagePermissionException(ErrorCode.PAGE_PERMISSION_DENIED));
                                                                        }
                                                                        return Mono.just(page);
                                                                    })
                                                                    .switchIfEmpty(Mono.error(new PagePermissionException(ErrorCode.PAGE_PERMISSION_DENIED)));
                                                        })
                                                        .flatMap(page ->
                                                                reactivePageRepository.updateArchiveStatusForTree(pageId, true, page.getLastEditedBy())
                                                                        .then(reactiveBlockRepository.updateArchiveStatusForPageTree(pageId, true))
                                                                        .thenReturn(PageStatusResponseDto.builder().pageId(pageIdStr).isArchived(true).build())
                                                        )
                                        )
                        )
        );
    }

    /**
     * 페이지 복원
     * <p>
     * 1. 인증 및 권한 확인
     * 2. 페이지 존재 유무 확인
     * 3. 페이지 소유자(createdBy)가 아닌 경우, Full Access 권한이 있는지 확인
     * 4. 현재 페이지와 모든 하위 페이지 및 블록을 복원 처리
     *
     * @param workspaceIdStr
     * @param pageIdStr
     * @return
     */
    public Mono<PageStatusResponseDto> restorePage(String workspaceIdStr, String pageIdStr) {
        UUID workspaceId = uuidUtils.fromString(workspaceIdStr);
        UUID pageId = uuidUtils.fromString(pageIdStr);

        return handleStandardExceptions(
                getCurrentUser()
                        .flatMap(user ->
                                findWorkspaceById(workspaceId)
                                        .flatMap(workspace ->
                                                findPageByIdAndWorkspaceId(pageId, workspaceId)
                                                        .flatMap(page -> {
                                                            if (page.getCreatedBy().equals(user.getId())) {
                                                                return Mono.just(page);
                                                            }
                                                            return reactivePagePermissionRepository.findByPageIdAndUserId(pageId, user.getId())
                                                                    .flatMap(permission -> {
                                                                        PagePermissionType permissionType = PagePermissionType.valueOf(permission.getPermission());
                                                                        if (permissionType != PagePermissionType.FULL_ACCESS) {
                                                                            return Mono.error(new PagePermissionException(ErrorCode.PAGE_PERMISSION_DENIED));
                                                                        }
                                                                        return Mono.just(page);
                                                                    })
                                                                    .switchIfEmpty(Mono.error(new PagePermissionException(ErrorCode.PAGE_PERMISSION_DENIED)));
                                                        })
                                                        .flatMap(page ->
                                                                reactivePageRepository.updateArchiveStatusForTree(pageId, false, page.getLastEditedBy())
                                                                        .then(reactiveBlockRepository.updateArchiveStatusForPageTree(pageId, false))
                                                                        .thenReturn(PageStatusResponseDto.builder().pageId(pageIdStr).isArchived(false).build())
                                                        )
                                        )
                        )
        );
    }

    /**
     * 페이지 삭제 (hard delete)
     * <p>
     * 1. 인증 및 권한 확인
     * 2. 페이지 존재 유무 확인
     * 3. 페이지 소유자(createdBy)가 아닌 경우, Full Access 권한이 있는지 확인
     * 4. 현재 페이지와 모든 하위 페이지 및 블록을 영구 삭제
     *
     * @param workspaceIdStr
     * @param pageIdStr
     * @return
     */
    public Mono<Void> deletePage(String workspaceIdStr, String pageIdStr) {
        UUID workspaceId = uuidUtils.fromString(workspaceIdStr);
        UUID pageId = uuidUtils.fromString(pageIdStr);

        return handleStandardExceptions(
                getCurrentUser()
                        .flatMap(user ->
                                findWorkspaceById(workspaceId)
                                        .flatMap(workspace ->
                                                findPageByIdAndWorkspaceId(pageId, workspaceId)
                                                        .flatMap(page -> {
                                                            if (page.getCreatedBy().equals(user.getId())) {
                                                                return Mono.just(page);
                                                            }
                                                            return reactivePagePermissionRepository.findByPageIdAndUserId(pageId, user.getId())
                                                                    .flatMap(permission -> {
                                                                        PagePermissionType permissionType = PagePermissionType.valueOf(permission.getPermission());
                                                                        if (permissionType != PagePermissionType.FULL_ACCESS) {
                                                                            return Mono.error(new PagePermissionException(ErrorCode.PAGE_PERMISSION_DENIED));
                                                                        }
                                                                        return Mono.just(page);
                                                                    })
                                                                    .switchIfEmpty(Mono.error(new PagePermissionException(ErrorCode.PAGE_PERMISSION_DENIED)));
                                                        })
                                                        .flatMap(page ->
                                                                reactiveBlockRepository.deleteAllByPageTree(pageId)
                                                                        .then(reactivePageRepository.deletePageAndDescendants(pageId))
                                                                        .then()
                                                        )
                                        )
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
     * 워크스페이스 존재 확인
     */
    private Mono<Workspace> findWorkspaceById(UUID workspaceId) {
        return reactiveWorkspaceRepository.findById(workspaceId)
                .switchIfEmpty(Mono.error(new WorkspaceException(ErrorCode.WORKSPACE_NOT_FOUND)));
    }

    /**
     * 페이지 존재 확인
     */
    private Mono<Page> findPageByIdAndWorkspaceId(UUID pageId, UUID workspaceId) {
        return reactivePageRepository.findByIdAndWorkspaceId(pageId, workspaceId)
                .switchIfEmpty(Mono.error(new PageException(ErrorCode.PAGE_NOT_FOUND)));
    }

    /**
     * 워크스페이스 소유자 또는 멤버 확인
     */
    private Mono<CustomUserDetails> validateWorkspaceAccess(UUID workspaceId, CustomUserDetails user, Workspace workspace) {
        if (workspace.getCreatedBy().equals(user.getId())) {
            return Mono.just(user);
        }
        return reactiveWorkspaceMemberRepository.existsByWorkspaceIdAndUserId(workspaceId, user.getId())
                .flatMap(exists -> {
                    if (!exists) {
                        return Mono.error(new WorkspaceMemberException(ErrorCode.MEMBER_NOT_FOUND));
                    }
                    return Mono.just(user);
                });
    }

    /**
     * 워크스페이스 멤버십 확인 (boolean 반환)
     */
    private Mono<Boolean> checkWorkspaceMembership(UUID workspaceId, UUID userId, Workspace workspace) {
        if (workspace.getCreatedBy().equals(userId)) {
            return Mono.just(true);
        }
        return reactiveWorkspaceMemberRepository.existsByWorkspaceIdAndUserId(workspaceId, userId);
    }

    /**
     * 페이지 읽기 권한 확인 (멤버십 + 권한 또는 공개 상태)
     */
    private Mono<Page> validatePageReadAccess(UUID pageId, UUID userId, boolean isMember, Page page) {
        if (isMember) {
            return reactivePagePermissionRepository.findByPageIdAndUserId(pageId, userId)
                    .flatMap(permission -> {
                        PagePermissionType permissionType = PagePermissionType.valueOf(permission.getPermission());
                        if (permissionType.getLevel() >= PagePermissionType.READ.getLevel()) {
                            return Mono.just(page);
                        }
                        return Mono.error(new PagePermissionException(ErrorCode.PAGE_PERMISSION_DENIED));
                    })
                    .switchIfEmpty(Mono.error(new PagePermissionException(ErrorCode.PAGE_PERMISSION_DENIED)));
        } else {
            if (page.isPublic()) {
                return Mono.just(page);
            }
            return Mono.error(new PagePermissionException(ErrorCode.PAGE_PERMISSION_DENIED));
        }
    }

    /**
     * 페이지 편집 권한 확인 (워크스페이스 소유자 또는 EDIT 이상 권한)
     */
    private Mono<Page> validatePageEditAccess(UUID workspaceId, UUID pageId, UUID userId, Workspace workspace, Page page) {
        if (workspace.getCreatedBy().equals(userId)) {
            return Mono.just(page);
        }

        return reactiveWorkspaceMemberRepository.existsByWorkspaceIdAndUserId(workspaceId, userId)
                .flatMap(isMember -> {
                    if (!isMember) {
                        return Mono.error(new PagePermissionException(ErrorCode.PAGE_PERMISSION_DENIED));
                    }

                    return reactivePagePermissionRepository.findByPageIdAndUserId(pageId, userId)
                            .flatMap(permission -> {
                                PagePermissionType permissionType = PagePermissionType.valueOf(permission.getPermission());
                                if (permissionType.getLevel() >= PagePermissionType.EDIT.getLevel()) {
                                    return Mono.just(page);
                                }
                                return Mono.error(new PagePermissionException(ErrorCode.PAGE_PERMISSION_DENIED));
                            })
                            .switchIfEmpty(Mono.error(new PagePermissionException(ErrorCode.PAGE_PERMISSION_DENIED)));
                });
    }

    /**
     * 페이지 FULL_ACCESS 권한 확인 (워크스페이스 소유자 또는 FULL_ACCESS 권한)
     */
    private Mono<Page> validatePageFullAccess(UUID pageId, UUID userId, Workspace workspace, Page page) {
        if (workspace.getCreatedBy().equals(userId)) {
            return Mono.just(page);
        }

        return reactivePagePermissionRepository.findByPageIdAndUserId(pageId, userId)
                .flatMap(permission -> {
                    PagePermissionType permissionType = PagePermissionType.valueOf(permission.getPermission());
                    if (permissionType != PagePermissionType.FULL_ACCESS) {
                        return Mono.error(new PagePermissionException(ErrorCode.PAGE_PERMISSION_DENIED));
                    }
                    return Mono.just(page);
                })
                .switchIfEmpty(Mono.error(new PagePermissionException(ErrorCode.PAGE_PERMISSION_DENIED)));
    }

    /**
     * 페이지 생성
     */
    private Mono<Page> createAndSavePage(UUID workspaceId, PageCreateRequestDto request, UUID userId) {
        return reactivePageRepository.save(Page.builder()
                .workspaceId(workspaceId)
                .parentPageId(uuidUtils.fromString(request.getParentPageId()))
                .title(request.getTitle() != null ? request.getTitle() : "Untitled")
                .icon(request.getIcon())
                .cover(request.getCover())
                .properties(request.getProperties())
                .isPublic(false)
                .isArchived(false)
                .isTemplate(false)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .createdBy(userId)
                .lastEditedBy(userId)
                .build());
    }

    /**
     * 페이지 필드 업데이트
     */
    private Page updatePageFields(Page page, PageUpdateRequestDto request, UUID userId) {
        if (request.getTitle() != null) {
            page.setTitle(request.getTitle());
        }
        if (request.getIcon() != null) {
            page.setIcon(request.getIcon());
        }
        if (request.getCover() != null) {
            page.setCover(request.getCover());
        }
        page.setUpdatedAt(LocalDateTime.now());
        page.setLastEditedBy(userId);
        return page;
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
                    throwable instanceof UuidException) {
                return throwable;
            }
            return new PageException(ErrorCode.UNEXPECTED_ERROR);
        });
    }
}
