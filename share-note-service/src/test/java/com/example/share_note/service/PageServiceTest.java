package com.example.share_note.service;


import com.example.share_note.domain.Page;
import com.example.share_note.domain.PagePermission;
import com.example.share_note.domain.Workspace;
import com.example.share_note.dto.CustomUserDetails;
import com.example.share_note.dto.page.*;
import com.example.share_note.enums.PagePermissionType;
import com.example.share_note.exception.*;
import com.example.share_note.repository.*;
import com.example.share_note.service.impl.PageServiceImpl;
import com.example.share_note.util.UuidUtils;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class PageServiceTest {
    @Mock
    private ReactivePageRepository reactivePageRepository;

    @Mock
    private ReactivePagePermissionRepository reactivePagePermissionRepository;

    @Mock
    private ReactiveWorkspaceRepository reactiveWorkspaceRepository;

    @Mock
    private ReactiveWorkspaceMemberRepository reactiveWorkspaceMemberRepository;

    @Mock
    private ReactiveBlockRepository reactiveBlockRepository;

    @Mock
    private UuidUtils uuidUtils;

    @InjectMocks
    private PageServiceImpl pageService;

    private UUID workspaceId;
    private UUID userId;
    private UUID pageId;
    private UUID parentPageId;
    private String workspaceIdStr;
    private String pageIdStr;
    private String parentPageIdStr;

    private CustomUserDetails customUserDetails;
    private Workspace workspace;
    private Page page;
    private Page parentPage;
    private PagePermission pagePermission;

    private SecurityContext securityContext;
    private Authentication authentication;

    private PageInviteRequestDto pageInviteRequestDto;
    private PageUpdatePermissionRequestDto pageUpdatePermissionRequestDto;
    private PagePublicStatusUpdateRequestDto pagePublicStatusUpdateRequestDto;
    private UUID targetUserId;
    private String targetUserIdStr;

    @BeforeEach
    void setUp() {
        workspaceId = UUID.randomUUID();
        userId = UUID.randomUUID();
        pageId = UUID.randomUUID();
        parentPageId = UUID.randomUUID();
        workspaceIdStr = workspaceId.toString();
        pageIdStr = pageId.toString();
        parentPageIdStr = parentPageId.toString();

        customUserDetails = new CustomUserDetails(
                userId, "testuser", "password", "ROLE_USER", "test@example.com"
        );

        securityContext = mock(SecurityContext.class);
        authentication = mock(Authentication.class);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(customUserDetails);

        workspace = Workspace.builder()
                .id(workspaceId)
                .name("Test Workspace")
                .createdBy(userId)
                .build();

        page = Page.builder()
                .id(pageId)
                .workspaceId(workspaceId)
                .parentPageId(parentPageId)
                .title("Test Page")
                .isPublic(false)
                .isArchived(false)
                .isTemplate(false)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .createdBy(userId)
                .lastEditedBy(userId)
                .build();

        parentPage = Page.builder()
                .id(parentPageId)
                .workspaceId(workspaceId)
                .parentPageId(null)
                .title("Parent Page")
                .isPublic(false)
                .isArchived(false)
                .isTemplate(false)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .createdBy(userId)
                .lastEditedBy(userId)
                .build();

        pagePermission = PagePermission.builder()
                .pageId(pageId)
                .userId(userId)
                .permission(PagePermissionType.EDIT.name())
                .build();

        pageInviteRequestDto = PageInviteRequestDto.builder()
                .userId(UUID.randomUUID().toString())
                .permissionType(PagePermissionType.READ.name())
                .build();

        pageUpdatePermissionRequestDto = PageUpdatePermissionRequestDto.builder()
                .permissionType(PagePermissionType.EDIT.name())
                .build();

        pagePublicStatusUpdateRequestDto = PagePublicStatusUpdateRequestDto.builder()
                .isPublic(true)
                .build();
    }

    @Test
    @Order(1)
    @DisplayName("페이지 생성 성공 - 워크스페이스 소유자가 루트 페이지 생성")
    void createPage_Success_WorkspaceOwner_RootPage() {
        // given
        PageCreateRequestDto request = PageCreateRequestDto.builder()
                .title("New Page")
                .icon("📄")
                .cover("cover.jpg")
                .properties("{}")
                .build();

        when(uuidUtils.fromString(workspaceIdStr)).thenReturn(workspaceId);
        when(reactiveWorkspaceRepository.findById(workspaceId)).thenReturn(Mono.just(workspace));
        when(reactivePageRepository.save(any(Page.class))).thenReturn(Mono.just(page));

        try (MockedStatic<ReactiveSecurityContextHolder> mockedSecurityContext =
                     mockStatic(ReactiveSecurityContextHolder.class)) {

            mockedSecurityContext.when(ReactiveSecurityContextHolder::getContext)
                    .thenReturn(Mono.just(securityContext));

            // when & then
            StepVerifier.create(pageService.createPage(workspaceIdStr, request))
                    .expectNextMatches(response -> {
                        assertThat(response.getPageId()).isNotNull();
                        assertThat(response.getWorkspaceId()).isEqualTo(workspaceIdStr);
                        return true;
                    })
                    .verifyComplete();
        }

        verify(reactivePageRepository).save(any(Page.class));
    }

    @Test
    @Order(2)
    @DisplayName("페이지 생성 성공 - 워크스페이스 멤버가 부모 페이지에 하위 페이지 생성")
    void createPage_Success_WorkspaceMember_ChildPage() {
        // given
        PageCreateRequestDto request = PageCreateRequestDto.builder()
                .parentPageId(parentPageIdStr)
                .title("Child Page")
                .build();

        Workspace memberWorkspace = Workspace.builder()
                .id(workspaceId)
                .name("Test Workspace")
                .createdBy(UUID.randomUUID()) // 다른 사용자가 소유자
                .build();

        PagePermission parentPagePermission = PagePermission.builder()
                .pageId(parentPageId)
                .userId(userId)
                .permission(PagePermissionType.EDIT.name())
                .build();

        when(uuidUtils.fromString(workspaceIdStr)).thenReturn(workspaceId);
        when(uuidUtils.fromString(parentPageIdStr)).thenReturn(parentPageId);
        when(reactiveWorkspaceRepository.findById(workspaceId)).thenReturn(Mono.just(memberWorkspace));
        when(reactiveWorkspaceMemberRepository.existsByWorkspaceIdAndUserId(workspaceId, userId))
                .thenReturn(Mono.just(true));
        when(reactivePageRepository.findByIdAndWorkspaceId(parentPageId, workspaceId))
                .thenReturn(Mono.just(parentPage));
        when(reactivePagePermissionRepository.findByPageIdAndUserId(parentPageId, userId))
                .thenReturn(Mono.just(parentPagePermission));
        when(reactivePageRepository.save(any(Page.class))).thenReturn(Mono.just(page));

        try (MockedStatic<ReactiveSecurityContextHolder> mockedSecurityContext =
                     mockStatic(ReactiveSecurityContextHolder.class)) {

            mockedSecurityContext.when(ReactiveSecurityContextHolder::getContext)
                    .thenReturn(Mono.just(securityContext));

            // when & then
            StepVerifier.create(pageService.createPage(workspaceIdStr, request))
                    .expectNextMatches(response -> {
                        assertThat(response.getPageId()).isNotNull();
                        return true;
                    })
                    .verifyComplete();
        }
    }

    @Test
    @Order(3)
    @DisplayName("페이지 생성 실패 - 워크스페이스 없음")
    void createPage_Fail_WorkspaceNotFound() {
        // given
        PageCreateRequestDto request = PageCreateRequestDto.builder()
                .title("New Page")
                .build();

        when(uuidUtils.fromString(workspaceIdStr)).thenReturn(workspaceId);
        when(reactiveWorkspaceRepository.findById(workspaceId)).thenReturn(Mono.empty());

        try (MockedStatic<ReactiveSecurityContextHolder> mockedSecurityContext =
                     mockStatic(ReactiveSecurityContextHolder.class)) {

            mockedSecurityContext.when(ReactiveSecurityContextHolder::getContext)
                    .thenReturn(Mono.just(securityContext));

            // when & then
            StepVerifier.create(pageService.createPage(workspaceIdStr, request))
                    .expectError(WorkspaceException.class)
                    .verify();
        }
    }

    @Test
    @Order(4)
    @DisplayName("페이지 생성 실패 - 워크스페이스 멤버 아님")
    void createPage_Fail_NotWorkspaceMember() {
        // given
        PageCreateRequestDto request = PageCreateRequestDto.builder()
                .title("New Page")
                .build();

        Workspace otherWorkspace = Workspace.builder()
                .id(workspaceId)
                .name("Test Workspace")
                .createdBy(UUID.randomUUID()) // 다른 사용자가 소유자
                .build();

        when(uuidUtils.fromString(workspaceIdStr)).thenReturn(workspaceId);
        when(reactiveWorkspaceRepository.findById(workspaceId)).thenReturn(Mono.just(otherWorkspace));
        when(reactiveWorkspaceMemberRepository.existsByWorkspaceIdAndUserId(workspaceId, userId))
                .thenReturn(Mono.just(false));

        try (MockedStatic<ReactiveSecurityContextHolder> mockedSecurityContext =
                     mockStatic(ReactiveSecurityContextHolder.class)) {

            mockedSecurityContext.when(ReactiveSecurityContextHolder::getContext)
                    .thenReturn(Mono.just(securityContext));

            // when & then
            StepVerifier.create(pageService.createPage(workspaceIdStr, request))
                    .expectError(WorkspaceMemberException.class)
                    .verify();
        }
    }

    @Test
    @Order(5)
    @DisplayName("페이지 생성 실패 - 부모 페이지 없음")
    void createPage_Fail_ParentPageNotFound() {
        // given
        PageCreateRequestDto request = PageCreateRequestDto.builder()
                .parentPageId(parentPageIdStr)
                .title("Child Page")
                .build();
        
        when(uuidUtils.fromString(workspaceIdStr)).thenReturn(workspaceId);
        when(uuidUtils.fromString(parentPageIdStr)).thenReturn(parentPageId);
        when(reactiveWorkspaceRepository.findById(workspaceId)).thenReturn(Mono.just(workspace));
        when(reactivePageRepository.findByIdAndWorkspaceId(parentPageId, workspaceId))
                .thenReturn(Mono.empty());

        try (MockedStatic<ReactiveSecurityContextHolder> mockedSecurityContext =
                     mockStatic(ReactiveSecurityContextHolder.class)) {

            mockedSecurityContext.when(ReactiveSecurityContextHolder::getContext)
                    .thenReturn(Mono.just(securityContext));

            // when & then
            StepVerifier.create(pageService.createPage(workspaceIdStr, request))
                    .expectError(PageException.class)
                    .verify();
        }
    }

    @Test
    @Order(6)
    @DisplayName("페이지 생성 실패 - 부모 페이지 권한 없음")
    void createPage_Fail_ParentPagePermissionDenied() {
        // given
        PageCreateRequestDto request = PageCreateRequestDto.builder()
                .parentPageId(parentPageIdStr)
                .title("Child Page")
                .build();

        Workspace memberWorkspace = Workspace.builder()
                .id(workspaceId)
                .name("Test Workspace")
                .createdBy(UUID.randomUUID())
                .build();

        PagePermission readOnlyPermission = PagePermission.builder()
                .pageId(parentPageId)
                .userId(userId)
                .permission(PagePermissionType.READ.name())
                .build();

        when(uuidUtils.fromString(workspaceIdStr)).thenReturn(workspaceId);
        when(uuidUtils.fromString(parentPageIdStr)).thenReturn(parentPageId);
        when(reactiveWorkspaceRepository.findById(workspaceId)).thenReturn(Mono.just(memberWorkspace));
        when(reactiveWorkspaceMemberRepository.existsByWorkspaceIdAndUserId(workspaceId, userId))
                .thenReturn(Mono.just(true));
        when(reactivePageRepository.findByIdAndWorkspaceId(parentPageId, workspaceId))
                .thenReturn(Mono.just(parentPage));
        when(reactivePagePermissionRepository.findByPageIdAndUserId(parentPageId, userId))
                .thenReturn(Mono.just(readOnlyPermission));

        try (MockedStatic<ReactiveSecurityContextHolder> mockedSecurityContext =
                     mockStatic(ReactiveSecurityContextHolder.class)) {

            mockedSecurityContext.when(ReactiveSecurityContextHolder::getContext)
                    .thenReturn(Mono.just(securityContext));

            // when & then
            StepVerifier.create(pageService.createPage(workspaceIdStr, request))
                    .expectError(PagePermissionException.class)
                    .verify();
        }
    }

    @Test
    @Order(7)
    @DisplayName("페이지 목록 조회 성공 - 워크스페이스 소유자")
    void getPages_Success_WorkspaceOwner() {
        // given
        Page page1 = Page.builder().id(UUID.randomUUID()).title("Page 1").icon("📄").build();
        Page page2 = Page.builder().id(UUID.randomUUID()).title("Page 2").icon("📝").build();

        when(uuidUtils.fromString(workspaceIdStr)).thenReturn(workspaceId);
        when(reactiveWorkspaceRepository.findById(workspaceId)).thenReturn(Mono.just(workspace));
        when(reactivePageRepository.findAllByWorkspaceIdAndParentPageIdIsNull(workspaceId))
                .thenReturn(Flux.just(page1, page2));

        try (MockedStatic<ReactiveSecurityContextHolder> mockedSecurityContext =
                     mockStatic(ReactiveSecurityContextHolder.class)) {

            mockedSecurityContext.when(ReactiveSecurityContextHolder::getContext)
                    .thenReturn(Mono.just(securityContext));

            // when & then
            StepVerifier.create(pageService.getPages(workspaceIdStr))
                    .expectNextMatches(response -> {
                        assertThat(response.getPages()).hasSize(2);
                        assertThat(response.getPages().get(0).getTitle()).isEqualTo("Page 1");
                        assertThat(response.getPages().get(1).getTitle()).isEqualTo("Page 2");
                        return true;
                    })
                    .verifyComplete();
        }
    }

    @Test
    @Order(8)
    @DisplayName("페이지 목록 조회 성공 - 워크스페이스 멤버")
    void getPages_Success_WorkspaceMember() {
        // given
        Workspace memberWorkspace = Workspace.builder()
                .id(workspaceId)
                .name("Test Workspace")
                .createdBy(UUID.randomUUID())
                .build();

        when(uuidUtils.fromString(workspaceIdStr)).thenReturn(workspaceId);
        when(reactiveWorkspaceRepository.findById(workspaceId)).thenReturn(Mono.just(memberWorkspace));
        when(reactiveWorkspaceMemberRepository.existsByWorkspaceIdAndUserId(workspaceId, userId))
                .thenReturn(Mono.just(true));
        when(reactivePageRepository.findAllByWorkspaceIdAndParentPageIdIsNull(workspaceId))
                .thenReturn(Flux.empty());

        try (MockedStatic<ReactiveSecurityContextHolder> mockedSecurityContext =
                     mockStatic(ReactiveSecurityContextHolder.class)) {

            mockedSecurityContext.when(ReactiveSecurityContextHolder::getContext)
                    .thenReturn(Mono.just(securityContext));

            // when & then
            StepVerifier.create(pageService.getPages(workspaceIdStr))
                    .expectNextMatches(response -> {
                        assertThat(response.getPages()).isEmpty();
                        return true;
                    })
                    .verifyComplete();
        }
    }

    @Test
    @Order(9)
    @DisplayName("페이지 조회 성공 - 워크스페이스 멤버, 읽기 권한 있음")
    void getPage_Success_WorkspaceMember_WithReadPermission() {
        // given
        Workspace memberWorkspace = Workspace.builder()
                .id(workspaceId)
                .name("Test Workspace")
                .createdBy(UUID.randomUUID())
                .build();

        PagePermission readPermission = PagePermission.builder()
                .pageId(pageId)
                .userId(userId)
                .permission(PagePermissionType.READ.name())
                .build();

        when(uuidUtils.fromString(workspaceIdStr)).thenReturn(workspaceId);
        when(uuidUtils.fromString(pageIdStr)).thenReturn(pageId);
        when(reactiveWorkspaceRepository.findById(workspaceId)).thenReturn(Mono.just(memberWorkspace));
        when(reactiveWorkspaceMemberRepository.existsByWorkspaceIdAndUserId(workspaceId, userId))
                .thenReturn(Mono.just(true));
        when(reactivePageRepository.findByIdAndWorkspaceId(pageId, workspaceId))
                .thenReturn(Mono.just(page));
        when(reactivePagePermissionRepository.findByPageIdAndUserId(pageId, userId))
                .thenReturn(Mono.just(readPermission));

        try (MockedStatic<ReactiveSecurityContextHolder> mockedSecurityContext =
                     mockStatic(ReactiveSecurityContextHolder.class)) {

            mockedSecurityContext.when(ReactiveSecurityContextHolder::getContext)
                    .thenReturn(Mono.just(securityContext));

            // when & then
            StepVerifier.create(pageService.getPage(workspaceIdStr, pageIdStr))
                    .expectNextMatches(response -> {
                        assertThat(response.getId()).isEqualTo(pageIdStr);
                        assertThat(response.getTitle()).isEqualTo("Test Page");
                        assertThat(response.getParentPageId()).isEqualTo(parentPageIdStr);
                        return true;
                    })
                    .verifyComplete();
        }
    }

    @Test
    @Order(10)
    @DisplayName("페이지 조회 성공 - 비멤버, 공개 페이지")
    void getPage_Success_NonMember_PublicPage() {
        // given
        UUID otherUser = UUID.randomUUID();
        Page publicPage = Page.builder()
                .id(pageId)
                .workspaceId(workspaceId)
                .title("Public Page")
                .parentPageId(parentPageId)
                .isPublic(true)
                .isArchived(false)
                .isTemplate(false)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .createdBy(otherUser)
                .lastEditedBy(otherUser)
                .build();

        Workspace otherWorkspace = Workspace.builder()
                .id(workspaceId)
                .name("Test Workspace")
                .createdBy(otherUser)
                .build();

        when(uuidUtils.fromString(workspaceIdStr)).thenReturn(workspaceId);
        when(uuidUtils.fromString(pageIdStr)).thenReturn(pageId);
        when(reactiveWorkspaceRepository.findById(workspaceId)).thenReturn(Mono.just(otherWorkspace));
        when(reactiveWorkspaceMemberRepository.existsByWorkspaceIdAndUserId(workspaceId, userId))
                .thenReturn(Mono.just(false));
        when(reactivePageRepository.findByIdAndWorkspaceId(pageId, workspaceId))
                .thenReturn(Mono.just(publicPage));

        try (MockedStatic<ReactiveSecurityContextHolder> mockedSecurityContext =
                     mockStatic(ReactiveSecurityContextHolder.class)) {

            mockedSecurityContext.when(ReactiveSecurityContextHolder::getContext)
                    .thenReturn(Mono.just(securityContext));

            // when & then
            StepVerifier.create(pageService.getPage(workspaceIdStr, pageIdStr))
                    .expectNextMatches(response -> {
                        assertThat(response.getTitle()).isEqualTo("Public Page");
                        assertThat(response.getIsPublic()).isTrue();
                        return true;
                    })
                    .verifyComplete();
        }
    }

    @Test
    @Order(11)
    @DisplayName("페이지 조회 실패 - 권한 없음")
    void getPage_Fail_PermissionDenied() {
        // given
        Workspace otherWorkspace = Workspace.builder()
                .id(workspaceId)
                .name("Test Workspace")
                .createdBy(UUID.randomUUID())
                .build();

        when(uuidUtils.fromString(workspaceIdStr)).thenReturn(workspaceId);
        when(uuidUtils.fromString(pageIdStr)).thenReturn(pageId);
        when(reactiveWorkspaceRepository.findById(workspaceId)).thenReturn(Mono.just(otherWorkspace));
        when(reactiveWorkspaceMemberRepository.existsByWorkspaceIdAndUserId(workspaceId, userId))
                .thenReturn(Mono.just(false));
        when(reactivePageRepository.findByIdAndWorkspaceId(pageId, workspaceId))
                .thenReturn(Mono.just(page)); // isPublic = false

        try (MockedStatic<ReactiveSecurityContextHolder> mockedSecurityContext =
                     mockStatic(ReactiveSecurityContextHolder.class)) {

            mockedSecurityContext.when(ReactiveSecurityContextHolder::getContext)
                    .thenReturn(Mono.just(securityContext));

            // when & then
            StepVerifier.create(pageService.getPage(workspaceIdStr, pageIdStr))
                    .expectError(PagePermissionException.class)
                    .verify();
        }
    }

    @Test
    @Order(12)
    @DisplayName("페이지 조회 실패 - 멤버지만 페이지 권한이 없는 경우")
    void getPage_Fail_MemberButNoPagePermission() {
        // given
        Workspace memberWorkspace = Workspace.builder()
                .id(workspaceId)
                .name("Test Workspace")
                .createdBy(UUID.randomUUID())
                .build();

        when(uuidUtils.fromString(workspaceIdStr)).thenReturn(workspaceId);
        when(uuidUtils.fromString(pageIdStr)).thenReturn(pageId);
        when(reactiveWorkspaceRepository.findById(workspaceId)).thenReturn(Mono.just(memberWorkspace));
        when(reactiveWorkspaceMemberRepository.existsByWorkspaceIdAndUserId(workspaceId, userId))
                .thenReturn(Mono.just(true));
        when(reactivePageRepository.findByIdAndWorkspaceId(pageId, workspaceId))
                .thenReturn(Mono.just(page));
        when(reactivePagePermissionRepository.findByPageIdAndUserId(pageId, userId))
                .thenReturn(Mono.empty()); // 권한 없음

        try (MockedStatic<ReactiveSecurityContextHolder> mockedSecurityContext =
                     mockStatic(ReactiveSecurityContextHolder.class)) {

            mockedSecurityContext.when(ReactiveSecurityContextHolder::getContext)
                    .thenReturn(Mono.just(securityContext));

            // when & then
            StepVerifier.create(pageService.getPage(workspaceIdStr, pageIdStr))
                    .expectError(PagePermissionException.class)
                    .verify();
        }
    }

    @Test
    @Order(13)
    @DisplayName("페이지 수정 성공 - 워크스페이스 소유자")
    void updatePage_Success_WorkspaceOwner() {
        // given
        PageUpdateRequestDto request = PageUpdateRequestDto.builder()
                .title("Updated Title")
                .icon("📝")
                .cover("new-cover.jpg")
                .build();

        when(uuidUtils.fromString(workspaceIdStr)).thenReturn(workspaceId);
        when(uuidUtils.fromString(pageIdStr)).thenReturn(pageId);
        when(reactiveWorkspaceRepository.findById(workspaceId)).thenReturn(Mono.just(workspace));
        when(reactivePageRepository.findByIdAndWorkspaceId(pageId, workspaceId))
                .thenReturn(Mono.just(page));
        when(reactivePageRepository.save(any(Page.class))).thenReturn(Mono.just(page));

        try (MockedStatic<ReactiveSecurityContextHolder> mockedSecurityContext =
                     mockStatic(ReactiveSecurityContextHolder.class)) {

            mockedSecurityContext.when(ReactiveSecurityContextHolder::getContext)
                    .thenReturn(Mono.just(securityContext));

            // when & then
            StepVerifier.create(pageService.updatePage(workspaceIdStr, pageIdStr, request))
                    .expectNextMatches(response -> {
                        assertThat(response.getId()).isEqualTo(pageIdStr);
                        return true;
                    })
                    .verifyComplete();
        }

        verify(reactivePageRepository).save(any(Page.class));
    }

    @Test
    @Order(14)
    @DisplayName("페이지 수정 성공 - 워크스페이스 멤버, 편집 권한 있음")
    void updatePage_Success_WorkspaceMember_WithEditPermission() {
        // given
        PageUpdateRequestDto request = PageUpdateRequestDto.builder()
                .title("Updated Title")
                .build();

        Workspace memberWorkspace = Workspace.builder()
                .id(workspaceId)
                .name("Test Workspace")
                .createdBy(UUID.randomUUID())
                .build();

        when(uuidUtils.fromString(workspaceIdStr)).thenReturn(workspaceId);
        when(uuidUtils.fromString(pageIdStr)).thenReturn(pageId);
        when(reactiveWorkspaceRepository.findById(workspaceId)).thenReturn(Mono.just(memberWorkspace));
        when(reactivePageRepository.findByIdAndWorkspaceId(pageId, workspaceId))
                .thenReturn(Mono.just(page));
        when(reactiveWorkspaceMemberRepository.existsByWorkspaceIdAndUserId(workspaceId, userId))
                .thenReturn(Mono.just(true));
        when(reactivePagePermissionRepository.findByPageIdAndUserId(pageId, userId))
                .thenReturn(Mono.just(pagePermission)); // EDIT permission
        when(reactivePageRepository.save(any(Page.class))).thenReturn(Mono.just(page));

        try (MockedStatic<ReactiveSecurityContextHolder> mockedSecurityContext =
                     mockStatic(ReactiveSecurityContextHolder.class)) {

            mockedSecurityContext.when(ReactiveSecurityContextHolder::getContext)
                    .thenReturn(Mono.just(securityContext));

            // when & then
            StepVerifier.create(pageService.updatePage(workspaceIdStr, pageIdStr, request))
                    .expectNextMatches(response -> {
                        assertThat(response.getId()).isEqualTo(pageIdStr);
                        return true;
                    })
                    .verifyComplete();
        }
    }

    @Test
    @Order(15)
    @DisplayName("페이지 수정 실패 - 페이지 없음")
    void updatePage_Fail_PageNotFound() {
        // given
        PageUpdateRequestDto request = PageUpdateRequestDto.builder()
                .title("Updated Title")
                .build();

        when(uuidUtils.fromString(workspaceIdStr)).thenReturn(workspaceId);
        when(uuidUtils.fromString(pageIdStr)).thenReturn(pageId);
        when(reactiveWorkspaceRepository.findById(workspaceId)).thenReturn(Mono.just(workspace));
        when(reactivePageRepository.findByIdAndWorkspaceId(pageId, workspaceId))
                .thenReturn(Mono.empty());

        try (MockedStatic<ReactiveSecurityContextHolder> mockedSecurityContext =
                     mockStatic(ReactiveSecurityContextHolder.class)) {

            mockedSecurityContext.when(ReactiveSecurityContextHolder::getContext)
                    .thenReturn(Mono.just(securityContext));

            // when & then
            StepVerifier.create(pageService.updatePage(workspaceIdStr, pageIdStr, request))
                    .expectError(PageException.class)
                    .verify();
        }
    }

    @Test
    @Order(16)
    @DisplayName("페이지 수정 실패 - 편집 권한 없음")
    void updatePage_Fail_EditPermissionDenied() {
        // given
        PageUpdateRequestDto request = PageUpdateRequestDto.builder()
                .title("Updated Title")
                .build();

        Workspace memberWorkspace = Workspace.builder()
                .id(workspaceId)
                .name("Test Workspace")
                .createdBy(UUID.randomUUID())
                .build();

        PagePermission readOnlyPermission = PagePermission.builder()
                .pageId(pageId)
                .userId(userId)
                .permission(PagePermissionType.READ.name())
                .build();

        when(uuidUtils.fromString(workspaceIdStr)).thenReturn(workspaceId);
        when(uuidUtils.fromString(pageIdStr)).thenReturn(pageId);
        when(reactiveWorkspaceRepository.findById(workspaceId)).thenReturn(Mono.just(memberWorkspace));
        when(reactivePageRepository.findByIdAndWorkspaceId(pageId, workspaceId))
                .thenReturn(Mono.just(page));
        when(reactiveWorkspaceMemberRepository.existsByWorkspaceIdAndUserId(workspaceId, userId))
                .thenReturn(Mono.just(true));
        when(reactivePagePermissionRepository.findByPageIdAndUserId(pageId, userId))
                .thenReturn(Mono.just(readOnlyPermission));

        try (MockedStatic<ReactiveSecurityContextHolder> mockedSecurityContext =
                     mockStatic(ReactiveSecurityContextHolder.class)) {

            mockedSecurityContext.when(ReactiveSecurityContextHolder::getContext)
                    .thenReturn(Mono.just(securityContext));

            // when & then
            StepVerifier.create(pageService.updatePage(workspaceIdStr, pageIdStr, request))
                    .expectError(PagePermissionException.class)
                    .verify();
        }
    }

    @Test
    @Order(17)
    @DisplayName("페이지 수정 실패 - 워크스페이스 멤버 아님")
    void updatePage_Fail_NotWorkspaceMember() {
        // given
        PageUpdateRequestDto request = PageUpdateRequestDto.builder()
                .title("Updated Title")
                .build();

        Workspace memberWorkspace = Workspace.builder()
                .id(workspaceId)
                .name("Test Workspace")
                .createdBy(UUID.randomUUID())
                .build();

        when(uuidUtils.fromString(workspaceIdStr)).thenReturn(workspaceId);
        when(uuidUtils.fromString(pageIdStr)).thenReturn(pageId);
        when(reactiveWorkspaceRepository.findById(workspaceId)).thenReturn(Mono.just(memberWorkspace));
        when(reactivePageRepository.findByIdAndWorkspaceId(pageId, workspaceId))
                .thenReturn(Mono.just(page));
        when(reactiveWorkspaceMemberRepository.existsByWorkspaceIdAndUserId(workspaceId, userId))
                .thenReturn(Mono.just(false));

        try (MockedStatic<ReactiveSecurityContextHolder> mockedSecurityContext =
                     mockStatic(ReactiveSecurityContextHolder.class)) {

            mockedSecurityContext.when(ReactiveSecurityContextHolder::getContext)
                    .thenReturn(Mono.just(securityContext));

            // when & then
            StepVerifier.create(pageService.updatePage(workspaceIdStr, pageIdStr, request))
                    .expectError(PagePermissionException.class)
                    .verify();
        }
    }

    @Test
    @Order(18)
    @DisplayName("페이지 수정 시 필드별 업데이트 확인")
    void updatePage_Success_FieldUpdate() {
        // given
        PageUpdateRequestDto request = PageUpdateRequestDto.builder()
                .title("New Title")
                .icon("🆕")
                .cover("new-cover.png")
                .build();

        when(uuidUtils.fromString(workspaceIdStr)).thenReturn(workspaceId);
        when(uuidUtils.fromString(pageIdStr)).thenReturn(pageId);
        when(reactiveWorkspaceRepository.findById(workspaceId)).thenReturn(Mono.just(workspace));
        when(reactivePageRepository.findByIdAndWorkspaceId(pageId, workspaceId))
                .thenReturn(Mono.just(page));
        when(reactivePageRepository.save(any(Page.class))).thenAnswer(invocation -> {
            Page savedPage = invocation.getArgument(0);
            // 필드 업데이트 확인
            assertThat(savedPage.getTitle()).isEqualTo("New Title");
            assertThat(savedPage.getIcon()).isEqualTo("🆕");
            assertThat(savedPage.getCover()).isEqualTo("new-cover.png");
            assertThat(savedPage.getLastEditedBy()).isEqualTo(userId);
            assertThat(savedPage.getUpdatedAt()).isNotNull();
            return Mono.just(savedPage);
        });

        try (MockedStatic<ReactiveSecurityContextHolder> mockedSecurityContext =
                     mockStatic(ReactiveSecurityContextHolder.class)) {

            mockedSecurityContext.when(ReactiveSecurityContextHolder::getContext)
                    .thenReturn(Mono.just(securityContext));

            // when & then
            StepVerifier.create(pageService.updatePage(workspaceIdStr, pageIdStr, request))
                    .expectNextCount(1)
                    .verifyComplete();
        }
    }

    @Test
    @Order(19)
    @DisplayName("페이지 멤버 초대 성공 - 워크스페이스 소유자")
    void inviteMemberToPage_Success_WorkspaceOwner() {
        // given
        targetUserId = UUID.randomUUID();
        targetUserIdStr = targetUserId.toString();

        PageInviteRequestDto request = PageInviteRequestDto.builder()
                .userId(targetUserIdStr)
                .permissionType(PagePermissionType.READ.name())
                .build();

        PagePermission newPermission = PagePermission.builder()
                .pageId(pageId)
                .userId(targetUserId)
                .permission(PagePermissionType.READ.name())
                .build();

        when(uuidUtils.fromString(workspaceIdStr)).thenReturn(workspaceId);
        when(uuidUtils.fromString(pageIdStr)).thenReturn(pageId);
        when(uuidUtils.fromString(targetUserIdStr)).thenReturn(targetUserId);
        when(reactiveWorkspaceRepository.findById(workspaceId)).thenReturn(Mono.just(workspace));
        when(reactivePageRepository.findByIdAndWorkspaceId(pageId, workspaceId))
                .thenReturn(Mono.just(page));
        when(reactiveWorkspaceMemberRepository.existsByWorkspaceIdAndUserId(workspaceId, targetUserId))
                .thenReturn(Mono.just(true));
        when(reactivePagePermissionRepository.findByPageIdAndUserId(pageId, targetUserId))
                .thenReturn(Mono.empty()); // 권한이 없는 상태
        when(reactivePagePermissionRepository.save(any(PagePermission.class)))
                .thenReturn(Mono.just(newPermission));

        try (MockedStatic<ReactiveSecurityContextHolder> mockedSecurityContext =
                     mockStatic(ReactiveSecurityContextHolder.class)) {

            mockedSecurityContext.when(ReactiveSecurityContextHolder::getContext)
                    .thenReturn(Mono.just(securityContext));

            // when & then
            StepVerifier.create(pageService.inviteMemberToPage(workspaceIdStr, pageIdStr, request))
                    .expectNextMatches(response -> {
                        assertThat(response.getPageId()).isEqualTo(pageIdStr);
                        assertThat(response.getUserId()).isEqualTo(targetUserIdStr);
                        assertThat(response.getPermission()).isEqualTo(PagePermissionType.READ.name());
                        return true;
                    })
                    .verifyComplete();
        }
    }

    @Test
    @Order(20)
    @DisplayName("페이지 멤버 초대 성공 - FULL_ACCESS 권한 있는 멤버")
    void inviteMemberToPage_Success_FullAccessMember() {
        // given
        Workspace memberWorkspace = Workspace.builder()
                .id(workspaceId)
                .name("Test Workspace")
                .createdBy(UUID.randomUUID()) // 다른 사용자가 소유자
                .build();

        targetUserId = UUID.randomUUID();
        targetUserIdStr = targetUserId.toString();

        PageInviteRequestDto request = PageInviteRequestDto.builder()
                .userId(targetUserIdStr)
                .permissionType(PagePermissionType.EDIT.name())
                .build();

        PagePermission clientPermission = PagePermission.builder()
                .pageId(pageId)
                .userId(userId)
                .permission(PagePermissionType.FULL_ACCESS.name())
                .build();

        PagePermission newPermission = PagePermission.builder()
                .pageId(pageId)
                .userId(targetUserId)
                .permission(PagePermissionType.EDIT.name())
                .build();

        when(uuidUtils.fromString(workspaceIdStr)).thenReturn(workspaceId);
        when(uuidUtils.fromString(pageIdStr)).thenReturn(pageId);
        when(uuidUtils.fromString(targetUserIdStr)).thenReturn(targetUserId);
        when(reactiveWorkspaceRepository.findById(workspaceId)).thenReturn(Mono.just(memberWorkspace));
        when(reactivePageRepository.findByIdAndWorkspaceId(pageId, workspaceId))
                .thenReturn(Mono.just(page));
        when(reactivePagePermissionRepository.findByPageIdAndUserId(pageId, userId))
                .thenReturn(Mono.just(clientPermission));
        when(reactiveWorkspaceMemberRepository.existsByWorkspaceIdAndUserId(workspaceId, targetUserId))
                .thenReturn(Mono.just(true));
        when(reactivePagePermissionRepository.findByPageIdAndUserId(pageId, targetUserId))
                .thenReturn(Mono.empty());
        when(reactivePagePermissionRepository.save(any(PagePermission.class)))
                .thenReturn(Mono.just(newPermission));

        try (MockedStatic<ReactiveSecurityContextHolder> mockedSecurityContext =
                     mockStatic(ReactiveSecurityContextHolder.class)) {

            mockedSecurityContext.when(ReactiveSecurityContextHolder::getContext)
                    .thenReturn(Mono.just(securityContext));

            // when & then
            StepVerifier.create(pageService.inviteMemberToPage(workspaceIdStr, pageIdStr, request))
                    .expectNextMatches(response -> {
                        assertThat(response.getPermission()).isEqualTo(PagePermissionType.EDIT.name());
                        return true;
                    })
                    .verifyComplete();
        }
    }

    @Test
    @Order(21)
    @DisplayName("페이지 멤버 초대 성공 - 기존 권한 업데이트")
    void inviteMemberToPage_Success_UpdateExistingPermission() {
        // given
        targetUserId = UUID.randomUUID();
        targetUserIdStr = targetUserId.toString();

        PageInviteRequestDto request = PageInviteRequestDto.builder()
                .userId(targetUserIdStr)
                .permissionType(PagePermissionType.EDIT.name())
                .build();

        PagePermission existingPermission = PagePermission.builder()
                .pageId(pageId)
                .userId(targetUserId)
                .permission(PagePermissionType.READ.name())
                .build();

        when(uuidUtils.fromString(workspaceIdStr)).thenReturn(workspaceId);
        when(uuidUtils.fromString(pageIdStr)).thenReturn(pageId);
        when(uuidUtils.fromString(targetUserIdStr)).thenReturn(targetUserId);
        when(reactiveWorkspaceRepository.findById(workspaceId)).thenReturn(Mono.just(workspace));
        when(reactivePageRepository.findByIdAndWorkspaceId(pageId, workspaceId))
                .thenReturn(Mono.just(page));
        when(reactiveWorkspaceMemberRepository.existsByWorkspaceIdAndUserId(workspaceId, targetUserId))
                .thenReturn(Mono.just(true));
        when(reactivePagePermissionRepository.findByPageIdAndUserId(pageId, targetUserId))
                .thenReturn(Mono.just(existingPermission));
        when(reactivePagePermissionRepository.save(any(PagePermission.class)))
                .thenReturn(Mono.just(existingPermission));

        try (MockedStatic<ReactiveSecurityContextHolder> mockedSecurityContext =
                     mockStatic(ReactiveSecurityContextHolder.class)) {

            mockedSecurityContext.when(ReactiveSecurityContextHolder::getContext)
                    .thenReturn(Mono.just(securityContext));

            // when & then
            StepVerifier.create(pageService.inviteMemberToPage(workspaceIdStr, pageIdStr, request))
                    .expectNextCount(1)
                    .verifyComplete();
        }
    }

    @Test
    @Order(22)
    @DisplayName("페이지 멤버 초대 실패 - FULL_ACCESS 권한 없음")
    void inviteMemberToPage_Fail_NoFullAccessPermission() {
        // given
        Workspace memberWorkspace = Workspace.builder()
                .id(workspaceId)
                .name("Test Workspace")
                .createdBy(UUID.randomUUID())
                .build();

        targetUserId = UUID.randomUUID();
        targetUserIdStr = targetUserId.toString();

        PageInviteRequestDto request = PageInviteRequestDto.builder()
                .userId(targetUserIdStr)
                .permissionType(PagePermissionType.READ.name())
                .build();

        PagePermission clientPermission = PagePermission.builder()
                .pageId(pageId)
                .userId(userId)
                .permission(PagePermissionType.EDIT.name()) // FULL_ACCESS가 아님
                .build();

        when(uuidUtils.fromString(workspaceIdStr)).thenReturn(workspaceId);
        when(uuidUtils.fromString(pageIdStr)).thenReturn(pageId);
        when(reactiveWorkspaceRepository.findById(workspaceId)).thenReturn(Mono.just(memberWorkspace));
        when(reactivePageRepository.findByIdAndWorkspaceId(pageId, workspaceId))
                .thenReturn(Mono.just(page));
        when(reactivePagePermissionRepository.findByPageIdAndUserId(pageId, userId))
                .thenReturn(Mono.just(clientPermission));

        try (MockedStatic<ReactiveSecurityContextHolder> mockedSecurityContext =
                     mockStatic(ReactiveSecurityContextHolder.class)) {

            mockedSecurityContext.when(ReactiveSecurityContextHolder::getContext)
                    .thenReturn(Mono.just(securityContext));

            // when & then
            StepVerifier.create(pageService.inviteMemberToPage(workspaceIdStr, pageIdStr, request))
                    .expectError(PagePermissionException.class)
                    .verify();
        }
    }

    @Test
    @Order(23)
    @DisplayName("페이지 멤버 초대 실패 - 초대할 사용자가 워크스페이스 멤버가 아님")
    void inviteMemberToPage_Fail_InvitedUserNotWorkspaceMember() {
        // given
        targetUserId = UUID.randomUUID();
        targetUserIdStr = targetUserId.toString();

        PageInviteRequestDto request = PageInviteRequestDto.builder()
                .userId(targetUserIdStr)
                .permissionType(PagePermissionType.READ.name())
                .build();

        when(uuidUtils.fromString(workspaceIdStr)).thenReturn(workspaceId);
        when(uuidUtils.fromString(pageIdStr)).thenReturn(pageId);
        when(uuidUtils.fromString(targetUserIdStr)).thenReturn(targetUserId);
        when(reactiveWorkspaceRepository.findById(workspaceId)).thenReturn(Mono.just(workspace));
        when(reactivePageRepository.findByIdAndWorkspaceId(pageId, workspaceId))
                .thenReturn(Mono.just(page));
        when(reactiveWorkspaceMemberRepository.existsByWorkspaceIdAndUserId(workspaceId, targetUserId))
                .thenReturn(Mono.just(false)); // 워크스페이스 멤버가 아님

        try (MockedStatic<ReactiveSecurityContextHolder> mockedSecurityContext =
                     mockStatic(ReactiveSecurityContextHolder.class)) {

            mockedSecurityContext.when(ReactiveSecurityContextHolder::getContext)
                    .thenReturn(Mono.just(securityContext));

            // when & then
            StepVerifier.create(pageService.inviteMemberToPage(workspaceIdStr, pageIdStr, request))
                    .expectError(WorkspaceMemberException.class)
                    .verify();
        }
    }
    
    @Test
    @Order(24)
    @DisplayName("페이지 멤버 권한 수정 성공 - 워크스페이스 소유자")
    void updateMemberPagePermission_Success_WorkspaceOwner() {
        // given
        targetUserId = UUID.randomUUID();
        targetUserIdStr = targetUserId.toString();

        PageUpdatePermissionRequestDto request = PageUpdatePermissionRequestDto.builder()
                .permissionType(PagePermissionType.EDIT.name())
                .build();

        PagePermission existingPermission = PagePermission.builder()
                .id(UUID.randomUUID())
                .pageId(pageId)
                .userId(targetUserId)
                .permission(PagePermissionType.READ.name())
                .build();

        Page testPage = Page.builder()
                .id(pageId)
                .workspaceId(workspaceId)
                .parentPageId(parentPageId)
                .title("Test Page")
                .isPublic(false)
                .isArchived(false)
                .isTemplate(false)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .createdBy(userId)
                .lastEditedBy(userId)
                .build();

        when(uuidUtils.fromString(workspaceIdStr)).thenReturn(workspaceId);
        when(uuidUtils.fromString(pageIdStr)).thenReturn(pageId);
        when(uuidUtils.fromString(targetUserIdStr)).thenReturn(targetUserId);
        when(reactiveWorkspaceRepository.findById(workspaceId)).thenReturn(Mono.just(workspace));
        when(reactivePageRepository.findByIdAndWorkspaceId(pageId, workspaceId))
                .thenReturn(Mono.just(testPage)); // testPage 사용
        when(reactiveWorkspaceMemberRepository.existsByWorkspaceIdAndUserId(workspaceId, targetUserId))
                .thenReturn(Mono.just(true));
        when(reactivePagePermissionRepository.findByPageIdAndUserId(pageId, targetUserId))
                .thenReturn(Mono.just(existingPermission));
        when(reactivePagePermissionRepository.save(any(PagePermission.class)))
                .thenAnswer(invocation -> {
                    PagePermission savedPermission = invocation.getArgument(0);
                    System.out.println("Saving permission - PageId: " + savedPermission.getPageId());
                    System.out.println("Saving permission - UserId: " + savedPermission.getUserId());
                    System.out.println("Saving permission - Permission: " + savedPermission.getPermission());
                    return Mono.just(savedPermission);
                });

        try (MockedStatic<ReactiveSecurityContextHolder> mockedSecurityContext =
                     mockStatic(ReactiveSecurityContextHolder.class)) {

            mockedSecurityContext.when(ReactiveSecurityContextHolder::getContext)
                    .thenReturn(Mono.just(securityContext));

            // when & then
            StepVerifier.create(pageService.updateMemberPagePermission(workspaceIdStr, pageIdStr, targetUserIdStr, request))
                    .expectNextMatches(response -> {
                        assertThat(response.getPageId()).isEqualTo(pageIdStr);
                        assertThat(response.getUserId()).isEqualTo(targetUserIdStr);
                        return true;
                    })
                    .verifyComplete();
        }
    }

    @Test
    @Order(25)
    @DisplayName("페이지 멤버 권한 수정 실패 - 페이지 소유자 권한은 변경 불가")
    void updateMemberPagePermission_Fail_CannotChangeOwnerPermission() {
        // given
        UUID pageOwnerId = userId; // 페이지 소유자와 동일
        String pageOwnerIdStr = pageOwnerId.toString();

        PageUpdatePermissionRequestDto request = PageUpdatePermissionRequestDto.builder()
                .permissionType(PagePermissionType.READ.name())
                .build();

        when(uuidUtils.fromString(workspaceIdStr)).thenReturn(workspaceId);
        when(uuidUtils.fromString(pageIdStr)).thenReturn(pageId);
        when(uuidUtils.fromString(pageOwnerIdStr)).thenReturn(pageOwnerId);
        when(reactiveWorkspaceRepository.findById(workspaceId)).thenReturn(Mono.just(workspace));
        when(reactivePageRepository.findByIdAndWorkspaceId(pageId, workspaceId))
                .thenReturn(Mono.just(page));
        when(reactiveWorkspaceMemberRepository.existsByWorkspaceIdAndUserId(workspaceId, pageOwnerId))
                .thenReturn(Mono.just(true));

        try (MockedStatic<ReactiveSecurityContextHolder> mockedSecurityContext =
                     mockStatic(ReactiveSecurityContextHolder.class)) {

            mockedSecurityContext.when(ReactiveSecurityContextHolder::getContext)
                    .thenReturn(Mono.just(securityContext));

            // when & then
            StepVerifier.create(pageService.updateMemberPagePermission(workspaceIdStr, pageIdStr, pageOwnerIdStr, request))
                    .expectError(PagePermissionException.class)
                    .verify();
        }
    }

    @Test
    @Order(26)
    @DisplayName("페이지 멤버 권한 수정 실패 - 권한이 존재하지 않음")
    void updateMemberPagePermission_Fail_PermissionNotFound() {
        // given
        targetUserId = UUID.randomUUID();
        targetUserIdStr = targetUserId.toString();

        PageUpdatePermissionRequestDto request = PageUpdatePermissionRequestDto.builder()
                .permissionType(PagePermissionType.EDIT.name())
                .build();

        when(uuidUtils.fromString(workspaceIdStr)).thenReturn(workspaceId);
        when(uuidUtils.fromString(pageIdStr)).thenReturn(pageId);
        when(uuidUtils.fromString(targetUserIdStr)).thenReturn(targetUserId);
        when(reactiveWorkspaceRepository.findById(workspaceId)).thenReturn(Mono.just(workspace));
        when(reactivePageRepository.findByIdAndWorkspaceId(pageId, workspaceId))
                .thenReturn(Mono.just(page));
        when(reactiveWorkspaceMemberRepository.existsByWorkspaceIdAndUserId(workspaceId, targetUserId))
                .thenReturn(Mono.just(true));
        when(reactivePagePermissionRepository.findByPageIdAndUserId(pageId, targetUserId))
                .thenReturn(Mono.empty()); // 권한이 존재하지 않음

        try (MockedStatic<ReactiveSecurityContextHolder> mockedSecurityContext =
                     mockStatic(ReactiveSecurityContextHolder.class)) {

            mockedSecurityContext.when(ReactiveSecurityContextHolder::getContext)
                    .thenReturn(Mono.just(securityContext));

            // when & then
            StepVerifier.create(pageService.updateMemberPagePermission(workspaceIdStr, pageIdStr, targetUserIdStr, request))
                    .expectError(PagePermissionException.class)
                    .verify();
        }
    }

    @Test
    @Order(27)
    @DisplayName("페이지 공개 상태 변경 성공 - 워크스페이스 소유자")
    void updatePagePublicStatus_Success_WorkspaceOwner() {
        // given
        PagePublicStatusUpdateRequestDto request = PagePublicStatusUpdateRequestDto.builder()
                .isPublic(true)
                .build();

        when(uuidUtils.fromString(workspaceIdStr)).thenReturn(workspaceId);
        when(uuidUtils.fromString(pageIdStr)).thenReturn(pageId);
        when(reactiveWorkspaceRepository.findById(workspaceId)).thenReturn(Mono.just(workspace));
        when(reactivePageRepository.findByIdAndWorkspaceId(pageId, workspaceId))
                .thenReturn(Mono.just(page));
        when(reactivePageRepository.save(any(Page.class))).thenReturn(Mono.just(page));

        try (MockedStatic<ReactiveSecurityContextHolder> mockedSecurityContext =
                     mockStatic(ReactiveSecurityContextHolder.class)) {

            mockedSecurityContext.when(ReactiveSecurityContextHolder::getContext)
                    .thenReturn(Mono.just(securityContext));

            // when & then
            StepVerifier.create(pageService.updatePagePublicStatus(workspaceIdStr, pageIdStr, request))
                    .expectNextMatches(response -> {
                        assertThat(response.getPageId()).isEqualTo(pageIdStr);
                        assertThat(response.getIsPublic()).isTrue();
                        return true;
                    })
                    .verifyComplete();
        }
    }

    @Test
    @Order(28)
    @DisplayName("페이지 공개 상태 변경 성공 - FULL_ACCESS 권한 있는 멤버")
    void updatePagePublicStatus_Success_FullAccessMember() {
        // given
        Workspace memberWorkspace = Workspace.builder()
                .id(workspaceId)
                .name("Test Workspace")
                .createdBy(UUID.randomUUID())
                .build();

        PagePublicStatusUpdateRequestDto request = PagePublicStatusUpdateRequestDto.builder()
                .isPublic(false)
                .build();

        PagePermission fullAccessPermission = PagePermission.builder()
                .pageId(pageId)
                .userId(userId)
                .permission(PagePermissionType.FULL_ACCESS.name())
                .build();

        when(uuidUtils.fromString(workspaceIdStr)).thenReturn(workspaceId);
        when(uuidUtils.fromString(pageIdStr)).thenReturn(pageId);
        when(reactiveWorkspaceRepository.findById(workspaceId)).thenReturn(Mono.just(memberWorkspace));
        when(reactivePageRepository.findByIdAndWorkspaceId(pageId, workspaceId))
                .thenReturn(Mono.just(page));
        when(reactivePagePermissionRepository.findByPageIdAndUserId(pageId, userId))
                .thenReturn(Mono.just(fullAccessPermission));
        when(reactivePageRepository.save(any(Page.class))).thenReturn(Mono.just(page));

        try (MockedStatic<ReactiveSecurityContextHolder> mockedSecurityContext =
                     mockStatic(ReactiveSecurityContextHolder.class)) {

            mockedSecurityContext.when(ReactiveSecurityContextHolder::getContext)
                    .thenReturn(Mono.just(securityContext));

            // when & then
            StepVerifier.create(pageService.updatePagePublicStatus(workspaceIdStr, pageIdStr, request))
                    .expectNextMatches(response -> {
                        assertThat(response.getIsPublic()).isFalse();
                        return true;
                    })
                    .verifyComplete();
        }
    }

    @Test
    @Order(29)
    @DisplayName("페이지 공개 상태 변경 실패 - FULL_ACCESS 권한 없음")
    void updatePagePublicStatus_Fail_NoFullAccessPermission() {
        // given
        Workspace memberWorkspace = Workspace.builder()
                .id(workspaceId)
                .name("Test Workspace")
                .createdBy(UUID.randomUUID())
                .build();

        PagePublicStatusUpdateRequestDto request = PagePublicStatusUpdateRequestDto.builder()
                .isPublic(true)
                .build();

        PagePermission editPermission = PagePermission.builder()
                .pageId(pageId)
                .userId(userId)
                .permission(PagePermissionType.EDIT.name()) // FULL_ACCESS가 아님
                .build();

        when(uuidUtils.fromString(workspaceIdStr)).thenReturn(workspaceId);
        when(uuidUtils.fromString(pageIdStr)).thenReturn(pageId);
        when(reactiveWorkspaceRepository.findById(workspaceId)).thenReturn(Mono.just(memberWorkspace));
        when(reactivePageRepository.findByIdAndWorkspaceId(pageId, workspaceId))
                .thenReturn(Mono.just(page));
        when(reactivePagePermissionRepository.findByPageIdAndUserId(pageId, userId))
                .thenReturn(Mono.just(editPermission));

        try (MockedStatic<ReactiveSecurityContextHolder> mockedSecurityContext =
                     mockStatic(ReactiveSecurityContextHolder.class)) {

            mockedSecurityContext.when(ReactiveSecurityContextHolder::getContext)
                    .thenReturn(Mono.just(securityContext));

            // when & then
            StepVerifier.create(pageService.updatePagePublicStatus(workspaceIdStr, pageIdStr, request))
                    .expectError(PagePermissionException.class)
                    .verify();
        }
    }

    @Test
    @Order(30)
    @DisplayName("페이지 아카이브 성공 - 페이지 소유자")
    void archivePage_Success_PageOwner() {
        // given
        when(uuidUtils.fromString(workspaceIdStr)).thenReturn(workspaceId);
        when(uuidUtils.fromString(pageIdStr)).thenReturn(pageId);
        when(reactiveWorkspaceRepository.findById(workspaceId)).thenReturn(Mono.just(workspace));
        when(reactivePageRepository.findByIdAndWorkspaceId(pageId, workspaceId))
                .thenReturn(Mono.just(page)); // page의 createdBy가 userId와 동일
        when(reactivePageRepository.updateArchiveStatusForTree(pageId, true, userId))
                .thenReturn(Mono.empty()); // 업데이트된 행 수
        when(reactiveBlockRepository.updateArchiveStatusForPageTree(pageId, true))
                .thenReturn(Mono.empty());

        try (MockedStatic<ReactiveSecurityContextHolder> mockedSecurityContext =
                     mockStatic(ReactiveSecurityContextHolder.class)) {

            mockedSecurityContext.when(ReactiveSecurityContextHolder::getContext)
                    .thenReturn(Mono.just(securityContext));

            // when & then
            StepVerifier.create(pageService.archivePage(workspaceIdStr, pageIdStr))
                    .expectNextMatches(response -> {
                        assertThat(response.getPageId()).isEqualTo(pageIdStr);
                        assertThat(response.getIsArchived()).isTrue();
                        return true;
                    })
                    .verifyComplete();
        }
    }

    @Test
    @Order(31)
    @DisplayName("페이지 아카이브 성공 - FULL_ACCESS 권한 있는 사용자")
    void archivePage_Success_FullAccessUser() {
        // given
        UUID otherUserId = UUID.randomUUID();
        Page otherUserPage = Page.builder()
                .id(pageId)
                .workspaceId(workspaceId)
                .parentPageId(parentPageId)
                .title("Other User Page")
                .isPublic(false)
                .isArchived(false)
                .isTemplate(false)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .createdBy(otherUserId) // 다른 사용자가 생성
                .lastEditedBy(otherUserId)
                .build();

        PagePermission fullAccessPermission = PagePermission.builder()
                .pageId(pageId)
                .userId(userId)
                .permission(PagePermissionType.FULL_ACCESS.name())
                .build();

        when(uuidUtils.fromString(workspaceIdStr)).thenReturn(workspaceId);
        when(uuidUtils.fromString(pageIdStr)).thenReturn(pageId);
        when(reactiveWorkspaceRepository.findById(workspaceId)).thenReturn(Mono.just(workspace));
        when(reactivePageRepository.findByIdAndWorkspaceId(pageId, workspaceId))
                .thenReturn(Mono.just(otherUserPage));
        when(reactivePagePermissionRepository.findByPageIdAndUserId(pageId, userId))
                .thenReturn(Mono.just(fullAccessPermission));
        when(reactivePageRepository.updateArchiveStatusForTree(pageId, true, otherUserId))
                .thenReturn(Mono.empty());
        when(reactiveBlockRepository.updateArchiveStatusForPageTree(pageId, true))
                .thenReturn(Mono.empty());

        try (MockedStatic<ReactiveSecurityContextHolder> mockedSecurityContext =
                     mockStatic(ReactiveSecurityContextHolder.class)) {

            mockedSecurityContext.when(ReactiveSecurityContextHolder::getContext)
                    .thenReturn(Mono.just(securityContext));

            // when & then
            StepVerifier.create(pageService.archivePage(workspaceIdStr, pageIdStr))
                    .expectNextMatches(response -> {
                        assertThat(response.getPageId()).isEqualTo(pageIdStr);
                        assertThat(response.getIsArchived()).isTrue();
                        return true;
                    })
                    .verifyComplete();
        }
    }

    @Test
    @Order(32)
    @DisplayName("페이지 아카이브 실패 - FULL_ACCESS 권한 없음")
    void archivePage_Fail_NoFullAccessPermission() {
        // given
        UUID otherUserId = UUID.randomUUID();
        Page otherUserPage = Page.builder()
                .id(pageId)
                .workspaceId(workspaceId)
                .parentPageId(parentPageId)
                .title("Other User Page")
                .createdBy(otherUserId)
                .lastEditedBy(otherUserId)
                .build();

        PagePermission editPermission = PagePermission.builder()
                .pageId(pageId)
                .userId(userId)
                .permission(PagePermissionType.EDIT.name()) // FULL_ACCESS가 아님
                .build();

        when(uuidUtils.fromString(workspaceIdStr)).thenReturn(workspaceId);
        when(uuidUtils.fromString(pageIdStr)).thenReturn(pageId);
        when(reactiveWorkspaceRepository.findById(workspaceId)).thenReturn(Mono.just(workspace));
        when(reactivePageRepository.findByIdAndWorkspaceId(pageId, workspaceId))
                .thenReturn(Mono.just(otherUserPage));
        when(reactivePagePermissionRepository.findByPageIdAndUserId(pageId, userId))
                .thenReturn(Mono.just(editPermission));

        try (MockedStatic<ReactiveSecurityContextHolder> mockedSecurityContext =
                     mockStatic(ReactiveSecurityContextHolder.class)) {

            mockedSecurityContext.when(ReactiveSecurityContextHolder::getContext)
                    .thenReturn(Mono.just(securityContext));

            // when & then
            StepVerifier.create(pageService.archivePage(workspaceIdStr, pageIdStr))
                    .expectError(PagePermissionException.class)
                    .verify();
        }
    }

    @Test
    @Order(33)
    @DisplayName("페이지 아카이브 실패 - 페이지 권한 없음")
    void archivePage_Fail_NoPagePermission() {
        // given
        UUID otherUserId = UUID.randomUUID();
        Page otherUserPage = Page.builder()
                .id(pageId)
                .workspaceId(workspaceId)
                .parentPageId(parentPageId)
                .title("Other User Page")
                .createdBy(otherUserId)
                .lastEditedBy(otherUserId)
                .build();

        when(uuidUtils.fromString(workspaceIdStr)).thenReturn(workspaceId);
        when(uuidUtils.fromString(pageIdStr)).thenReturn(pageId);
        when(reactiveWorkspaceRepository.findById(workspaceId)).thenReturn(Mono.just(workspace));
        when(reactivePageRepository.findByIdAndWorkspaceId(pageId, workspaceId))
                .thenReturn(Mono.just(otherUserPage));
        when(reactivePagePermissionRepository.findByPageIdAndUserId(pageId, userId))
                .thenReturn(Mono.empty()); // 권한 없음

        try (MockedStatic<ReactiveSecurityContextHolder> mockedSecurityContext =
                     mockStatic(ReactiveSecurityContextHolder.class)) {

            mockedSecurityContext.when(ReactiveSecurityContextHolder::getContext)
                    .thenReturn(Mono.just(securityContext));

            // when & then
            StepVerifier.create(pageService.archivePage(workspaceIdStr, pageIdStr))
                    .expectError(PagePermissionException.class)
                    .verify();
        }
    }

    @Test
    @Order(34)
    @DisplayName("페이지 복원 성공 - 페이지 소유자")
    void restorePage_Success_PageOwner() {
        // given
        when(uuidUtils.fromString(workspaceIdStr)).thenReturn(workspaceId);
        when(uuidUtils.fromString(pageIdStr)).thenReturn(pageId);
        when(reactiveWorkspaceRepository.findById(workspaceId)).thenReturn(Mono.just(workspace));
        when(reactivePageRepository.findByIdAndWorkspaceId(pageId, workspaceId))
                .thenReturn(Mono.just(page));
        when(reactivePageRepository.updateArchiveStatusForTree(pageId, false, userId))
                .thenReturn(Mono.empty());
        when(reactiveBlockRepository.updateArchiveStatusForPageTree(pageId, false))
                .thenReturn(Mono.empty());

        try (MockedStatic<ReactiveSecurityContextHolder> mockedSecurityContext =
                     mockStatic(ReactiveSecurityContextHolder.class)) {

            mockedSecurityContext.when(ReactiveSecurityContextHolder::getContext)
                    .thenReturn(Mono.just(securityContext));

            // when & then
            StepVerifier.create(pageService.restorePage(workspaceIdStr, pageIdStr))
                    .expectNextMatches(response -> {
                        assertThat(response.getPageId()).isEqualTo(pageIdStr);
                        assertThat(response.getIsArchived()).isFalse();
                        return true;
                    })
                    .verifyComplete();
        }
    }

    @Test
    @Order(35)
    @DisplayName("페이지 복원 성공 - FULL_ACCESS 권한 있는 사용자")
    void restorePage_Success_FullAccessUser() {
        // given
        UUID otherUserId = UUID.randomUUID();
        Page otherUserPage = Page.builder()
                .id(pageId)
                .workspaceId(workspaceId)
                .parentPageId(parentPageId)
                .title("Other User Page")
                .createdBy(otherUserId)
                .lastEditedBy(otherUserId)
                .build();

        PagePermission fullAccessPermission = PagePermission.builder()
                .pageId(pageId)
                .userId(userId)
                .permission(PagePermissionType.FULL_ACCESS.name())
                .build();

        when(uuidUtils.fromString(workspaceIdStr)).thenReturn(workspaceId);
        when(uuidUtils.fromString(pageIdStr)).thenReturn(pageId);
        when(reactiveWorkspaceRepository.findById(workspaceId)).thenReturn(Mono.just(workspace));
        when(reactivePageRepository.findByIdAndWorkspaceId(pageId, workspaceId))
                .thenReturn(Mono.just(otherUserPage));
        when(reactivePagePermissionRepository.findByPageIdAndUserId(pageId, userId))
                .thenReturn(Mono.just(fullAccessPermission));
        when(reactivePageRepository.updateArchiveStatusForTree(pageId, false, otherUserId))
                .thenReturn(Mono.empty());
        when(reactiveBlockRepository.updateArchiveStatusForPageTree(pageId, false))
                .thenReturn(Mono.empty());

        try (MockedStatic<ReactiveSecurityContextHolder> mockedSecurityContext =
                     mockStatic(ReactiveSecurityContextHolder.class)) {

            mockedSecurityContext.when(ReactiveSecurityContextHolder::getContext)
                    .thenReturn(Mono.just(securityContext));

            // when & then
            StepVerifier.create(pageService.restorePage(workspaceIdStr, pageIdStr))
                    .expectNextMatches(response -> {
                        assertThat(response.getPageId()).isEqualTo(pageIdStr);
                        assertThat(response.getIsArchived()).isFalse();
                        return true;
                    })
                    .verifyComplete();
        }
    }

    @Test
    @Order(36)
    @DisplayName("페이지 복원 실패 - 페이지 없음")
    void restorePage_Fail_PageNotFound() {
        // given
        when(uuidUtils.fromString(workspaceIdStr)).thenReturn(workspaceId);
        when(uuidUtils.fromString(pageIdStr)).thenReturn(pageId);
        when(reactiveWorkspaceRepository.findById(workspaceId)).thenReturn(Mono.just(workspace));
        when(reactivePageRepository.findByIdAndWorkspaceId(pageId, workspaceId))
                .thenReturn(Mono.empty()); // 페이지 없음

        try (MockedStatic<ReactiveSecurityContextHolder> mockedSecurityContext =
                     mockStatic(ReactiveSecurityContextHolder.class)) {

            mockedSecurityContext.when(ReactiveSecurityContextHolder::getContext)
                    .thenReturn(Mono.just(securityContext));

            // when & then
            StepVerifier.create(pageService.restorePage(workspaceIdStr, pageIdStr))
                    .expectError(PageException.class)
                    .verify();
        }
    }

    @Test
    @Order(37)
    @DisplayName("페이지 삭제 성공 - 페이지 소유자")
    void deletePage_Success_PageOwner() {
        // given
        when(uuidUtils.fromString(workspaceIdStr)).thenReturn(workspaceId);
        when(uuidUtils.fromString(pageIdStr)).thenReturn(pageId);
        when(reactiveWorkspaceRepository.findById(workspaceId)).thenReturn(Mono.just(workspace));
        when(reactivePageRepository.findByIdAndWorkspaceId(pageId, workspaceId))
                .thenReturn(Mono.just(page));
        when(reactiveBlockRepository.deleteAllByPageTree(pageId))
                .thenReturn(Mono.empty()); // 삭제된 블록 수
        when(reactivePageRepository.deletePageAndDescendants(pageId))
                .thenReturn(Mono.empty()); // 삭제된 페이지 수

        try (MockedStatic<ReactiveSecurityContextHolder> mockedSecurityContext =
                     mockStatic(ReactiveSecurityContextHolder.class)) {

            mockedSecurityContext.when(ReactiveSecurityContextHolder::getContext)
                    .thenReturn(Mono.just(securityContext));

            // when & then
            StepVerifier.create(pageService.deletePage(workspaceIdStr, pageIdStr))
                    .verifyComplete(); // Mono<Void>이므로 완료만 확인
        }

        // 삭제 메소드들이 호출되었는지 확인
        verify(reactiveBlockRepository).deleteAllByPageTree(pageId);
        verify(reactivePageRepository).deletePageAndDescendants(pageId);
    }

    @Test
    @Order(38)
    @DisplayName("페이지 삭제 성공 - FULL_ACCESS 권한 있는 사용자")
    void deletePage_Success_FullAccessUser() {
        // given
        UUID otherUserId = UUID.randomUUID();
        Page otherUserPage = Page.builder()
                .id(pageId)
                .workspaceId(workspaceId)
                .parentPageId(parentPageId)
                .title("Other User Page")
                .createdBy(otherUserId)
                .lastEditedBy(otherUserId)
                .build();

        PagePermission fullAccessPermission = PagePermission.builder()
                .pageId(pageId)
                .userId(userId)
                .permission(PagePermissionType.FULL_ACCESS.name())
                .build();

        when(uuidUtils.fromString(workspaceIdStr)).thenReturn(workspaceId);
        when(uuidUtils.fromString(pageIdStr)).thenReturn(pageId);
        when(reactiveWorkspaceRepository.findById(workspaceId)).thenReturn(Mono.just(workspace));
        when(reactivePageRepository.findByIdAndWorkspaceId(pageId, workspaceId))
                .thenReturn(Mono.just(otherUserPage));
        when(reactivePagePermissionRepository.findByPageIdAndUserId(pageId, userId))
                .thenReturn(Mono.just(fullAccessPermission));
        when(reactiveBlockRepository.deleteAllByPageTree(pageId))
                .thenReturn(Mono.empty());
        when(reactivePageRepository.deletePageAndDescendants(pageId))
                .thenReturn(Mono.empty());

        try (MockedStatic<ReactiveSecurityContextHolder> mockedSecurityContext =
                     mockStatic(ReactiveSecurityContextHolder.class)) {

            mockedSecurityContext.when(ReactiveSecurityContextHolder::getContext)
                    .thenReturn(Mono.just(securityContext));

            // when & then
            StepVerifier.create(pageService.deletePage(workspaceIdStr, pageIdStr))
                    .verifyComplete();
        }

        verify(reactiveBlockRepository).deleteAllByPageTree(pageId);
        verify(reactivePageRepository).deletePageAndDescendants(pageId);
    }

    @Test
    @Order(39)
    @DisplayName("페이지 삭제 실패 - FULL_ACCESS 권한 없음")
    void deletePage_Fail_NoFullAccessPermission() {
        // given
        UUID otherUserId = UUID.randomUUID();
        Page otherUserPage = Page.builder()
                .id(pageId)
                .workspaceId(workspaceId)
                .parentPageId(parentPageId)
                .title("Other User Page")
                .createdBy(otherUserId)
                .lastEditedBy(otherUserId)
                .build();

        PagePermission editPermission = PagePermission.builder()
                .pageId(pageId)
                .userId(userId)
                .permission(PagePermissionType.EDIT.name()) // FULL_ACCESS가 아님
                .build();

        when(uuidUtils.fromString(workspaceIdStr)).thenReturn(workspaceId);
        when(uuidUtils.fromString(pageIdStr)).thenReturn(pageId);
        when(reactiveWorkspaceRepository.findById(workspaceId)).thenReturn(Mono.just(workspace));
        when(reactivePageRepository.findByIdAndWorkspaceId(pageId, workspaceId))
                .thenReturn(Mono.just(otherUserPage));
        when(reactivePagePermissionRepository.findByPageIdAndUserId(pageId, userId))
                .thenReturn(Mono.just(editPermission));

        try (MockedStatic<ReactiveSecurityContextHolder> mockedSecurityContext =
                     mockStatic(ReactiveSecurityContextHolder.class)) {

            mockedSecurityContext.when(ReactiveSecurityContextHolder::getContext)
                    .thenReturn(Mono.just(securityContext));

            // when & then
            StepVerifier.create(pageService.deletePage(workspaceIdStr, pageIdStr))
                    .expectError(PagePermissionException.class)
                    .verify();
        }

        // 삭제 메소드들이 호출되지 않았는지 확인
        verify(reactiveBlockRepository, never()).deleteAllByPageTree(any());
        verify(reactivePageRepository, never()).deletePageAndDescendants(any());
    }

    @Test
    @Order(40)
    @DisplayName("페이지 삭제 실패 - 워크스페이스 없음")
    void deletePage_Fail_WorkspaceNotFound() {
        // given
        when(uuidUtils.fromString(workspaceIdStr)).thenReturn(workspaceId);
        when(uuidUtils.fromString(pageIdStr)).thenReturn(pageId);
        when(reactiveWorkspaceRepository.findById(workspaceId)).thenReturn(Mono.empty()); // 워크스페이스 없음

        try (MockedStatic<ReactiveSecurityContextHolder> mockedSecurityContext =
                     mockStatic(ReactiveSecurityContextHolder.class)) {

            mockedSecurityContext.when(ReactiveSecurityContextHolder::getContext)
                    .thenReturn(Mono.just(securityContext));

            // when & then
            StepVerifier.create(pageService.deletePage(workspaceIdStr, pageIdStr))
                    .expectError(WorkspaceException.class)
                    .verify();
        }
    }
}
