package com.example.share_note.service.impl;

import com.example.share_note.domain.Page;
import com.example.share_note.domain.PagePermission;
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
        // 클라이언트의 인증 처리
        return ReactiveSecurityContextHolder.getContext()
                .map(securityContext -> (CustomUserDetails) securityContext.getAuthentication().getPrincipal())
                .switchIfEmpty(Mono.error(new UserException(ErrorCode.AUTHENTICATION_FAILED)))
                .flatMap(customUserDetails ->
                        // 워크스페이스 존재 유무 확인
                        reactiveWorkspaceRepository.findById(workspaceId)
                                .switchIfEmpty(Mono.error(new WorkspaceException(ErrorCode.WORKSPACE_NOT_FOUND)))
                                .flatMap(workspace -> {
                                    // 클라이언트가 워크스페이스의 소유자 또는 멤버인지 확인
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
                                    // 부모 페이지가 없는 경우는 워크스페이스에서의 페이지 생성이므로 바로 페이지 생성 로직으로 넘어감
                                    if (request.getParentPageId() == null) {
                                        return Mono.just(customUserDetailsAfterAccessCheck);
                                    }
                                    // 클라이언트 요청에서는 부모 페이지 ID 값을 받았지만 실제 DB 에서는 부모페이지가 존재하지 않는 경우
                                    return reactivePageRepository.findByIdAndWorkspaceId(uuidUtils.fromString(request.getParentPageId()), workspaceId)
                                            .switchIfEmpty(Mono.error(new PageException(ErrorCode.PARENT_PAGE_NOT_FOUND)))
                                            .flatMap(parentPage ->
                                                    // 클라이언트에서 부모 페이지 ID 값을 받았고 실제로도 부모페이지가 존재하는 경우
                                                    reactivePagePermissionRepository.findByPageIdAndUserId(parentPage.getId(), customUserDetailsAfterAccessCheck.getId())
                                                            .flatMap(permission -> {
                                                                // 클라이언트가 페이지에 대한 편집 권한이 있는지 확인
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
                                        reactivePageRepository.save(Page.builder()
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
                                                .createdBy(customUserDetails.getId())
                                                .lastEditedBy(customUserDetails.getId())
                                                .build())
                                )
                )
                .map(PageCreateResponseDto::from)
                .onErrorMap(throwable -> {
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
        // 클라이언트의 인증 처리
        return ReactiveSecurityContextHolder.getContext()
                .map(securityContext -> (CustomUserDetails) securityContext.getAuthentication().getPrincipal())
                .switchIfEmpty(Mono.error(new UserException(ErrorCode.AUTHENTICATION_FAILED)))
                .flatMap(customUserDetails ->
                        // 워크스페이스 존재 유무 확인
                        reactiveWorkspaceRepository.findById(workspaceId)
                                .switchIfEmpty(Mono.error(new WorkspaceException(ErrorCode.WORKSPACE_NOT_FOUND)))
                                .flatMap(workspace -> {
                                    // 클라이언트가 워크스페이스의 소유자 또는 멤버인지 확인
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
                )
                .flatMap(customUserDetails ->
                        reactivePageRepository.findAllByWorkspaceIdAndParentPageIdIsNull(workspaceId)
                                .map(page -> PageListItemResponseDto.builder()
                                        .pageId(page.getId().toString())
                                        .title(page.getTitle())
                                        .icon(page.getIcon())
                                        .build())
                                .collectList()
                                .map(PageListResponseDto::new)
                )
                .onErrorMap(throwable -> {
                    if (throwable instanceof UserException ||
                            throwable instanceof WorkspaceException ||
                            throwable instanceof WorkspaceMemberException ||
                            throwable instanceof UuidException) {
                        return throwable;
                    }
                    return new PageException(ErrorCode.UNEXPECTED_ERROR);
                });
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

        return ReactiveSecurityContextHolder.getContext()
                .map(securityContext -> (CustomUserDetails) securityContext.getAuthentication().getPrincipal())
                .switchIfEmpty(Mono.error(new UserException(ErrorCode.AUTHENTICATION_FAILED)))
                .flatMap(customUserDetails ->
                        // 워크스페이스 존재 유무 확인
                        reactiveWorkspaceRepository.findById(workspaceId)
                                .switchIfEmpty(Mono.error(new WorkspaceException(ErrorCode.WORKSPACE_NOT_FOUND)))
                                .flatMap(workspace -> {
                                    // 클라이언트가 워크스페이스의 소유자 또는 멤버인지 확인
                                    if (workspace.getCreatedBy().equals(customUserDetails.getId())) {
                                        return Mono.just(true);
                                    }
                                    // 클라이언트가 워크스페이스 멤버인지 확인
                                    return reactiveWorkspaceMemberRepository
                                            .existsByWorkspaceIdAndUserId(workspaceId, customUserDetails.getId());
                                })
                                .flatMap(isMember ->
                                        reactivePageRepository.findByIdAndWorkspaceId(pageId, workspaceId)
                                                .switchIfEmpty(Mono.error(new PageException(ErrorCode.PAGE_NOT_FOUND)))
                                                .flatMap(page -> {
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
                                                        if (page.isPublic()) {
                                                            return Mono.just(page);
                                                        }
                                                        return Mono.error(new PagePermissionException(ErrorCode.PAGE_PERMISSION_DENIED));
                                                    }
                                                })
                                )
                )
                .map(PageResponseDto::from)
                .onErrorMap(throwable -> {
                    if (throwable instanceof UserException ||
                            throwable instanceof PageException ||
                            throwable instanceof PagePermissionException ||
                            throwable instanceof WorkspaceException ||
                            throwable instanceof UuidException) {
                        return throwable;
                    }
                    return new PageException(ErrorCode.UNEXPECTED_ERROR);
                });
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

        return ReactiveSecurityContextHolder.getContext()
                .map(securityContext -> (CustomUserDetails) securityContext.getAuthentication().getPrincipal())
                .switchIfEmpty(Mono.error(new UserException(ErrorCode.AUTHENTICATION_FAILED)))
                .flatMap(customUserDetails ->
                        reactiveWorkspaceRepository.findById(workspaceId)
                                .switchIfEmpty(Mono.error(new WorkspaceException(ErrorCode.WORKSPACE_NOT_FOUND)))
                                .flatMap(workspace -> {
                                    // 페이지 존재 확인
                                    return reactivePageRepository.findByIdAndWorkspaceId(pageId, workspaceId)
                                            .switchIfEmpty(Mono.error(new PageException(ErrorCode.PAGE_NOT_FOUND)))
                                            .flatMap(page -> {
                                                // 클라이언트가 워크스페이스의 소유자인지 확인 (가장 높은 권한)
                                                if (workspace.getCreatedBy().equals(customUserDetails.getId())) {
                                                    return Mono.just(page); // 소유자는 무조건 수정 가능
                                                }

                                                // 워크스페이스 멤버 여부 확인
                                                return reactiveWorkspaceMemberRepository.existsByWorkspaceIdAndUserId(workspaceId, customUserDetails.getId())
                                                        .flatMap(isMember -> {
                                                            if (isMember) {
                                                                // 멤버인 경우, 페이지 수정 권한(EDIT 이상) 확인
                                                                return reactivePagePermissionRepository.findByPageIdAndUserId(pageId, customUserDetails.getId())
                                                                        .flatMap(permission -> {
                                                                            PagePermissionType permissionType = PagePermissionType.valueOf(permission.getPermission());
                                                                            if (permissionType.getLevel() >= PagePermissionType.EDIT.getLevel()) {
                                                                                return Mono.just(page);
                                                                            }
                                                                            return Mono.error(new PagePermissionException(ErrorCode.PAGE_PERMISSION_DENIED));
                                                                        })
                                                                        .switchIfEmpty(Mono.error(new PagePermissionException(ErrorCode.PAGE_PERMISSION_DENIED)));
                                                            } else {
                                                                // 워크스페이스 멤버가 아닌 경우, 수정 권한 없음
                                                                return Mono.error(new PagePermissionException(ErrorCode.PAGE_PERMISSION_DENIED));
                                                            }
                                                        });
                                            })
                                            .flatMap(page -> {
                                                // 페이지 수정 및 저장
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
                                                page.setLastEditedBy(customUserDetails.getId());

                                                return reactivePageRepository.save(page);
                                            });
                                })
                )
                .map(PageResponseDto::from)
                .onErrorMap(throwable -> {
                    if (throwable instanceof UserException ||
                            throwable instanceof PageException ||
                            throwable instanceof PagePermissionException ||
                            throwable instanceof WorkspaceException ||
                            throwable instanceof UuidException) {
                        return throwable;
                    }
                    return new PageException(ErrorCode.UNEXPECTED_ERROR);
                });
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
        return ReactiveSecurityContextHolder.getContext()
                .map(securityContext -> (CustomUserDetails) securityContext.getAuthentication().getPrincipal())
                .switchIfEmpty(Mono.error(new UserException(ErrorCode.AUTHENTICATION_FAILED)))
                .flatMap(clientUser ->
                        reactiveWorkspaceRepository.findById(workspaceId)
                                .switchIfEmpty(Mono.error(new WorkspaceException(ErrorCode.WORKSPACE_NOT_FOUND)))
                                .flatMap(workspace ->
                                        reactivePageRepository.findByIdAndWorkspaceId(pageId, workspaceId)
                                                .switchIfEmpty(Mono.error(new PageException(ErrorCode.PAGE_NOT_FOUND)))
                                                .flatMap(page -> {
                                                    // 3, 5: 클라이언트가 워크스페이스 소유자이거나, 페이지에 FULL_ACCESS 권한이 있는지 확인
                                                    if (workspace.getCreatedBy().equals(clientUser.getId())) {
                                                        return Mono.just(page); // 소유자는 무조건 초대 가능
                                                    }
                                                    return reactivePagePermissionRepository.findByPageIdAndUserId(pageId, clientUser.getId())
                                                            .flatMap(permission -> {
                                                                PagePermissionType permissionType = PagePermissionType.valueOf(permission.getPermission());
                                                                if (permissionType != PagePermissionType.FULL_ACCESS) {
                                                                    return Mono.error(new PagePermissionException(ErrorCode.PAGE_PERMISSION_DENIED));
                                                                }
                                                                return Mono.just(page);
                                                            })
                                                            .switchIfEmpty(Mono.error(new PagePermissionException(ErrorCode.PAGE_PERMISSION_DENIED)));
                                                })
                                                .flatMap(page -> {
                                                            UUID userId = uuidUtils.fromString(request.getUserId());
                                                            // 6: 초대할 사용자가 워크스페이스 멤버인지 확인
                                                            return reactiveWorkspaceMemberRepository.existsByWorkspaceIdAndUserId(workspaceId, userId)
                                                                    .flatMap(isInvitedUserWorkspaceMember -> {
                                                                        if (!isInvitedUserWorkspaceMember) {
                                                                            return Mono.error(new WorkspaceMemberException(ErrorCode.INVITED_USER_NOT_WORKSPACE_MEMBER));
                                                                        }
                                                                        // 7: 초대할 사용자가 이미 페이지에 권한이 있는지 확인
                                                                        return reactivePagePermissionRepository.findByPageIdAndUserId(pageId, userId)
                                                                                .flatMap(existingPermission -> {
                                                                                    // 이미 권한이 있으면 업데이트 또는 에러 처리 (여기서는 업데이트로 처리)
                                                                                    existingPermission.setPermission(request.getPermissionType());
                                                                                    return reactivePagePermissionRepository.save(existingPermission)
                                                                                            .map(PageInviteResponseDto::from);
                                                                                })
                                                                                .switchIfEmpty(
                                                                                        // 8: 권한이 없으면 새로 생성
                                                                                        Mono.defer(() ->
                                                                                                reactivePagePermissionRepository.save(PagePermission.builder()
                                                                                                                .pageId(page.getId())
                                                                                                                .userId(userId)
                                                                                                                .permission(request.getPermissionType())
                                                                                                                .build())
                                                                                                        .map(PageInviteResponseDto::from)
                                                                                        )
                                                                                );
                                                                    });
                                                        }
                                                )
                                )
                )
                .onErrorMap(throwable -> {
                    if (throwable instanceof UserException ||
                            throwable instanceof PageException ||
                            throwable instanceof PagePermissionException ||
                            throwable instanceof WorkspaceException ||
                            throwable instanceof WorkspaceMemberException ||
                            throwable instanceof UuidException) {
                        return throwable;
                    }
                    log.error("{}",throwable);
                    return new PageException(ErrorCode.UNEXPECTED_ERROR);
                });
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
            String workspaceIdStr, String pageIdStr, String targetUserIdStr, PageUpdatePermissionRequestDto request
    ) {
        UUID workspaceId = uuidUtils.fromString(workspaceIdStr);
        UUID pageId = uuidUtils.fromString(pageIdStr);
        UUID targetUserId = uuidUtils.fromString(targetUserIdStr);

        return ReactiveSecurityContextHolder.getContext()
                .map(securityContext -> (CustomUserDetails) securityContext.getAuthentication().getPrincipal())
                .switchIfEmpty(Mono.error(new UserException(ErrorCode.AUTHENTICATION_FAILED)))
                .flatMap(clientUser ->
                        reactiveWorkspaceRepository.findById(workspaceId)
                                .switchIfEmpty(Mono.error(new WorkspaceException(ErrorCode.WORKSPACE_NOT_FOUND)))
                                .flatMap(workspace ->
                                        reactivePageRepository.findByIdAndWorkspaceId(pageId, workspaceId)
                                                .switchIfEmpty(Mono.error(new PageException(ErrorCode.PAGE_NOT_FOUND)))
                                                .flatMap(page -> {
                                                    // 5: 클라이언트가 워크스페이스 소유자이거나, 페이지에 FULL_ACCESS 권한이 있는지 확인
                                                    if (!workspace.getCreatedBy().equals(clientUser.getId())) {
                                                        return reactivePagePermissionRepository.findByPageIdAndUserId(pageId, clientUser.getId())
                                                                .flatMap(permission -> {
                                                                    PagePermissionType permissionType = PagePermissionType.valueOf(permission.getPermission());
                                                                    if (permissionType != PagePermissionType.FULL_ACCESS) {
                                                                        return Mono.error(new PagePermissionException(ErrorCode.PAGE_PERMISSION_DENIED));
                                                                    }
                                                                    return Mono.just(page);
                                                                })
                                                                .switchIfEmpty(Mono.error(new PagePermissionException(ErrorCode.PAGE_PERMISSION_DENIED)));
                                                    }
                                                    // 워크스페이스 소유자는 바로 다음 로직으로 진행
                                                    return Mono.just(page);
                                                })
                                                .flatMap(page ->
                                                        // 6: 수정할 대상 사용자가 워크스페이스 멤버인지 확인
                                                        reactiveWorkspaceMemberRepository.existsByWorkspaceIdAndUserId(workspaceId, targetUserId)
                                                                .flatMap(isTargetUserWorkspaceMember -> {
                                                                    if (!isTargetUserWorkspaceMember) {
                                                                        return Mono.error(new WorkspaceMemberException(ErrorCode.INVITED_USER_NOT_WORKSPACE_MEMBER));
                                                                    }
                                                                    // 7: 수정할 대상 사용자가 페이지 소유자인지 확인 (페이지 소유자 권한은 수정 불가)
                                                                    if (page.getCreatedBy().equals(targetUserId)) {
                                                                        return Mono.error(new PagePermissionException(ErrorCode.CANNOT_CHANGE_OWNER_PERMISSION));
                                                                    }
                                                                    // 8: 페이지 권한 수정
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
                .onErrorMap(throwable -> {
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
            String workspaceIdStr, String pageIdStr, PagePublicStatusUpdateRequestDto request
    ) {
        UUID workspaceId = uuidUtils.fromString(workspaceIdStr);
        UUID pageId = uuidUtils.fromString(pageIdStr);

        return ReactiveSecurityContextHolder.getContext()
                .map(securityContext -> (CustomUserDetails) securityContext.getAuthentication().getPrincipal())
                .switchIfEmpty(Mono.error(new UserException(ErrorCode.AUTHENTICATION_FAILED)))
                .flatMap(customUserDetails ->
                        reactiveWorkspaceRepository.findById(workspaceId)
                                .switchIfEmpty(Mono.error(new WorkspaceException(ErrorCode.WORKSPACE_NOT_FOUND)))
                                .flatMap(workspace ->
                                        reactivePageRepository.findByIdAndWorkspaceId(pageId, workspaceId)
                                                .switchIfEmpty(Mono.error(new PageException(ErrorCode.PAGE_NOT_FOUND)))
                                                .flatMap(page -> {
                                                    // 5: 클라이언트가 워크스페이스 소유자이거나, 페이지에 FULL_ACCESS 권한이 있는지 확인
                                                    if (!workspace.getCreatedBy().equals(customUserDetails.getId())) {
                                                        return reactivePagePermissionRepository.findByPageIdAndUserId(pageId, customUserDetails.getId())
                                                                .flatMap(permission -> {
                                                                    PagePermissionType permissionType = PagePermissionType.valueOf(permission.getPermission());
                                                                    if (permissionType != PagePermissionType.FULL_ACCESS) {
                                                                        return Mono.error(new PagePermissionException(ErrorCode.PAGE_PERMISSION_DENIED));
                                                                    }
                                                                    return Mono.just(page);
                                                                })
                                                                .switchIfEmpty(Mono.error(new PagePermissionException(ErrorCode.PAGE_PERMISSION_DENIED)));
                                                    }
                                                    // 워크스페이스 소유자는 바로 다음 로직으로 진행
                                                    return Mono.just(page);
                                                })
                                                .flatMap(page -> {
                                                    // 6: 페이지 공개 상태 수정 및 저장
                                                    page.setPublic(request.getIsPublic());
                                                    page.setUpdatedAt(LocalDateTime.now());
                                                    page.setLastEditedBy(customUserDetails.getId());

                                                    return reactivePageRepository.save(page)
                                                            .map(PagePublicStatusUpdateResponseDto::from);
                                                })
                                )
                )
                .onErrorMap(throwable -> {
                    if (throwable instanceof UserException ||
                            throwable instanceof PageException ||
                            throwable instanceof PagePermissionException ||
                            throwable instanceof WorkspaceException ||
                            throwable instanceof UuidException) {
                        return throwable;
                    }
                    return new PageException(ErrorCode.UNEXPECTED_ERROR);
                });
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
        return ReactiveSecurityContextHolder.getContext()
                .map(securityContext -> (CustomUserDetails) securityContext.getAuthentication().getPrincipal())
                .switchIfEmpty(Mono.error(new UserException(ErrorCode.AUTHENTICATION_FAILED)))
                .flatMap(customUserDetails ->
                        // 워크스페이스 및 페이지 존재 확인
                        reactiveWorkspaceRepository.findById(workspaceId)
                                .switchIfEmpty(Mono.error(new WorkspaceException(ErrorCode.WORKSPACE_NOT_FOUND)))
                                .flatMap(workspace ->
                                        reactivePageRepository.findByIdAndWorkspaceId(pageId, workspaceId)
                                                .switchIfEmpty(Mono.error(new PageException(ErrorCode.PAGE_NOT_FOUND)))
                                                .flatMap(page -> {
                                                    // 페이지 소유자(createdBy)이거나 FULL_ACCESS 권한이 있는지 확인
                                                    if (page.getCreatedBy().equals(customUserDetails.getId())) {
                                                        return Mono.just(page);
                                                    }
                                                    return reactivePagePermissionRepository.findByPageIdAndUserId(pageId, customUserDetails.getId())
                                                            .flatMap(permission -> {
                                                                PagePermissionType permissionType = PagePermissionType.valueOf(permission.getPermission());
                                                                if (permissionType != PagePermissionType.FULL_ACCESS) {
                                                                    return Mono.error(new PagePermissionException(ErrorCode.PAGE_PERMISSION_DENIED));
                                                                }
                                                                return Mono.just(page);
                                                            })
                                                            .switchIfEmpty(Mono.error(new PagePermissionException(ErrorCode.PAGE_PERMISSION_DENIED)));
                                                })
                                )
                )
                .flatMap(page ->
                        // CTE를 사용하여 페이지와 하위 페이지의 상태를 일괄적으로 변경
                        reactivePageRepository.updateArchiveStatusForTree(pageId, true, page.getLastEditedBy())
                                .then(reactiveBlockRepository.updateArchiveStatusForPageTree(pageId, true))
                                .thenReturn(PageStatusResponseDto.builder().pageId(pageIdStr).isArchived(true).build())
                )
                .onErrorMap(throwable -> {
                    if (throwable instanceof UserException ||
                            throwable instanceof PageException ||
                            throwable instanceof PagePermissionException ||
                            throwable instanceof WorkspaceException ||
                            throwable instanceof UuidException) {
                        return throwable;
                    }
                    return new PageException(ErrorCode.UNEXPECTED_ERROR);
                });
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
        return ReactiveSecurityContextHolder.getContext()
                .map(securityContext -> (CustomUserDetails) securityContext.getAuthentication().getPrincipal())
                .switchIfEmpty(Mono.error(new UserException(ErrorCode.AUTHENTICATION_FAILED)))
                .flatMap(customUserDetails ->
                        // 워크스페이스 및 페이지 존재 확인
                        reactiveWorkspaceRepository.findById(workspaceId)
                                .switchIfEmpty(Mono.error(new WorkspaceException(ErrorCode.WORKSPACE_NOT_FOUND)))
                                .flatMap(workspace ->
                                        reactivePageRepository.findByIdAndWorkspaceId(pageId, workspaceId)
                                                .switchIfEmpty(Mono.error(new PageException(ErrorCode.PAGE_NOT_FOUND)))
                                                .flatMap(page -> {
                                                    // 페이지 소유자(createdBy)이거나 FULL_ACCESS 권한이 있는지 확인
                                                    if (page.getCreatedBy().equals(customUserDetails.getId())) {
                                                        return Mono.just(page);
                                                    }
                                                    return reactivePagePermissionRepository.findByPageIdAndUserId(pageId, customUserDetails.getId())
                                                            .flatMap(permission -> {
                                                                PagePermissionType permissionType = PagePermissionType.valueOf(permission.getPermission());
                                                                if (permissionType != PagePermissionType.FULL_ACCESS) {
                                                                    return Mono.error(new PagePermissionException(ErrorCode.PAGE_PERMISSION_DENIED));
                                                                }
                                                                return Mono.just(page);
                                                            })
                                                            .switchIfEmpty(Mono.error(new PagePermissionException(ErrorCode.PAGE_PERMISSION_DENIED)));
                                                })
                                )
                )
                .flatMap(page ->
                        // CTE를 사용하여 페이지와 하위 페이지의 상태를 일괄적으로 변경
                        reactivePageRepository.updateArchiveStatusForTree(pageId, false, page.getLastEditedBy())
                                .then(reactiveBlockRepository.updateArchiveStatusForPageTree(pageId, false))
                                .thenReturn(PageStatusResponseDto.builder().pageId(pageIdStr).isArchived(false).build())
                )
                .onErrorMap(throwable -> {
                    if (throwable instanceof UserException ||
                            throwable instanceof PageException ||
                            throwable instanceof PagePermissionException ||
                            throwable instanceof WorkspaceException ||
                            throwable instanceof UuidException) {
                        return throwable;
                    }
                    return new PageException(ErrorCode.UNEXPECTED_ERROR);
                });
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
        return ReactiveSecurityContextHolder.getContext()
                .map(securityContext -> (CustomUserDetails) securityContext.getAuthentication().getPrincipal())
                .switchIfEmpty(Mono.error(new UserException(ErrorCode.AUTHENTICATION_FAILED)))
                .flatMap(customUserDetails ->
                        // 워크스페이스 및 페이지 존재 확인
                        reactiveWorkspaceRepository.findById(workspaceId)
                                .switchIfEmpty(Mono.error(new WorkspaceException(ErrorCode.WORKSPACE_NOT_FOUND)))
                                .flatMap(workspace ->
                                        reactivePageRepository.findByIdAndWorkspaceId(pageId, workspaceId)
                                                .switchIfEmpty(Mono.error(new PageException(ErrorCode.PAGE_NOT_FOUND)))
                                                .flatMap(page -> {
                                                    // 페이지 소유자(createdBy)이거나 FULL_ACCESS 권한이 있는지 확인
                                                    if (page.getCreatedBy().equals(customUserDetails.getId())) {
                                                        return Mono.just(page);
                                                    }
                                                    return reactivePagePermissionRepository.findByPageIdAndUserId(pageId, customUserDetails.getId())
                                                            .flatMap(permission -> {
                                                                PagePermissionType permissionType = PagePermissionType.valueOf(permission.getPermission());
                                                                if (permissionType != PagePermissionType.FULL_ACCESS) {
                                                                    return Mono.error(new PagePermissionException(ErrorCode.PAGE_PERMISSION_DENIED));
                                                                }
                                                                return Mono.just(page);
                                                            })
                                                            .switchIfEmpty(Mono.error(new PagePermissionException(ErrorCode.PAGE_PERMISSION_DENIED)));
                                                })
                                )
                )
                .flatMap(page ->
                        // CTE를 사용하여 블록과 페이지를 일괄적으로 삭제
                        reactiveBlockRepository.deleteAllByPageTree(pageId)
                                .then(reactivePageRepository.deletePageAndDescendants(pageId))
                                .then()
                )
                .onErrorMap(throwable -> {
                    if (throwable instanceof UserException ||
                            throwable instanceof PageException ||
                            throwable instanceof PagePermissionException ||
                            throwable instanceof WorkspaceException ||
                            throwable instanceof UuidException) {
                        return throwable;
                    }
                    return new PageException(ErrorCode.UNEXPECTED_ERROR);
                });
    }
}
