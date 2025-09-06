package com.example.share_note.service;

import com.example.share_note.domain.Page;
import com.example.share_note.domain.PagePermission;
import com.example.share_note.domain.Workspace;
import com.example.share_note.dto.CustomUserDetails;
import com.example.share_note.dto.page.*;
import com.example.share_note.enums.PagePermissionType;
import com.example.share_note.exception.ErrorCode;
import com.example.share_note.exception.PageException;
import com.example.share_note.exception.WorkspaceException;
import com.example.share_note.exception.WorkspaceMemberException;
import com.example.share_note.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
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

    @InjectMocks
    private PageService pageService;

    private CustomUserDetails mockUserDetails;
    private Workspace mockWorkspace;
    private Page mockParentPage;
    private Page mockCreatedPage;
    private PagePermission mockPagePermission;
    private PageCreateRequestDto requestDto;
    private SecurityContext mockSecurityContext;
    private Authentication mockAuthentication;
    private Page mockPage;
    private CustomUserDetails mockTargetUser;

    @BeforeEach
    void setUp() {
        mockUserDetails = mock(CustomUserDetails.class);
        lenient().when(mockUserDetails.getId()).thenReturn(1L);

        mockAuthentication = new UsernamePasswordAuthenticationToken(
                mockUserDetails,
                null,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"))
        );
        mockSecurityContext = mock(SecurityContext.class);
        lenient().when(mockSecurityContext.getAuthentication()).thenReturn(mockAuthentication);

        mockWorkspace = Workspace.builder()
                .id(1L)
                .name("Test Workspace")
                .createdBy(1L)
                .build();

        mockParentPage = Page.builder()
                .id(2L)
                .workspaceId(1L)
                .title("Parent Page")
                .createdBy(1L)
                .build();

        mockCreatedPage = Page.builder()
                .id(3L)
                .workspaceId(1L)
                .parentPageId(2L)
                .title("New Page")
                .createdAt(LocalDateTime.now())
                .createdBy(1L)
                .build();

        mockPage = Page.builder()
                .id(10L)
                .workspaceId(1L)
                .title("Original Title")
                .icon("📄")
                .cover("original-cover.jpg")
                .isPublic(true)
                .isArchived(false)
                .isTemplate(false)
                .createdBy(1L)
                .build();

        mockPagePermission = PagePermission.builder()
                .pageId(2L)
                .userId(1L)
                .permission(PagePermissionType.EDIT.name())
                .build();

        requestDto = PageCreateRequestDto.builder()
                .parentPageId(2L)
                .title("New Page")
                .icon("📄")
                .cover("cover.jpg")
                .properties("{\"color\": \"blue\"}")
                .build();
    }

    @Test
    @DisplayName("인증되지 않은 사용자가 페이지 생성 시 예외 발생")
    void createPage_WhenNotAuthenticated_ShouldThrowException() {
        // given
        try (MockedStatic<ReactiveSecurityContextHolder> mockedHolder = mockStatic(ReactiveSecurityContextHolder.class)) {
            mockedHolder.when(ReactiveSecurityContextHolder::getContext).thenReturn(Mono.empty());

            // when & then
            StepVerifier.create(pageService.createPage(1L, requestDto))
                    .expectError(WorkspaceException.class)
                    .verify();
        }
    }

    @Test
    @DisplayName("존재하지 않는 워크스페이스에 페이지 생성 시 예외 발생")
    void createPage_WhenWorkspaceNotFound_ShouldThrowException() {
        // given
        try (MockedStatic<ReactiveSecurityContextHolder> mockedHolder = mockStatic(ReactiveSecurityContextHolder.class)) {
            SecurityContext securityContext = mock(SecurityContext.class);
            Authentication authentication = mock(Authentication.class);

            mockedHolder.when(ReactiveSecurityContextHolder::getContext).thenReturn(Mono.just(securityContext));
            when(securityContext.getAuthentication()).thenReturn(authentication);
            when(authentication.getPrincipal()).thenReturn(mockUserDetails);
            when(reactiveWorkspaceRepository.findById(1L)).thenReturn(Mono.empty());

            // when & then
            StepVerifier.create(pageService.createPage(1L, requestDto))
                    .expectError(WorkspaceException.class)
                    .verify();
        }
    }

    @Test
    @DisplayName("워크스페이스 소유자가 페이지 생성 성공 - 부모 페이지 없음")
    void createPage_WhenWorkspaceOwner_WithoutParentPage_ShouldCreateSuccessfully() {
        // given
        PageCreateRequestDto requestWithoutParent = PageCreateRequestDto.builder()
                .title("Root Page")
                .build();

        Page savedPage = Page.builder()
                .id(3L)
                .workspaceId(1L)
                .title("Root Page")
                .createdAt(LocalDateTime.now())
                .createdBy(1L)
                .build();

        try (MockedStatic<ReactiveSecurityContextHolder> mockedHolder = mockStatic(ReactiveSecurityContextHolder.class)) {
            SecurityContext securityContext = mock(SecurityContext.class);
            Authentication authentication = mock(Authentication.class);

            mockedHolder.when(ReactiveSecurityContextHolder::getContext).thenReturn(Mono.just(securityContext));
            when(securityContext.getAuthentication()).thenReturn(authentication);
            when(authentication.getPrincipal()).thenReturn(mockUserDetails);
            when(reactiveWorkspaceRepository.findById(1L)).thenReturn(Mono.just(mockWorkspace));
            when(reactivePageRepository.save(any(Page.class))).thenReturn(Mono.just(savedPage));

            // when & then
            StepVerifier.create(pageService.createPage(1L, requestWithoutParent))
                    .expectNextMatches(response ->
                            response.getPageId().equals(3L) &&
                                    response.getWorkspaceId().equals(1L) &&
                                    response.getCreatedBy().equals(1L)
                    )
                    .verifyComplete();

            verify(reactivePageRepository).save(any(Page.class));
        }
    }

    @Test
    @DisplayName("워크스페이스 멤버가 페이지 생성 성공")
    void createPage_WhenWorkspaceMember_ShouldCreateSuccessfully() {
        // given
        Workspace workspaceOwnedByOthers = Workspace.builder()
                .id(1L)
                .name("Test Workspace")
                .createdBy(2L) // 다른 사용자가 소유
                .build();

        PageCreateRequestDto requestWithoutParent = PageCreateRequestDto.builder()
                .title("Member Page")
                .build();

        Page savedPage = Page.builder()
                .id(3L)
                .workspaceId(1L)
                .title("Member Page")
                .createdAt(LocalDateTime.now())
                .createdBy(1L)
                .build();

        try (MockedStatic<ReactiveSecurityContextHolder> mockedHolder = mockStatic(ReactiveSecurityContextHolder.class)) {
            SecurityContext securityContext = mock(SecurityContext.class);
            Authentication authentication = mock(Authentication.class);

            mockedHolder.when(ReactiveSecurityContextHolder::getContext).thenReturn(Mono.just(securityContext));
            when(securityContext.getAuthentication()).thenReturn(authentication);
            when(authentication.getPrincipal()).thenReturn(mockUserDetails);
            when(reactiveWorkspaceRepository.findById(1L)).thenReturn(Mono.just(workspaceOwnedByOthers));
            when(reactiveWorkspaceMemberRepository.existsByWorkspaceIdAndUserId(1L, 1L)).thenReturn(Mono.just(true));
            when(reactivePageRepository.save(any(Page.class))).thenReturn(Mono.just(savedPage));

            // when & then
            StepVerifier.create(pageService.createPage(1L, requestWithoutParent))
                    .expectNextMatches(response ->
                            response.getPageId().equals(3L) &&
                                    response.getWorkspaceId().equals(1L)
                    )
                    .verifyComplete();
        }
    }

    @Test
    @DisplayName("워크스페이스 멤버가 아닌 경우 예외 발생")
    void createPage_WhenNotWorkspaceMember_ShouldThrowException() {
        // given
        Workspace workspaceOwnedByOthers = Workspace.builder()
                .id(1L)
                .name("Test Workspace")
                .createdBy(2L)
                .build();

        try (MockedStatic<ReactiveSecurityContextHolder> mockedHolder = mockStatic(ReactiveSecurityContextHolder.class)) {
            SecurityContext securityContext = mock(SecurityContext.class);
            Authentication authentication = mock(Authentication.class);

            mockedHolder.when(ReactiveSecurityContextHolder::getContext).thenReturn(Mono.just(securityContext));
            when(securityContext.getAuthentication()).thenReturn(authentication);
            when(authentication.getPrincipal()).thenReturn(mockUserDetails);
            when(reactiveWorkspaceRepository.findById(1L)).thenReturn(Mono.just(workspaceOwnedByOthers));
            when(reactiveWorkspaceMemberRepository.existsByWorkspaceIdAndUserId(1L, 1L)).thenReturn(Mono.just(false));

            // when & then
            StepVerifier.create(pageService.createPage(1L, requestDto))
                    .expectError(WorkspaceException.class)
                    .verify();
        }
    }

    @Test
    @DisplayName("존재하지 않는 부모 페이지로 페이지 생성 시 예외 발생")
    void createPage_WhenParentPageNotFound_ShouldThrowException() {
        // given
        try (MockedStatic<ReactiveSecurityContextHolder> mockedHolder = mockStatic(ReactiveSecurityContextHolder.class)) {
            SecurityContext securityContext = mock(SecurityContext.class);
            Authentication authentication = mock(Authentication.class);

            mockedHolder.when(ReactiveSecurityContextHolder::getContext).thenReturn(Mono.just(securityContext));
            when(securityContext.getAuthentication()).thenReturn(authentication);
            when(authentication.getPrincipal()).thenReturn(mockUserDetails);
            when(reactiveWorkspaceRepository.findById(1L)).thenReturn(Mono.just(mockWorkspace));
            when(reactivePageRepository.findByIdAndWorkspaceId(2L, 1L)).thenReturn(Mono.empty());

            // when & then
            StepVerifier.create(pageService.createPage(1L, requestDto))
                    .expectError(PageException.class)
                    .verify();
        }
    }

    @Test
    @DisplayName("부모 페이지에 대한 권한이 없는 경우 예외 발생")
    void createPage_WhenNoParentPagePermission_ShouldThrowException() {
        // given
        try (MockedStatic<ReactiveSecurityContextHolder> mockedHolder = mockStatic(ReactiveSecurityContextHolder.class)) {
            SecurityContext securityContext = mock(SecurityContext.class);
            Authentication authentication = mock(Authentication.class);

            mockedHolder.when(ReactiveSecurityContextHolder::getContext).thenReturn(Mono.just(securityContext));
            when(securityContext.getAuthentication()).thenReturn(authentication);
            when(authentication.getPrincipal()).thenReturn(mockUserDetails);
            when(reactiveWorkspaceRepository.findById(1L)).thenReturn(Mono.just(mockWorkspace));
            when(reactivePageRepository.findByIdAndWorkspaceId(2L, 1L)).thenReturn(Mono.just(mockParentPage));
            when(reactivePagePermissionRepository.findByPageIdAndUserId(2L, 1L)).thenReturn(Mono.empty());

            // when & then
            StepVerifier.create(pageService.createPage(1L, requestDto))
                    .expectError(PageException.class)
                    .verify();
        }
    }

    @Test
    @DisplayName("부모 페이지에 대한 권한이 부족한 경우 예외 발생")
    void createPage_WhenInsufficientParentPagePermission_ShouldThrowException() {
        // given
        PagePermission readOnlyPermission = PagePermission.builder()
                .pageId(2L)
                .userId(1L)
                .permission(PagePermissionType.READ.name())
                .build();

        try (MockedStatic<ReactiveSecurityContextHolder> mockedHolder = mockStatic(ReactiveSecurityContextHolder.class)) {
            SecurityContext securityContext = mock(SecurityContext.class);
            Authentication authentication = mock(Authentication.class);

            mockedHolder.when(ReactiveSecurityContextHolder::getContext).thenReturn(Mono.just(securityContext));
            when(securityContext.getAuthentication()).thenReturn(authentication);
            when(authentication.getPrincipal()).thenReturn(mockUserDetails);
            when(reactiveWorkspaceRepository.findById(1L)).thenReturn(Mono.just(mockWorkspace));
            when(reactivePageRepository.findByIdAndWorkspaceId(2L, 1L)).thenReturn(Mono.just(mockParentPage));
            when(reactivePagePermissionRepository.findByPageIdAndUserId(2L, 1L)).thenReturn(Mono.just(readOnlyPermission));

            // when & then
            StepVerifier.create(pageService.createPage(1L, requestDto))
                    .expectError(PageException.class)
                    .verify();
        }
    }

    @Test
    @DisplayName("부모 페이지에 편집 권한이 있는 경우 페이지 생성 성공")
    void createPage_WithEditPermissionOnParentPage_ShouldCreateSuccessfully() {
        // given
        try (MockedStatic<ReactiveSecurityContextHolder> mockedHolder = mockStatic(ReactiveSecurityContextHolder.class)) {
            SecurityContext securityContext = mock(SecurityContext.class);
            Authentication authentication = mock(Authentication.class);

            mockedHolder.when(ReactiveSecurityContextHolder::getContext).thenReturn(Mono.just(securityContext));
            when(securityContext.getAuthentication()).thenReturn(authentication);
            when(authentication.getPrincipal()).thenReturn(mockUserDetails);
            when(reactiveWorkspaceRepository.findById(1L)).thenReturn(Mono.just(mockWorkspace));
            when(reactivePageRepository.findByIdAndWorkspaceId(2L, 1L)).thenReturn(Mono.just(mockParentPage));
            when(reactivePagePermissionRepository.findByPageIdAndUserId(2L, 1L)).thenReturn(Mono.just(mockPagePermission));
            when(reactivePageRepository.save(any(Page.class))).thenReturn(Mono.just(mockCreatedPage));

            // when & then
            StepVerifier.create(pageService.createPage(1L, requestDto))
                    .expectNextMatches(response ->
                            response.getPageId().equals(3L) &&
                                    response.getWorkspaceId().equals(1L) &&
                                    response.getCreatedBy().equals(1L)
                    )
                    .verifyComplete();

            verify(reactivePageRepository).save(any(Page.class));
        }
    }

    @Test
    @DisplayName("부모 페이지에 관리자 권한이 있는 경우 페이지 생성 성공")
    void createPage_WithAdminPermissionOnParentPage_ShouldCreateSuccessfully() {
        // given
        PagePermission adminPermission = PagePermission.builder()
                .pageId(2L)
                .userId(1L)
                .permission(PagePermissionType.FULL_ACCESS.name())
                .build();

        try (MockedStatic<ReactiveSecurityContextHolder> mockedHolder = mockStatic(ReactiveSecurityContextHolder.class)) {
            SecurityContext securityContext = mock(SecurityContext.class);
            Authentication authentication = mock(Authentication.class);

            mockedHolder.when(ReactiveSecurityContextHolder::getContext).thenReturn(Mono.just(securityContext));
            when(securityContext.getAuthentication()).thenReturn(authentication);
            when(authentication.getPrincipal()).thenReturn(mockUserDetails);
            when(reactiveWorkspaceRepository.findById(1L)).thenReturn(Mono.just(mockWorkspace));
            when(reactivePageRepository.findByIdAndWorkspaceId(2L, 1L)).thenReturn(Mono.just(mockParentPage));
            when(reactivePagePermissionRepository.findByPageIdAndUserId(2L, 1L)).thenReturn(Mono.just(adminPermission));
            when(reactivePageRepository.save(any(Page.class))).thenReturn(Mono.just(mockCreatedPage));

            // when & then
            StepVerifier.create(pageService.createPage(1L, requestDto))
                    .expectNextMatches(response -> response.getPageId().equals(3L))
                    .verifyComplete();
        }
    }

    @Test
    @DisplayName("제목이 null인 경우 'Untitled'로 설정")
    void createPage_WhenTitleIsNull_ShouldSetUntitled() {
        // given
        PageCreateRequestDto requestWithoutTitle = PageCreateRequestDto.builder()
                .title(null)
                .build();

        Page savedPageWithUntitled = Page.builder()
                .id(3L)
                .workspaceId(1L)
                .title("Untitled")
                .createdAt(LocalDateTime.now())
                .createdBy(1L)
                .build();

        try (MockedStatic<ReactiveSecurityContextHolder> mockedHolder = mockStatic(ReactiveSecurityContextHolder.class)) {
            SecurityContext securityContext = mock(SecurityContext.class);
            Authentication authentication = mock(Authentication.class);

            mockedHolder.when(ReactiveSecurityContextHolder::getContext).thenReturn(Mono.just(securityContext));
            when(securityContext.getAuthentication()).thenReturn(authentication);
            when(authentication.getPrincipal()).thenReturn(mockUserDetails);
            when(reactiveWorkspaceRepository.findById(1L)).thenReturn(Mono.just(mockWorkspace));
            when(reactivePageRepository.save(any(Page.class))).thenReturn(Mono.just(savedPageWithUntitled));

            // when & then
            StepVerifier.create(pageService.createPage(1L, requestWithoutTitle))
                    .expectNextMatches(response -> response.getPageId().equals(3L))
                    .verifyComplete();

            verify(reactivePageRepository).save(argThat(page -> "Untitled".equals(page.getTitle())));
        }
    }

    @Test
    @DisplayName("예상치 못한 예외 발생 시 PageException으로 변환")
    void createPage_WhenUnexpectedError_ShouldMapToPageException() {
        // given
        try (MockedStatic<ReactiveSecurityContextHolder> mockedHolder = mockStatic(ReactiveSecurityContextHolder.class)) {
            SecurityContext securityContext = mock(SecurityContext.class);
            Authentication authentication = mock(Authentication.class);

            mockedHolder.when(ReactiveSecurityContextHolder::getContext).thenReturn(Mono.just(securityContext));
            when(securityContext.getAuthentication()).thenReturn(authentication);
            when(authentication.getPrincipal()).thenReturn(mockUserDetails);
            when(reactiveWorkspaceRepository.findById(1L)).thenReturn(Mono.error(new RuntimeException("Unexpected error")));

            // when & then
            StepVerifier.create(pageService.createPage(1L, requestDto))
                    .expectError(PageException.class)
                    .verify();
        }
    }

    @Test
    @DisplayName("페이지 저장 중 예외 발생 시 예외 전파")
    void createPage_WhenSaveError_ShouldPropagateException() {
        // given
        PageCreateRequestDto requestWithoutParent = PageCreateRequestDto.builder()
                .title("Test Page")
                .build();

        try (MockedStatic<ReactiveSecurityContextHolder> mockedHolder = mockStatic(ReactiveSecurityContextHolder.class)) {
            SecurityContext securityContext = mock(SecurityContext.class);
            Authentication authentication = mock(Authentication.class);

            mockedHolder.when(ReactiveSecurityContextHolder::getContext).thenReturn(Mono.just(securityContext));
            when(securityContext.getAuthentication()).thenReturn(authentication);
            when(authentication.getPrincipal()).thenReturn(mockUserDetails);
            when(reactiveWorkspaceRepository.findById(1L)).thenReturn(Mono.just(mockWorkspace));
            when(reactivePageRepository.save(any(Page.class))).thenReturn(Mono.error(new RuntimeException("Save failed")));

            // when & then
            StepVerifier.create(pageService.createPage(1L, requestWithoutParent))
                    .expectError(PageException.class)
                    .verify();
        }
    }

    @Test
    @DisplayName("페이지 목록 조회 성공 - 워크스페이스 소유자")
    void getPages_WhenWorkspaceOwner_ShouldReturnPageList() {
        // given
        Page rootPage1 = Page.builder().id(10L).title("Root Page 1").icon("📄").build();
        Page rootPage2 = Page.builder().id(11L).title("Root Page 2").icon("📚").build();

        when(reactiveWorkspaceRepository.findById(1L)).thenReturn(Mono.just(mockWorkspace));
        when(reactivePageRepository.findAllByWorkspaceIdAndParentPageIdIsNull(1L)).thenReturn(Flux.just(rootPage1, rootPage2));

        // when
        Mono<PageListResponseDto> responseMono = pageService.getPages(1L)
                .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(mockSecurityContext)));

        // then
        StepVerifier.create(responseMono)
                .expectNextMatches(response -> {
                    List<PageListItemResponseDto> pages = response.getPages();
                    return pages.size() == 2 &&
                            pages.get(0).getPageId().equals(10L) &&
                            pages.get(0).getTitle().equals("Root Page 1") &&
                            pages.get(1).getPageId().equals(11L) &&
                            pages.get(1).getTitle().equals("Root Page 2");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("페이지 목록 조회 성공 - 워크스페이스 멤버")
    void getPages_WhenWorkspaceMember_ShouldReturnPageList() {
        // given
        Workspace workspaceOwnedByOthers = Workspace.builder().id(1L).createdBy(2L).build();
        Page rootPage1 = Page.builder().id(10L).title("Root Page 1").build();

        when(reactiveWorkspaceRepository.findById(1L)).thenReturn(Mono.just(workspaceOwnedByOthers));
        when(reactiveWorkspaceMemberRepository.existsByWorkspaceIdAndUserId(1L, 1L)).thenReturn(Mono.just(true));
        when(reactivePageRepository.findAllByWorkspaceIdAndParentPageIdIsNull(1L)).thenReturn(Flux.just(rootPage1));

        // when
        Mono<PageListResponseDto> responseMono = pageService.getPages(1L)
                .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(mockSecurityContext)));

        // then
        StepVerifier.create(responseMono)
                .expectNextMatches(response -> response.getPages().size() == 1)
                .verifyComplete();
    }

    @Test
    @DisplayName("페이지 목록 조회 성공 - 결과가 없는 경우 빈 리스트 반환")
    void getPages_WhenNoPagesExist_ShouldReturnEmptyList() {
        // given
        when(reactiveWorkspaceRepository.findById(1L)).thenReturn(Mono.just(mockWorkspace));
        when(reactivePageRepository.findAllByWorkspaceIdAndParentPageIdIsNull(1L)).thenReturn(Flux.empty());

        // when
        Mono<PageListResponseDto> responseMono = pageService.getPages(1L)
                .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(mockSecurityContext)));

        // then
        StepVerifier.create(responseMono)
                .expectNextMatches(response -> response.getPages().isEmpty())
                .verifyComplete();
    }

    @Test
    @DisplayName("페이지 목록 조회 실패 - 인증되지 않은 사용자")
    void getPages_WhenNotAuthenticated_ShouldThrowException() {
        // given
        try (MockedStatic<ReactiveSecurityContextHolder> mockedHolder = mockStatic(ReactiveSecurityContextHolder.class)) {
            mockedHolder.when(ReactiveSecurityContextHolder::getContext).thenReturn(Mono.empty());

            // when & then
            StepVerifier.create(pageService.getPages(1L))
                    .expectError(WorkspaceException.class)
                    .verify();
        }
    }

    @Test
    @DisplayName("페이지 목록 조회 실패 - 존재하지 않는 워크스페이스")
    void getPages_WhenWorkspaceNotFound_ShouldThrowException() {
        // given
        when(reactiveWorkspaceRepository.findById(1L)).thenReturn(Mono.empty());

        // when
        Mono<PageListResponseDto> responseMono = pageService.getPages(1L)
                .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(mockSecurityContext)));

        // then
        StepVerifier.create(responseMono)
                .expectErrorMatches(throwable ->
                        throwable instanceof WorkspaceException &&
                                ((WorkspaceException) throwable).getErrorCode() == ErrorCode.WORKSPACE_NOT_FOUND)
                .verify();
    }

    @Test
    @DisplayName("페이지 목록 조회 실패 - 워크스페이스 멤버 아님")
    void getPages_WhenNotAWorkspaceMember_ShouldThrowException() {
        // given
        Workspace workspaceOwnedByOthers = Workspace.builder().id(1L).createdBy(2L).build();
        when(reactiveWorkspaceRepository.findById(1L)).thenReturn(Mono.just(workspaceOwnedByOthers));
        when(reactiveWorkspaceMemberRepository.existsByWorkspaceIdAndUserId(1L, 1L)).thenReturn(Mono.just(false));

        // when
        Mono<PageListResponseDto> responseMono = pageService.getPages(1L)
                .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(mockSecurityContext)));

        // then
        StepVerifier.create(responseMono)
                .expectErrorMatches(throwable ->
                        throwable instanceof WorkspaceException &&
                                ((WorkspaceException) throwable).getErrorCode() == ErrorCode.MEMBER_NOT_FOUND)
                .verify();
    }

    @Test
    @DisplayName("페이지 목록 조회 성공 - 워크스페이스 소유자")
    void getPage_WhenWorkspaceOwner_ShouldReturnAllPages() {
        // given
        Page publicRootPage = Page.builder().id(10L).title("Public Page").icon("📄").isPublic(true).parentPageId(null).build();
        Page privateRootPage = Page.builder().id(11L).title("Private Page").icon("🔒").isPublic(false).parentPageId(null).build();

        when(reactiveWorkspaceRepository.findById(1L)).thenReturn(Mono.just(mockWorkspace));
        when(reactivePageRepository.findAllByWorkspaceIdAndParentPageIdIsNull(1L)).thenReturn(Flux.just(publicRootPage, privateRootPage));

        // when
        Mono<PageListResponseDto> responseMono = pageService.getPages(1L)
                .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(mockSecurityContext)));

        // then
        StepVerifier.create(responseMono)
                .expectNextMatches(response -> {
                    List<PageListItemResponseDto> pages = response.getPages();
                    return pages.size() == 2 &&
                            pages.stream().anyMatch(p -> p.getPageId().equals(10L)) &&
                            pages.stream().anyMatch(p -> p.getPageId().equals(11L));
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("페이지 목록 조회 성공 - 워크스페이스 멤버")
    void getPage_WhenWorkspaceMember_ShouldReturnAllPages() {
        // given
        Workspace workspaceOwnedByOthers = Workspace.builder().id(1L).createdBy(2L).build();
        Page publicRootPage = Page.builder().id(10L).title("Public Page").icon("📄").isPublic(true).parentPageId(null).build();
        Page privateRootPage = Page.builder().id(11L).title("Private Page").icon("🔒").isPublic(false).parentPageId(null).build();

        when(reactiveWorkspaceRepository.findById(1L)).thenReturn(Mono.just(workspaceOwnedByOthers));
        when(reactiveWorkspaceMemberRepository.existsByWorkspaceIdAndUserId(1L, 1L)).thenReturn(Mono.just(true));
        when(reactivePageRepository.findAllByWorkspaceIdAndParentPageIdIsNull(1L)).thenReturn(Flux.just(publicRootPage, privateRootPage));

        // when
        Mono<PageListResponseDto> responseMono = pageService.getPages(1L)
                .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(mockSecurityContext)));

        // then
        StepVerifier.create(responseMono)
                .expectNextMatches(response -> {
                    List<PageListItemResponseDto> pages = response.getPages();
                    return pages.size() == 2 &&
                            pages.stream().anyMatch(p -> p.getPageId().equals(10L)) &&
                            pages.stream().anyMatch(p -> p.getPageId().equals(11L));
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("페이지 조회 성공 - 워크스페이스 멤버, 비공개 페이지에 권한 있음")
    void getPage_WhenWorkspaceMemberAndPrivatePageWithPermission_ShouldSucceed() {
        // given
        Workspace workspaceOwnedByOthers = Workspace.builder().id(1L).createdBy(2L).build();
        Page privatePage = Page.builder()
                .id(1L).workspaceId(1L).title("Private Page").isPublic(false).build();
        PagePermission readPermission = PagePermission.builder()
                .permission(PagePermissionType.READ.name()).build();

        when(reactiveWorkspaceRepository.findById(1L)).thenReturn(Mono.just(workspaceOwnedByOthers));
        when(reactiveWorkspaceMemberRepository.existsByWorkspaceIdAndUserId(1L, 1L)).thenReturn(Mono.just(true));
        when(reactivePageRepository.findByIdAndWorkspaceId(1L, 1L)).thenReturn(Mono.just(privatePage));
        when(reactivePagePermissionRepository.findByPageIdAndUserId(1L, 1L)).thenReturn(Mono.just(readPermission));

        // when
        Mono<PageResponseDto> result = pageService.getPage(1L, 1L)
                .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(mockSecurityContext)));

        // then
        StepVerifier.create(result)
                .expectNextMatches(dto -> dto.getId().equals(1L) && dto.getTitle().equals("Private Page"))
                .verifyComplete();
    }

    @Test
    @DisplayName("페이지 조회 실패 - 워크스페이스 멤버, 비공개 페이지에 권한 없음")
    void getPage_WhenWorkspaceMemberAndPrivatePageNoPermission_ShouldThrowException() {
        // given
        Workspace workspaceOwnedByOthers = Workspace.builder().id(1L).createdBy(2L).build();
        Page privatePage = Page.builder()
                .id(1L).workspaceId(1L).title("Private Page").isPublic(false).build();

        when(reactiveWorkspaceRepository.findById(1L)).thenReturn(Mono.just(workspaceOwnedByOthers));
        when(reactiveWorkspaceMemberRepository.existsByWorkspaceIdAndUserId(1L, 1L)).thenReturn(Mono.just(true));
        when(reactivePageRepository.findByIdAndWorkspaceId(1L, 1L)).thenReturn(Mono.just(privatePage));
        when(reactivePagePermissionRepository.findByPageIdAndUserId(1L, 1L)).thenReturn(Mono.empty());

        // when
        Mono<PageResponseDto> result = pageService.getPage(1L, 1L)
                .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(mockSecurityContext)));

        // then
        StepVerifier.create(result)
                .expectErrorMatches(throwable ->
                        throwable instanceof PageException &&
                                ((PageException) throwable).getErrorCode() == ErrorCode.PAGE_PERMISSION_DENIED)
                .verify();
    }

    @Test
    @DisplayName("페이지 조회 실패 - 비멤버, 비공개 페이지")
    void getPage_WhenNotMemberAndPrivatePage_ShouldThrowException() {
        // given
        Workspace workspaceOwnedByOthers = Workspace.builder().id(1L).createdBy(2L).build();
        Page privatePage = Page.builder()
                .id(1L).workspaceId(1L).title("Private Page").isPublic(false).build();

        when(reactiveWorkspaceRepository.findById(1L)).thenReturn(Mono.just(workspaceOwnedByOthers));
        when(reactiveWorkspaceMemberRepository.existsByWorkspaceIdAndUserId(1L, 1L)).thenReturn(Mono.just(false));
        when(reactivePageRepository.findByIdAndWorkspaceId(1L, 1L)).thenReturn(Mono.just(privatePage));

        // when
        Mono<PageResponseDto> result = pageService.getPage(1L, 1L)
                .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(mockSecurityContext)));

        // then
        StepVerifier.create(result)
                .expectErrorMatches(throwable ->
                        throwable instanceof PageException &&
                                ((PageException) throwable).getErrorCode() == ErrorCode.PAGE_PERMISSION_DENIED)
                .verify();
    }

    @Test
    @DisplayName("페이지 조회 성공 - 비멤버, 공개 페이지")
    void getPage_WhenNotMemberAndPublicPage_ShouldSucceed() {
        // given
        Workspace workspaceOwnedByOthers = Workspace.builder().id(1L).createdBy(2L).build();
        Page publicPage = Page.builder()
                .id(1L).workspaceId(1L).title("Public Page").isPublic(true).build();

        when(reactiveWorkspaceRepository.findById(1L)).thenReturn(Mono.just(workspaceOwnedByOthers));
        when(reactiveWorkspaceMemberRepository.existsByWorkspaceIdAndUserId(1L, 1L)).thenReturn(Mono.just(false));
        when(reactivePageRepository.findByIdAndWorkspaceId(1L, 1L)).thenReturn(Mono.just(publicPage));

        // when
        Mono<PageResponseDto> result = pageService.getPage(1L, 1L)
                .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(mockSecurityContext)));

        // then
        StepVerifier.create(result)
                .expectNextMatches(dto -> dto.getId().equals(1L) && dto.getTitle().equals("Public Page"))
                .verifyComplete();
    }

    @Test
    @DisplayName("페이지 조회 실패 - 워크스페이스 멤버 아님")
    void getPage_WhenNotAWorkspaceMember_ShouldThrowException() {
        // given
        Workspace workspaceOwnedByOthers = Workspace.builder().id(1L).createdBy(2L).build();

        when(reactiveWorkspaceRepository.findById(1L)).thenReturn(Mono.just(workspaceOwnedByOthers));
        when(reactiveWorkspaceMemberRepository.existsByWorkspaceIdAndUserId(1L, 1L)).thenReturn(Mono.just(false));

        // when
        Mono<PageListResponseDto> responseMono = pageService.getPages(1L)
                .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(mockSecurityContext)));

        // then
        StepVerifier.create(responseMono)
                .expectErrorMatches(throwable ->
                        throwable instanceof WorkspaceException &&
                                ((WorkspaceException) throwable).getErrorCode() == ErrorCode.MEMBER_NOT_FOUND)
                .verify();
    }

    @Test
    @DisplayName("페이지 조회 실패 - 존재하지 않는 페이지")
    void getPage_WhenPageNotFound_ShouldThrowException() {
        // given
        when(reactiveWorkspaceRepository.findById(1L)).thenReturn(Mono.just(mockWorkspace));
        when(reactivePageRepository.findByIdAndWorkspaceId(1L, 1L)).thenReturn(Mono.empty());

        // when
        Mono<PageResponseDto> result = pageService.getPage(1L, 1L)
                .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(mockSecurityContext)));

        // then
        StepVerifier.create(result)
                .expectErrorMatches(throwable ->
                        throwable instanceof PageException &&
                                ((PageException) throwable).getErrorCode() == ErrorCode.PAGE_NOT_FOUND)
                .verify();
    }

    @Test
    @DisplayName("페이지 수정 성공 - 워크스페이스 소유자")
    void updatePage_WhenWorkspaceOwner_ShouldSucceed() {
        // given
        PageUpdateRequestDto request = PageUpdateRequestDto.builder()
                .title("Updated Title")
                .icon("✏️")
                .cover("updated-cover.jpg")
                .build();

        when(reactiveWorkspaceRepository.findById(1L)).thenReturn(Mono.just(mockWorkspace));
        when(reactivePageRepository.findByIdAndWorkspaceId(10L, 1L)).thenReturn(Mono.just(mockPage));
        when(reactivePageRepository.save(any(Page.class))).thenReturn(Mono.just(mockPage));

        // when
        Mono<PageResponseDto> result = pageService.updatePage(1L, 10L, request)
                .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(mockSecurityContext)));

        // then
        StepVerifier.create(result)
                .expectNextMatches(dto -> "Updated Title".equals(dto.getTitle()) && "✏️".equals(dto.getIcon()) && "updated-cover.jpg".equals(dto.getCover()))
                .verifyComplete();

        // 소유자는 권한 확인 로직을 거치지 않으므로, permission repository가 호출되지 않아야 함
        verify(reactiveWorkspaceMemberRepository, never()).existsByWorkspaceIdAndUserId(any(), any());
        verify(reactivePagePermissionRepository, never()).findByPageIdAndUserId(any(), any());
    }

    @Test
    @DisplayName("페이지 수정 성공 - 워크스페이스 멤버, EDIT 권한 소유")
    void updatePage_WhenMemberWithEditPermission_ShouldSucceed() {
        // given
        Workspace workspaceOwnedByOthers = Workspace.builder().id(1L).createdBy(2L).build();
        PagePermission editPermission = PagePermission.builder()
                .pageId(10L)
                .userId(1L)
                .permission(PagePermissionType.EDIT.name())
                .build();
        PageUpdateRequestDto request = PageUpdateRequestDto.builder().title("Member Updated Title").build();

        when(reactiveWorkspaceRepository.findById(1L)).thenReturn(Mono.just(workspaceOwnedByOthers));
        when(reactivePageRepository.findByIdAndWorkspaceId(10L, 1L)).thenReturn(Mono.just(mockPage));
        when(reactiveWorkspaceMemberRepository.existsByWorkspaceIdAndUserId(1L, 1L)).thenReturn(Mono.just(true));
        when(reactivePagePermissionRepository.findByPageIdAndUserId(10L, 1L)).thenReturn(Mono.just(editPermission));
        when(reactivePageRepository.save(any(Page.class))).thenReturn(Mono.just(mockPage));

        // when
        Mono<PageResponseDto> result = pageService.updatePage(1L, 10L, request)
                .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(mockSecurityContext)));

        // then
        StepVerifier.create(result)
                .expectNextMatches(dto -> "Member Updated Title".equals(dto.getTitle()))
                .verifyComplete();
    }

    @Test
    @DisplayName("페이지 수정 성공 - 워크스페이스 멤버, FULL_ACCESS 권한 소유")
    void updatePage_WhenMemberWithFullAccessPermission_ShouldSucceed() {
        // given
        Workspace workspaceOwnedByOthers = Workspace.builder().id(1L).createdBy(2L).build();
        PagePermission fullAccessPermission = PagePermission.builder()
                .pageId(10L)
                .userId(1L)
                .permission(PagePermissionType.FULL_ACCESS.name())
                .build();
        PageUpdateRequestDto request = PageUpdateRequestDto.builder().title("Member Updated Title").build();

        when(reactiveWorkspaceRepository.findById(1L)).thenReturn(Mono.just(workspaceOwnedByOthers));
        when(reactivePageRepository.findByIdAndWorkspaceId(10L, 1L)).thenReturn(Mono.just(mockPage));
        when(reactiveWorkspaceMemberRepository.existsByWorkspaceIdAndUserId(1L, 1L)).thenReturn(Mono.just(true));
        when(reactivePagePermissionRepository.findByPageIdAndUserId(10L, 1L)).thenReturn(Mono.just(fullAccessPermission));
        when(reactivePageRepository.save(any(Page.class))).thenReturn(Mono.just(mockPage));

        // when
        Mono<PageResponseDto> result = pageService.updatePage(1L, 10L, request)
                .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(mockSecurityContext)));

        // then
        StepVerifier.create(result)
                .expectNextMatches(dto -> "Member Updated Title".equals(dto.getTitle()))
                .verifyComplete();
    }

    @Test
    @DisplayName("페이지 수정 실패 - 워크스페이스 멤버, READ 권한만 소유")
    void updatePage_WhenMemberWithReadPermission_ShouldThrowException() {
        // given
        Workspace workspaceOwnedByOthers = Workspace.builder().id(1L).createdBy(2L).build();
        PagePermission readPermission = PagePermission.builder()
                .pageId(10L)
                .userId(1L)
                .permission(PagePermissionType.READ.name())
                .build();
        PageUpdateRequestDto request = PageUpdateRequestDto.builder().title("Should Fail").build();

        when(reactiveWorkspaceRepository.findById(1L)).thenReturn(Mono.just(workspaceOwnedByOthers));
        when(reactivePageRepository.findByIdAndWorkspaceId(10L, 1L)).thenReturn(Mono.just(mockPage));
        when(reactiveWorkspaceMemberRepository.existsByWorkspaceIdAndUserId(1L, 1L)).thenReturn(Mono.just(true));
        when(reactivePagePermissionRepository.findByPageIdAndUserId(10L, 1L)).thenReturn(Mono.just(readPermission));

        // when
        Mono<PageResponseDto> result = pageService.updatePage(1L, 10L, request)
                .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(mockSecurityContext)));

        // then
        StepVerifier.create(result)
                .expectErrorMatches(throwable ->
                        throwable instanceof PageException &&
                                ((PageException) throwable).getErrorCode() == ErrorCode.PAGE_PERMISSION_DENIED)
                .verify();
    }

    @Test
    @DisplayName("페이지 수정 실패 - 워크스페이스 멤버, 권한 정보 없음")
    void updatePage_WhenMemberWithNoPermissionRecord_ShouldThrowException() {
        // given
        Workspace workspaceOwnedByOthers = Workspace.builder().id(1L).createdBy(2L).build();
        PageUpdateRequestDto request = PageUpdateRequestDto.builder().title("Should Fail").build();

        when(reactiveWorkspaceRepository.findById(1L)).thenReturn(Mono.just(workspaceOwnedByOthers));
        when(reactivePageRepository.findByIdAndWorkspaceId(10L, 1L)).thenReturn(Mono.just(mockPage));
        when(reactiveWorkspaceMemberRepository.existsByWorkspaceIdAndUserId(1L, 1L)).thenReturn(Mono.just(true));
        when(reactivePagePermissionRepository.findByPageIdAndUserId(10L, 1L)).thenReturn(Mono.empty());

        // when
        Mono<PageResponseDto> result = pageService.updatePage(1L, 10L, request)
                .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(mockSecurityContext)));

        // then
        StepVerifier.create(result)
                .expectErrorMatches(throwable ->
                        throwable instanceof PageException &&
                                ((PageException) throwable).getErrorCode() == ErrorCode.PAGE_PERMISSION_DENIED)
                .verify();
    }

    @Test
    @DisplayName("페이지 수정 실패 - 워크스페이스 멤버 아님")
    void updatePage_WhenNotAWorkspaceMember_ShouldThrowException() {
        // given
        Workspace workspaceOwnedByOthers = Workspace.builder().id(1L).createdBy(2L).build();
        PageUpdateRequestDto request = PageUpdateRequestDto.builder().title("Should Fail").build();

        when(reactiveWorkspaceRepository.findById(1L)).thenReturn(Mono.just(workspaceOwnedByOthers));
        when(reactivePageRepository.findByIdAndWorkspaceId(10L, 1L)).thenReturn(Mono.just(mockPage));
        when(reactiveWorkspaceMemberRepository.existsByWorkspaceIdAndUserId(1L, 1L)).thenReturn(Mono.just(false));

        // when
        Mono<PageResponseDto> result = pageService.updatePage(1L, 10L, request)
                .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(mockSecurityContext)));

        // then
        StepVerifier.create(result)
                .expectErrorMatches(throwable ->
                        throwable instanceof PageException &&
                                ((PageException) throwable).getErrorCode() == ErrorCode.PAGE_PERMISSION_DENIED)
                .verify();
    }

    @Test
    @DisplayName("페이지 수정 실패 - 존재하지 않는 워크스페이스")
    void updatePage_WhenWorkspaceNotFound_ShouldThrowException() {
        // given
        PageUpdateRequestDto request = PageUpdateRequestDto.builder().title("Fail").build();
        when(reactiveWorkspaceRepository.findById(1L)).thenReturn(Mono.empty());

        // when
        Mono<PageResponseDto> result = pageService.updatePage(1L, 10L, request)
                .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(mockSecurityContext)));

        // then
        StepVerifier.create(result)
                .expectErrorMatches(throwable ->
                        throwable instanceof WorkspaceException &&
                                ((WorkspaceException) throwable).getErrorCode() == ErrorCode.WORKSPACE_NOT_FOUND)
                .verify();
    }

    @Test
    @DisplayName("페이지 수정 실패 - 존재하지 않는 페이지")
    void updatePage_WhenPageNotFound_ShouldThrowException() {
        // given
        PageUpdateRequestDto request = PageUpdateRequestDto.builder().title("Fail").build();
        when(reactiveWorkspaceRepository.findById(1L)).thenReturn(Mono.just(mockWorkspace));
        when(reactivePageRepository.findByIdAndWorkspaceId(10L, 1L)).thenReturn(Mono.empty());

        // when
        Mono<PageResponseDto> result = pageService.updatePage(1L, 10L, request)
                .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(mockSecurityContext)));

        // then
        StepVerifier.create(result)
                .expectErrorMatches(throwable ->
                        throwable instanceof PageException &&
                                ((PageException) throwable).getErrorCode() == ErrorCode.PAGE_NOT_FOUND)
                .verify();
    }

    @Test
    @DisplayName("페이지에 멤버 초대 성공 - 워크스페이스 소유자")
    void inviteMemberToPage_WhenWorkspaceOwner_ShouldSucceed() {
        // given
        PageInviteRequestDto request = PageInviteRequestDto.builder()
                .userId(2L)
                .permissionType(PagePermissionType.READ.name())
                .build();

        when(reactiveWorkspaceRepository.findById(1L)).thenReturn(Mono.just(mockWorkspace));
        when(reactivePageRepository.findByIdAndWorkspaceId(10L, 1L)).thenReturn(Mono.just(mockPage));
        when(reactiveWorkspaceMemberRepository.existsByWorkspaceIdAndUserId(1L, 2L)).thenReturn(Mono.just(true));
        when(reactivePagePermissionRepository.findByPageIdAndUserId(10L, 2L)).thenReturn(Mono.empty());
        when(reactivePagePermissionRepository.save(any(PagePermission.class))).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        // when
        Mono<PageInviteResponseDto> result = pageService.inviteMemberToPage(1L, 10L, request)
                .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(mockSecurityContext)));

        // then
        StepVerifier.create(result)
                .expectNextMatches(dto -> dto.getUserId().equals(2L) && dto.getPageId().equals(10L) && "READ".equals(dto.getPermission()))
                .verifyComplete();

        verify(reactivePagePermissionRepository, times(1)).save(any(PagePermission.class));
    }

    @Test
    @DisplayName("페이지에 멤버 초대 성공 - FULL_ACCESS 권한 소유 멤버")
    void inviteMemberToPage_WhenFullAccessMember_ShouldSucceed() {
        // given
        PageInviteRequestDto request = PageInviteRequestDto.builder()
                .userId(2L)
                .permissionType(PagePermissionType.EDIT.name())
                .build();
        PagePermission fullAccessPermission = PagePermission.builder()
                .pageId(10L)
                .userId(1L)
                .permission(PagePermissionType.FULL_ACCESS.name())
                .build();

        Workspace workspaceOwnedByOthers = Workspace.builder().id(1L).createdBy(20L).build();

        when(reactiveWorkspaceRepository.findById(1L)).thenReturn(Mono.just(workspaceOwnedByOthers));
        when(reactivePageRepository.findByIdAndWorkspaceId(10L, 1L)).thenReturn(Mono.just(mockPage));
        when(reactivePagePermissionRepository.findByPageIdAndUserId(10L, 1L)).thenReturn(Mono.just(fullAccessPermission));
        when(reactiveWorkspaceMemberRepository.existsByWorkspaceIdAndUserId(1L, 2L)).thenReturn(Mono.just(true));
        when(reactivePagePermissionRepository.findByPageIdAndUserId(10L, 2L)).thenReturn(Mono.empty());
        when(reactivePagePermissionRepository.save(any(PagePermission.class))).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        // when
        Mono<PageInviteResponseDto> result = pageService.inviteMemberToPage(1L, 10L, request)
                .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(mockSecurityContext)));

        // then
        StepVerifier.create(result)
                .expectNextMatches(dto -> dto.getUserId().equals(2L) && "EDIT".equals(dto.getPermission()))
                .verifyComplete();
    }

    @Test
    @DisplayName("페이지에 멤버 초대 실패 - EDIT 권한 소유 멤버")
    void inviteMemberToPage_WhenEditPermissionMember_ShouldThrowException() {
        // given
        PageInviteRequestDto request = PageInviteRequestDto.builder()
                .userId(2L)
                .permissionType(PagePermissionType.READ.name())
                .build();
        PagePermission editPermission = PagePermission.builder()
                .pageId(10L)
                .userId(1L)
                .permission(PagePermissionType.EDIT.name())
                .build();

        Workspace workspaceOwnedByOthers = Workspace.builder().id(1L).createdBy(20L).build();

        when(reactiveWorkspaceRepository.findById(1L)).thenReturn(Mono.just(workspaceOwnedByOthers));
        when(reactivePageRepository.findByIdAndWorkspaceId(10L, 1L)).thenReturn(Mono.just(mockPage));
        when(reactivePagePermissionRepository.findByPageIdAndUserId(10L, 1L)).thenReturn(Mono.just(editPermission));

        // when
        Mono<PageInviteResponseDto> result = pageService.inviteMemberToPage(1L, 10L, request)
                .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(mockSecurityContext)));

        // then
        StepVerifier.create(result)
                .expectErrorMatches(throwable ->
                        throwable instanceof PageException &&
                                ((PageException) throwable).getErrorCode() == ErrorCode.PAGE_PERMISSION_DENIED)
                .verify();

        verify(reactiveWorkspaceMemberRepository, never()).existsByWorkspaceIdAndUserId(any(), any());
        verify(reactivePagePermissionRepository, never()).save(any());
    }

    @Test
    @DisplayName("페이지에 멤버 초대 실패 - 초대할 유저가 워크스페이스 멤버가 아님")
    void inviteMemberToPage_WhenInvitedUserIsNotWorkspaceMember_ShouldThrowException() {
        // given
        PageInviteRequestDto request = PageInviteRequestDto.builder()
                .userId(2L)
                .permissionType(PagePermissionType.READ.name())
                .build();

        when(reactiveWorkspaceRepository.findById(1L)).thenReturn(Mono.just(mockWorkspace));
        when(reactivePageRepository.findByIdAndWorkspaceId(10L, 1L)).thenReturn(Mono.just(mockPage));
        when(reactiveWorkspaceMemberRepository.existsByWorkspaceIdAndUserId(1L, 2L)).thenReturn(Mono.just(false));

        // when
        Mono<PageInviteResponseDto> result = pageService.inviteMemberToPage(1L, 10L, request)
                .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(mockSecurityContext)));

        // then
        StepVerifier.create(result)
                .expectErrorMatches(throwable ->
                        throwable instanceof WorkspaceMemberException &&
                                ((WorkspaceMemberException) throwable).getErrorCode() == ErrorCode.INVITED_USER_NOT_WORKSPACE_MEMBER)
                .verify();
    }

    @Test
    @DisplayName("페이지에 멤버 초대 성공 - 이미 권한이 있는 멤버의 권한 업데이트")
    void inviteMemberToPage_WhenUserAlreadyHasPermission_ShouldUpdatePermission() {
        // given
        PageInviteRequestDto request = PageInviteRequestDto.builder()
                .userId(2L)
                .permissionType(PagePermissionType.FULL_ACCESS.name())
                .build();

        when(mockUserDetails.getId()).thenReturn(1L);

        PagePermission existingPermission = PagePermission.builder()
                .id(1L)
                .pageId(10L)
                .userId(2L)
                .permission(PagePermissionType.READ.name())
                .build();

        when(reactiveWorkspaceRepository.findById(1L)).thenReturn(Mono.just(mockWorkspace));
        when(reactivePageRepository.findByIdAndWorkspaceId(10L, 1L)).thenReturn(Mono.just(mockPage));
        when(reactiveWorkspaceMemberRepository.existsByWorkspaceIdAndUserId(1L, 2L)).thenReturn(Mono.just(true));
        when(reactivePagePermissionRepository.findByPageIdAndUserId(10L, 2L)).thenReturn(Mono.just(existingPermission));
        when(reactivePagePermissionRepository.save(any(PagePermission.class))).thenAnswer(invocation -> {
            PagePermission savedPermission = invocation.getArgument(0);
            savedPermission.setPermission(request.getPermissionType());
            return Mono.just(savedPermission);
        });

        // when
        Mono<PageInviteResponseDto> result = pageService.inviteMemberToPage(1L, 10L, request)
                .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(mockSecurityContext)));

        // then
        StepVerifier.create(result)
                .expectNextMatches(dto ->
                        dto.getUserId().equals(2L) &&
                                dto.getPermission().equals(PagePermissionType.FULL_ACCESS.name()))
                .verifyComplete();

        verify(reactivePagePermissionRepository, times(1)).save(any(PagePermission.class));
    }

    @Test
    @DisplayName("페이지 멤버 권한 수정 성공 - 워크스페이스 소유자")
    void updateMemberPermission_WhenWorkspaceOwner_ShouldSucceed() {
        // given
        PageUpdatePermissionRequestDto request = PageUpdatePermissionRequestDto.builder()
                .permissionType(PagePermissionType.FULL_ACCESS.name())
                .build();

        PagePermission existingPermissionForTargetUser = PagePermission.builder()
                .id(100L)
                .pageId(10L)
                .userId(2L)
                .permission(PagePermissionType.READ.name())
                .build();

        when(reactiveWorkspaceRepository.findById(1L)).thenReturn(Mono.just(mockWorkspace));
        when(reactivePageRepository.findByIdAndWorkspaceId(10L, 1L)).thenReturn(Mono.just(mockPage));
        when(reactiveWorkspaceMemberRepository.existsByWorkspaceIdAndUserId(1L, 2L)).thenReturn(Mono.just(true));
        when(reactivePagePermissionRepository.findByPageIdAndUserId(10L, 2L)).thenReturn(Mono.just(existingPermissionForTargetUser));
        when(reactivePagePermissionRepository.save(any(PagePermission.class))).thenAnswer(invocation -> {
            PagePermission updatedPermission = invocation.getArgument(0);
            return Mono.just(updatedPermission);
        });

        // when
        Mono<PageUpdatePermissionResponseDto> result = pageService.updateMemberPagePermission(1L, 10L, 2L, request)
                .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(mockSecurityContext)));

        // then
        StepVerifier.create(result)
                .expectNextMatches(dto ->
                        dto.getUserId().equals(2L) &&
                                dto.getPageId().equals(10L) &&
                                dto.getPermission().equals(PagePermissionType.FULL_ACCESS.name()))
                .verifyComplete();

        verify(reactivePagePermissionRepository, never()).findByPageIdAndUserId(10L, 1L);
        verify(reactivePagePermissionRepository, times(1)).save(any(PagePermission.class));
    }

    @Test
    @DisplayName("페이지 멤버 권한 수정 성공 - FULL_ACCESS 권한 소유 멤버")
    void updateMemberPermission_WhenFullAccessMember_ShouldSucceed() {
        // given
        PageUpdatePermissionRequestDto request = PageUpdatePermissionRequestDto.builder()
                .permissionType(PagePermissionType.READ.name())
                .build();

        Workspace workspaceOwnedByOthers = Workspace.builder().id(1L).createdBy(99L).build(); // 소유자가 아닌 경우
        Page pageOwnedByClientUser = Page.builder().id(10L).workspaceId(1L).createdBy(1L).build();

        PagePermission clientUserPermission = PagePermission.builder()
                .pageId(10L)
                .userId(1L)
                .permission(PagePermissionType.FULL_ACCESS.name())
                .build();

        PagePermission existingPermissionForTargetUser = PagePermission.builder()
                .id(100L)
                .pageId(10L)
                .userId(2L)
                .permission(PagePermissionType.EDIT.name())
                .build();

        when(reactiveWorkspaceRepository.findById(1L)).thenReturn(Mono.just(workspaceOwnedByOthers));
        when(reactivePageRepository.findByIdAndWorkspaceId(10L, 1L)).thenReturn(Mono.just(pageOwnedByClientUser));
        when(reactivePagePermissionRepository.findByPageIdAndUserId(10L, 1L)).thenReturn(Mono.just(clientUserPermission));
        when(reactiveWorkspaceMemberRepository.existsByWorkspaceIdAndUserId(1L, 2L)).thenReturn(Mono.just(true));
        when(reactivePagePermissionRepository.findByPageIdAndUserId(10L, 2L)).thenReturn(Mono.just(existingPermissionForTargetUser));
        when(reactivePagePermissionRepository.save(any(PagePermission.class))).thenAnswer(invocation -> {
            PagePermission updatedPermission = invocation.getArgument(0);
            return Mono.just(updatedPermission);
        });

        // when
        Mono<PageUpdatePermissionResponseDto> result = pageService.updateMemberPagePermission(1L, 10L, 2L, request)
                .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(mockSecurityContext)));

        // then
        StepVerifier.create(result)
                .expectNextMatches(dto ->
                        dto.getUserId().equals(2L) &&
                                dto.getPermission().equals(PagePermissionType.READ.name()))
                .verifyComplete();
    }

    @Test
    @DisplayName("페이지 멤버 권한 수정 실패 - EDIT 권한 소유 멤버")
    void updateMemberPermission_WhenEditPermission_ShouldThrowException() {
        // given
        PageUpdatePermissionRequestDto request = PageUpdatePermissionRequestDto.builder()
                .permissionType(PagePermissionType.FULL_ACCESS.name())
                .build();

        Workspace workspaceOwnedByOthers = Workspace.builder().id(1L).createdBy(99L).build();

        PagePermission clientUserPermission = PagePermission.builder()
                .pageId(10L)
                .userId(1L)
                .permission(PagePermissionType.EDIT.name())
                .build();

        when(reactiveWorkspaceRepository.findById(1L)).thenReturn(Mono.just(workspaceOwnedByOthers));
        when(reactivePageRepository.findByIdAndWorkspaceId(10L, 1L)).thenReturn(Mono.just(mockPage));
        when(reactivePagePermissionRepository.findByPageIdAndUserId(10L, 1L)).thenReturn(Mono.just(clientUserPermission));

        // when
        Mono<PageUpdatePermissionResponseDto> result = pageService.updateMemberPagePermission(1L, 10L, 2L, request)
                .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(mockSecurityContext)));

        // then
        StepVerifier.create(result)
                .expectErrorMatches(throwable ->
                        throwable instanceof PageException &&
                                ((PageException) throwable).getErrorCode() == ErrorCode.PAGE_PERMISSION_DENIED)
                .verify();
    }

    @Test
    @DisplayName("페이지 멤버 권한 수정 실패 - 수정 대상이 페이지 소유자")
    void updateMemberPermission_WhenTargetIsPageOwner_ShouldThrowException() {
        // given
        PageUpdatePermissionRequestDto request = PageUpdatePermissionRequestDto.builder()
                .permissionType(PagePermissionType.READ.name())
                .build();

        Page pageOwnedByTargetUser = Page.builder()
                .id(10L)
                .workspaceId(1L)
                .title("Owner's Page")
                .createdBy(2L)
                .build();

        when(reactiveWorkspaceRepository.findById(1L)).thenReturn(Mono.just(mockWorkspace));
        when(reactivePageRepository.findByIdAndWorkspaceId(10L, 1L)).thenReturn(Mono.just(pageOwnedByTargetUser));
        when(reactiveWorkspaceMemberRepository.existsByWorkspaceIdAndUserId(1L, 2L)).thenReturn(Mono.just(true));

        // when
        Mono<PageUpdatePermissionResponseDto> result = pageService.updateMemberPagePermission(1L, 10L, 2L, request)
                .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(mockSecurityContext)));

        // then
        StepVerifier.create(result)
                .expectErrorMatches(throwable ->
                        throwable instanceof PageException &&
                                ((PageException) throwable).getErrorCode() == ErrorCode.CANNOT_CHANGE_OWNER_PERMISSION)
                .verify();
    }

    @Test
    @DisplayName("페이지 멤버 권한 수정 실패 - 수정 대상이 워크스페이스 멤버 아님")
    void updateMemberPermission_WhenTargetIsNotWorkspaceMember_ShouldThrowException() {
        // given
        PageUpdatePermissionRequestDto request = PageUpdatePermissionRequestDto.builder()
                .permissionType(PagePermissionType.READ.name())
                .build();

        when(reactiveWorkspaceRepository.findById(1L)).thenReturn(Mono.just(mockWorkspace));
        when(reactivePageRepository.findByIdAndWorkspaceId(10L, 1L)).thenReturn(Mono.just(mockPage));
        when(reactiveWorkspaceMemberRepository.existsByWorkspaceIdAndUserId(1L, 2L)).thenReturn(Mono.just(false));

        // when
        Mono<PageUpdatePermissionResponseDto> result = pageService.updateMemberPagePermission(1L, 10L, 2L, request)
                .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(mockSecurityContext)));

        // then
        StepVerifier.create(result)
                .expectErrorMatches(throwable ->
                        throwable instanceof WorkspaceMemberException &&
                                ((WorkspaceMemberException) throwable).getErrorCode() == ErrorCode.INVITED_USER_NOT_WORKSPACE_MEMBER)
                .verify();
    }

    @Test
    @DisplayName("페이지 멤버 권한 수정 실패 - 수정 대상의 권한 정보가 없음")
    void updateMemberPermission_WhenTargetHasNoPermission_ShouldThrowException() {
        // given
        PageUpdatePermissionRequestDto request = PageUpdatePermissionRequestDto.builder()
                .permissionType(PagePermissionType.FULL_ACCESS.name())
                .build();

        Workspace workspaceOwnedByOthers = Workspace.builder().id(1L).createdBy(99L).build();
        Page pageOwnedByClientUser = Page.builder().id(10L).workspaceId(1L).createdBy(1L).build();

        PagePermission clientUserPermission = PagePermission.builder()
                .pageId(10L)
                .userId(1L)
                .permission(PagePermissionType.FULL_ACCESS.name())
                .build();

        when(reactiveWorkspaceRepository.findById(1L)).thenReturn(Mono.just(workspaceOwnedByOthers));
        when(reactivePageRepository.findByIdAndWorkspaceId(10L, 1L)).thenReturn(Mono.just(pageOwnedByClientUser));
        when(reactivePagePermissionRepository.findByPageIdAndUserId(10L, 1L)).thenReturn(Mono.just(clientUserPermission));
        when(reactiveWorkspaceMemberRepository.existsByWorkspaceIdAndUserId(1L, 2L)).thenReturn(Mono.just(true));
        when(reactivePagePermissionRepository.findByPageIdAndUserId(10L, 2L)).thenReturn(Mono.empty());

        // when
        Mono<PageUpdatePermissionResponseDto> result = pageService.updateMemberPagePermission(1L, 10L, 2L, request)
                .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(mockSecurityContext)));

        // then
        StepVerifier.create(result)
                .expectErrorMatches(throwable ->
                        throwable instanceof PageException &&
                                ((PageException) throwable).getErrorCode() == ErrorCode.PAGE_PERMISSION_NOT_FOUND)
                .verify();
    }

    @Test
    @DisplayName("페이지 공개 상태 수정 성공 - 워크스페이스 소유자가 공개로 변경")
    void updatePagePublicStatus_WhenWorkspaceOwner_ShouldSucceed() {
        // given
        PagePublicStatusUpdateRequestDto request = PagePublicStatusUpdateRequestDto.builder()
                .isPublic(true)
                .build();

        when(reactiveWorkspaceRepository.findById(1L)).thenReturn(Mono.just(mockWorkspace));
        when(reactivePageRepository.findByIdAndWorkspaceId(10L, 1L)).thenReturn(Mono.just(mockPage));
        when(reactivePageRepository.save(any(Page.class))).thenAnswer(invocation -> {
            Page pageToSave = invocation.getArgument(0);
            return Mono.just(pageToSave);
        });

        // when
        Mono<PagePublicStatusUpdateResponseDto> result = pageService.updatePagePublicStatus(1L, 10L, request)
                .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(mockSecurityContext)));

        // then
        StepVerifier.create(result)
                .expectNextMatches(dto -> dto.getPageId().equals(10L) && dto.getIsPublic().equals(true))
                .verifyComplete();

        verify(reactivePageRepository, times(1)).save(any(Page.class));
    }

    @Test
    @DisplayName("페이지 공개 상태 수정 성공 - Full Access 멤버가 비공개로 변경")
    void updatePagePublicStatus_WhenFullAccessMember_ShouldSucceed() {
        // given
        PagePublicStatusUpdateRequestDto request = PagePublicStatusUpdateRequestDto.builder()
                .isPublic(false)
                .build();

        Workspace workspaceOwnedByOthers = Workspace.builder().id(1L).createdBy(99L).build();
        PagePermission fullAccessPermission = PagePermission.builder()
                .pageId(10L)
                .userId(1L)
                .permission(PagePermissionType.FULL_ACCESS.name())
                .build();

        when(reactiveWorkspaceRepository.findById(1L)).thenReturn(Mono.just(workspaceOwnedByOthers));
        when(reactivePageRepository.findByIdAndWorkspaceId(10L, 1L)).thenReturn(Mono.just(mockPage));
        when(reactivePagePermissionRepository.findByPageIdAndUserId(10L, 1L)).thenReturn(Mono.just(fullAccessPermission));
        when(reactivePageRepository.save(any(Page.class))).thenAnswer(invocation -> {
            Page pageToSave = invocation.getArgument(0);
            return Mono.just(pageToSave);
        });

        // when
        Mono<PagePublicStatusUpdateResponseDto> result = pageService.updatePagePublicStatus(1L, 10L, request)
                .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(mockSecurityContext)));

        // then
        StepVerifier.create(result)
                .expectNextMatches(dto -> dto.getPageId().equals(10L) && dto.getIsPublic().equals(false))
                .verifyComplete();

        verify(reactivePageRepository, times(1)).save(any(Page.class));
    }

    @Test
    @DisplayName("페이지 공개 상태 수정 실패 - Edit 권한 멤버가 변경 시도")
    void updatePagePublicStatus_WhenEditPermissionMember_ShouldThrowException() {
        // given
        PagePublicStatusUpdateRequestDto request = PagePublicStatusUpdateRequestDto.builder()
                .isPublic(true)
                .build();

        Workspace workspaceOwnedByOthers = Workspace.builder().id(1L).createdBy(99L).build();
        PagePermission editPermission = PagePermission.builder()
                .pageId(10L)
                .userId(1L)
                .permission(PagePermissionType.EDIT.name())
                .build();

        when(reactiveWorkspaceRepository.findById(1L)).thenReturn(Mono.just(workspaceOwnedByOthers));
        when(reactivePageRepository.findByIdAndWorkspaceId(10L, 1L)).thenReturn(Mono.just(mockPage));
        when(reactivePagePermissionRepository.findByPageIdAndUserId(10L, 1L)).thenReturn(Mono.just(editPermission));

        // when
        Mono<PagePublicStatusUpdateResponseDto> result = pageService.updatePagePublicStatus(1L, 10L, request)
                .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(mockSecurityContext)));

        // then
        StepVerifier.create(result)
                .expectErrorMatches(throwable ->
                        throwable instanceof PageException &&
                                ((PageException) throwable).getErrorCode() == ErrorCode.PAGE_PERMISSION_DENIED)
                .verify();

        verify(reactivePageRepository, never()).save(any());
    }

    @Test
    @DisplayName("페이지 공개 상태 수정 실패 - 페이지 권한이 없는 멤버가 변경 시도")
    void updatePagePublicStatus_WhenNoPermissionMember_ShouldThrowException() {
        // given
        PagePublicStatusUpdateRequestDto request = PagePublicStatusUpdateRequestDto.builder()
                .isPublic(true)
                .build();

        Workspace workspaceOwnedByOthers = Workspace.builder().id(1L).createdBy(99L).build();
        when(reactiveWorkspaceRepository.findById(1L)).thenReturn(Mono.just(workspaceOwnedByOthers));
        when(reactivePageRepository.findByIdAndWorkspaceId(10L, 1L)).thenReturn(Mono.just(mockPage));
        when(reactivePagePermissionRepository.findByPageIdAndUserId(10L, 1L)).thenReturn(Mono.empty()); // 권한 없음

        // when
        Mono<PagePublicStatusUpdateResponseDto> result = pageService.updatePagePublicStatus(1L, 10L, request)
                .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(mockSecurityContext)));

        // then
        StepVerifier.create(result)
                .expectErrorMatches(throwable ->
                        throwable instanceof PageException &&
                                ((PageException) throwable).getErrorCode() == ErrorCode.PAGE_PERMISSION_DENIED)
                .verify();

        verify(reactivePageRepository, never()).save(any());
    }

    @Test
    @DisplayName("페이지 공개 상태 수정 실패 - 워크스페이스 멤버 아님")
    void updatePagePublicStatus_WhenNotWorkspaceMember_ShouldThrowException() {
        // given
        PagePublicStatusUpdateRequestDto request = PagePublicStatusUpdateRequestDto.builder()
                .isPublic(true)
                .build();

        Workspace workspaceOwnedByOthers = Workspace.builder().id(1L).createdBy(99L).build();
        when(reactiveWorkspaceRepository.findById(1L)).thenReturn(Mono.just(workspaceOwnedByOthers));
        when(reactivePageRepository.findByIdAndWorkspaceId(10L, 1L)).thenReturn(Mono.just(mockPage));
        when(reactivePagePermissionRepository.findByPageIdAndUserId(10L, 1L)).thenReturn(Mono.empty());

        // when
        Mono<PagePublicStatusUpdateResponseDto> result = pageService.updatePagePublicStatus(1L, 10L, request)
                .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(mockSecurityContext)));

        // then
        StepVerifier.create(result)
                .expectErrorMatches(throwable ->
                        throwable instanceof PageException &&
                                ((PageException) throwable).getErrorCode() == ErrorCode.PAGE_PERMISSION_DENIED)
                .verify();

        verify(reactiveWorkspaceMemberRepository, never()).existsByWorkspaceIdAndUserId(any(), any());
        verify(reactivePageRepository, never()).save(any());
    }
}
