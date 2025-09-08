package com.example.share_note.service;

import com.example.share_note.domain.Block;
import com.example.share_note.domain.Page;
import com.example.share_note.domain.PagePermission;
import com.example.share_note.domain.Workspace;
import com.example.share_note.dto.CustomUserDetails;
import com.example.share_note.dto.block.*;
import com.example.share_note.enums.PagePermissionType;
import com.example.share_note.exception.BlockException;
import com.example.share_note.exception.ErrorCode;
import com.example.share_note.exception.PageException;
import com.example.share_note.exception.WorkspaceException;
import com.example.share_note.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BlockServiceTest {

    @Mock
    private ReactivePageRepository reactivePageRepository;

    @Mock
    private ReactiveWorkspaceRepository reactiveWorkspaceRepository;

    @Mock
    private ReactiveWorkspaceMemberRepository reactiveWorkspaceMemberRepository;

    @Mock
    private ReactivePagePermissionRepository reactivePagePermissionRepository;

    @Mock
    private ReactiveBlockRepository reactiveBlockRepository;

    @InjectMocks
    private BlockService blockService;

    private CustomUserDetails mockUserDetails;
    private Workspace mockWorkspace;
    private Page mockPage;
    private Block mockParentBlock;
    private Block mockCreatedBlock;
    private Block mockBlock;
    private PagePermission mockPagePermission;
    private PagePermission mockReadOnlyPermission;
    private SecurityContext mockSecurityContext;
    private Authentication mockAuthentication;
    private BlockCreateRequestDto createRequestDto;
    private BlockUpdateRequestDto updateRequestDto;

    @BeforeEach
    void setUp() {
        mockUserDetails = mock(CustomUserDetails.class);
        lenient().when(mockUserDetails.getId()).thenReturn(1L);

        mockAuthentication = new UsernamePasswordAuthenticationToken(mockUserDetails, null);
        mockSecurityContext = mock(SecurityContext.class);
        lenient().when(mockSecurityContext.getAuthentication()).thenReturn(mockAuthentication);

        mockWorkspace = Workspace.builder()
                .id(1L)
                .name("Test Workspace")
                .createdBy(1L)
                .build();

        mockPage = Page.builder()
                .id(2L)
                .workspaceId(1L)
                .title("Test Page")
                .isPublic(false)
                .createdBy(1L)
                .build();

        mockParentBlock = Block.builder()
                .id(3L)
                .pageId(2L)
                .type("text")
                .content("Parent Block")
                .createdBy(1L)
                .build();

        mockCreatedBlock = Block.builder()
                .id(4L)
                .pageId(2L)
                .parentBlockId(3L)
                .type("text")
                .content("New Block")
                .createdBy(1L)
                .build();

        mockBlock = Block.builder()
                .id(4L)
                .pageId(2L)
                .type("text")
                .content("Test Block Content")
                .build();

        mockPagePermission = PagePermission.builder()
                .pageId(2L)
                .userId(1L)
                .permission(PagePermissionType.EDIT.name())
                .build();

        mockReadOnlyPermission = PagePermission.builder()
                .pageId(2L)
                .userId(1L)
                .permission(PagePermissionType.READ.name())
                .build();

        createRequestDto = BlockCreateRequestDto.builder()
                .parentBlockId(3L)
                .type("text")
                .content("New Block Content")
                .position(0)
                .build();

        updateRequestDto = BlockUpdateRequestDto.builder()
                .content("Updated Block Content")
                .type("heading")
                .position(1)
                .build();
    }

        @Test
    @DisplayName("인증되지 않은 사용자가 블록 생성 시 예외 발생")
    void createBlock_WhenNotAuthenticated_ShouldThrowException() {
        // given
        try (var mockedHolder = mockStatic(ReactiveSecurityContextHolder.class)) {
            mockedHolder.when(ReactiveSecurityContextHolder::getContext).thenReturn(Mono.empty());

            // when & then
            StepVerifier.create(blockService.createBlock(1L, 2L, createRequestDto))
                    .expectError(WorkspaceException.class)
                    .verify();
        }
    }

    @Test
    @DisplayName("존재하지 않는 페이지에 블록 생성 시 예외 발생")
    void createBlock_WhenPageNotFound_ShouldThrowException() {
        // given
        try (var mockedHolder = mockStatic(ReactiveSecurityContextHolder.class)) {
            SecurityContext securityContext = mock(SecurityContext.class);
            Authentication authentication = mock(Authentication.class);

            mockedHolder.when(ReactiveSecurityContextHolder::getContext).thenReturn(Mono.just(securityContext));
            when(securityContext.getAuthentication()).thenReturn(authentication);
            when(authentication.getPrincipal()).thenReturn(mockUserDetails);
            when(reactivePageRepository.findByIdAndWorkspaceId(2L, 1L)).thenReturn(Mono.empty());

            // when & then
            StepVerifier.create(blockService.createBlock(1L, 2L, createRequestDto))
                    .expectError(PageException.class)
                    .verify();
        }
    }

    @Test
    @DisplayName("존재하지 않는 워크스페이스에 블록 생성 시 예외 발생")
    void createBlock_WhenWorkspaceNotFound_ShouldThrowException() {
        // given
        try (var mockedHolder = mockStatic(ReactiveSecurityContextHolder.class)) {
            SecurityContext securityContext = mock(SecurityContext.class);
            Authentication authentication = mock(Authentication.class);

            mockedHolder.when(ReactiveSecurityContextHolder::getContext).thenReturn(Mono.just(securityContext));
            when(securityContext.getAuthentication()).thenReturn(authentication);
            when(authentication.getPrincipal()).thenReturn(mockUserDetails);
            when(reactivePageRepository.findByIdAndWorkspaceId(2L, 1L)).thenReturn(Mono.just(mockPage));
            when(reactiveWorkspaceRepository.findById(1L)).thenReturn(Mono.empty());

            // when & then
            StepVerifier.create(blockService.createBlock(1L, 2L, createRequestDto))
                    .expectError(WorkspaceException.class)
                    .verify();
        }
    }

    @Test
    @DisplayName("워크스페이스 소유자가 블록 생성 성공")
    void createBlock_WhenWorkspaceOwner_ShouldCreateSuccessfully() {
        // given
        try (var mockedHolder = mockStatic(ReactiveSecurityContextHolder.class)) {
            SecurityContext securityContext = mock(SecurityContext.class);
            Authentication authentication = mock(Authentication.class);
            mockedHolder.when(ReactiveSecurityContextHolder::getContext).thenReturn(Mono.just(securityContext));
            when(securityContext.getAuthentication()).thenReturn(authentication);
            when(authentication.getPrincipal()).thenReturn(mockUserDetails);
            when(reactivePageRepository.findByIdAndWorkspaceId(2L, 1L)).thenReturn(Mono.just(mockPage));
            when(reactiveWorkspaceRepository.findById(1L)).thenReturn(Mono.just(mockWorkspace));
            when(reactiveBlockRepository.findByIdAndPageId(3L, 2L)).thenReturn(Mono.just(mockParentBlock));
            when(reactiveBlockRepository.save(any(Block.class))).thenReturn(Mono.just(mockCreatedBlock));

            // when & then
            StepVerifier.create(blockService.createBlock(1L, 2L, createRequestDto))
                    .expectNextMatches(response ->
                            response.getId().equals(4L) &&
                                    response.getPageId().equals(2L) &&
                                    response.getCreatedBy().equals(1L)
                    )
                    .verifyComplete();
            verify(reactiveBlockRepository).save(any(Block.class));
        }
    }

    @Test
    @DisplayName("페이지에 편집 권한이 있는 멤버가 블록 생성 성공")
    void createBlock_WhenMemberHasEditPermission_ShouldCreateSuccessfully() {
        // given
        Workspace workspaceOwnedByOthers = Workspace.builder().id(1L).createdBy(2L).build();
        Page pageOwnedByOthers = Page.builder().id(2L).workspaceId(1L).createdBy(2L).build();

        try (var mockedHolder = mockStatic(ReactiveSecurityContextHolder.class)) {
            SecurityContext securityContext = mock(SecurityContext.class);
            Authentication authentication = mock(Authentication.class);
            mockedHolder.when(ReactiveSecurityContextHolder::getContext).thenReturn(Mono.just(securityContext));
            when(securityContext.getAuthentication()).thenReturn(authentication);
            when(authentication.getPrincipal()).thenReturn(mockUserDetails);
            when(reactivePageRepository.findByIdAndWorkspaceId(2L, 1L)).thenReturn(Mono.just(pageOwnedByOthers));
            when(reactiveWorkspaceRepository.findById(1L)).thenReturn(Mono.just(workspaceOwnedByOthers));
            when(reactiveWorkspaceMemberRepository.existsByWorkspaceIdAndUserId(1L, 1L)).thenReturn(Mono.just(true));
            when(reactivePagePermissionRepository.findByPageIdAndUserId(2L, 1L)).thenReturn(Mono.just(mockPagePermission));
            when(reactiveBlockRepository.findByIdAndPageId(3L, 2L)).thenReturn(Mono.just(mockParentBlock));
            when(reactiveBlockRepository.save(any(Block.class))).thenReturn(Mono.just(mockCreatedBlock));

            // when & then
            StepVerifier.create(blockService.createBlock(1L, 2L, createRequestDto))
                    .expectNextMatches(response -> response.getId().equals(4L))
                    .verifyComplete();
        }
    }

    @Test
    @DisplayName("워크스페이스 멤버가 아닌 경우 예외 발생")
    void createBlock_WhenNotWorkspaceMember_ShouldThrowException() {
        // given
        Workspace workspaceOwnedByOthers = Workspace.builder().id(1L).createdBy(2L).build();

        try (var mockedHolder = mockStatic(ReactiveSecurityContextHolder.class)) {
            SecurityContext securityContext = mock(SecurityContext.class);
            Authentication authentication = mock(Authentication.class);
            mockedHolder.when(ReactiveSecurityContextHolder::getContext).thenReturn(Mono.just(securityContext));
            when(securityContext.getAuthentication()).thenReturn(authentication);
            when(authentication.getPrincipal()).thenReturn(mockUserDetails);
            when(reactivePageRepository.findByIdAndWorkspaceId(2L, 1L)).thenReturn(Mono.just(mockPage));
            when(reactiveWorkspaceRepository.findById(1L)).thenReturn(Mono.just(workspaceOwnedByOthers));
            when(reactiveWorkspaceMemberRepository.existsByWorkspaceIdAndUserId(1L, 1L)).thenReturn(Mono.just(false));

            // when & then
            StepVerifier.create(blockService.createBlock(1L, 2L, createRequestDto))
                    .expectError(WorkspaceException.class)
                    .verify();
        }
    }

    @Test
    @DisplayName("부모 블록이 존재하지 않는 경우 예외 발생")
    void createBlock_WhenParentBlockNotFound_ShouldThrowException() {
        // given
        try (var mockedHolder = mockStatic(ReactiveSecurityContextHolder.class)) {
            SecurityContext securityContext = mock(SecurityContext.class);
            Authentication authentication = mock(Authentication.class);
            mockedHolder.when(ReactiveSecurityContextHolder::getContext).thenReturn(Mono.just(securityContext));
            when(securityContext.getAuthentication()).thenReturn(authentication);
            when(authentication.getPrincipal()).thenReturn(mockUserDetails);
            when(reactivePageRepository.findByIdAndWorkspaceId(2L, 1L)).thenReturn(Mono.just(mockPage));
            when(reactiveWorkspaceRepository.findById(1L)).thenReturn(Mono.just(mockWorkspace));
            when(reactiveBlockRepository.findByIdAndPageId(3L, 2L)).thenReturn(Mono.empty());

            // when & then
            StepVerifier.create(blockService.createBlock(1L, 2L, createRequestDto))
                    .expectError(BlockException.class)
                    .verify();
        }
    }

    @Test
    @DisplayName("페이지에 편집 권한이 없는 경우 예외 발생")
    void createBlock_WhenInsufficientPagePermission_ShouldThrowException() {
        // given
        Workspace workspaceOwnedByOthers = Workspace.builder().id(1L).createdBy(2L).build();
        Page pageOwnedByOthers = Page.builder().id(2L).workspaceId(1L).createdBy(2L).build();

        PagePermission readOnlyPermission = PagePermission.builder()
                .pageId(2L)
                .userId(1L)
                .permission(PagePermissionType.READ.name())
                .build();

        try (var mockedHolder = mockStatic(ReactiveSecurityContextHolder.class)) {
            SecurityContext securityContext = mock(SecurityContext.class);
            Authentication authentication = mock(Authentication.class);
            mockedHolder.when(ReactiveSecurityContextHolder::getContext).thenReturn(Mono.just(securityContext));
            when(securityContext.getAuthentication()).thenReturn(authentication);
            when(authentication.getPrincipal()).thenReturn(mockUserDetails);
            when(reactivePageRepository.findByIdAndWorkspaceId(2L, 1L)).thenReturn(Mono.just(pageOwnedByOthers));
            when(reactiveWorkspaceRepository.findById(1L)).thenReturn(Mono.just(workspaceOwnedByOthers));
            when(reactiveWorkspaceMemberRepository.existsByWorkspaceIdAndUserId(1L, 1L)).thenReturn(Mono.just(true));
            when(reactivePagePermissionRepository.findByPageIdAndUserId(2L, 1L)).thenReturn(Mono.just(readOnlyPermission));

            // when & then
            StepVerifier.create(blockService.createBlock(1L, 2L, createRequestDto))
                    .expectError(PageException.class)
                    .verify();
        }
    }

    @Test
    @DisplayName("예상치 못한 예외 발생 시 BlockException으로 변환")
    void createBlock_WhenUnexpectedError_ShouldMapToBlockException() {
        // given
        try (var mockedHolder = mockStatic(ReactiveSecurityContextHolder.class)) {
            SecurityContext securityContext = mock(SecurityContext.class);
            Authentication authentication = mock(Authentication.class);
            mockedHolder.when(ReactiveSecurityContextHolder::getContext).thenReturn(Mono.just(securityContext));
            when(securityContext.getAuthentication()).thenReturn(authentication);
            when(authentication.getPrincipal()).thenReturn(mockUserDetails);
            when(reactivePageRepository.findByIdAndWorkspaceId(2L, 1L)).thenReturn(Mono.error(new RuntimeException("Unexpected error")));

            // when & then
            StepVerifier.create(blockService.createBlock(1L, 2L, createRequestDto))
                    .expectError(BlockException.class)
                    .verify();
        }
    }

    @Test
    @DisplayName("블록 목록 조회 성공 - 워크스페이스 소유자")
    void getBlocks_WhenWorkspaceOwner_ShouldReturnBlockList() {
        // given
        Block block1 = Block.builder().id(10L).pageId(2L).type("text").position(0).build();
        Block block2 = Block.builder().id(11L).pageId(2L).type("heading1").position(1).build();

        when(reactivePageRepository.findByIdAndWorkspaceId(2L, 1L)).thenReturn(Mono.just(mockPage));
        when(reactiveWorkspaceRepository.findById(1L)).thenReturn(Mono.just(mockWorkspace));
        when(reactiveBlockRepository.findAllByPageIdAndIsArchivedFalseOrderByPositionAsc(2L)).thenReturn(Flux.just(block1, block2));

        // when
        Mono<BlockListResponseDto> responseMono = blockService.getBlocks(1L, 2L)
                .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(mockSecurityContext)));

        // then
        StepVerifier.create(responseMono)
                .expectNextMatches(response -> {
                    List<BlockListItemResponseDto> blocks = response.getBlocks();
                    return blocks.size() == 2 &&
                            blocks.get(0).getBlockId().equals(10L) &&
                            blocks.get(0).getType().equals("text") &&
                            blocks.get(1).getBlockId().equals(11L) &&
                            blocks.get(1).getType().equals("heading1");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("블록 목록 조회 성공 - 페이지 읽기 권한이 있는 멤버")
    void getBlocks_WhenMemberHasReadPermission_ShouldReturnBlockList() {
        // given
        Workspace workspaceOwnedByOthers = Workspace.builder().id(1L).createdBy(2L).build();
        Page pageOwnedByOthers = Page.builder().id(2L).workspaceId(1L).createdBy(2L).isPublic(false).build();
        Block block1 = Block.builder().id(10L).pageId(2L).type("text").position(0).build();

        when(reactivePageRepository.findByIdAndWorkspaceId(2L, 1L)).thenReturn(Mono.just(pageOwnedByOthers));
        when(reactiveWorkspaceRepository.findById(1L)).thenReturn(Mono.just(workspaceOwnedByOthers));
        when(reactiveWorkspaceMemberRepository.existsByWorkspaceIdAndUserId(1L, 1L)).thenReturn(Mono.just(true));
        when(reactivePagePermissionRepository.findByPageIdAndUserId(2L, 1L)).thenReturn(Mono.just(mockPagePermission));
        when(reactiveBlockRepository.findAllByPageIdAndIsArchivedFalseOrderByPositionAsc(2L)).thenReturn(Flux.just(block1));

        // when
        Mono<BlockListResponseDto> responseMono = blockService.getBlocks(1L, 2L)
                .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(mockSecurityContext)));

        // then
        StepVerifier.create(responseMono)
                .expectNextMatches(response -> response.getBlocks().size() == 1)
                .verifyComplete();
    }

    @Test
    @DisplayName("블록 목록 조회 성공 - 워크스페이스 멤버가 아니지만 공개 페이지")
    void getBlocks_WhenNotMemberButPageIsPublic_ShouldReturnBlockList() {
        // given
        Workspace workspaceOwnedByOthers = Workspace.builder().id(1L).createdBy(2L).build();
        Page publicPage = Page.builder().id(3L).workspaceId(1L).createdBy(2L).isPublic(true).build();
        Block block1 = Block.builder().id(10L).pageId(3L).type("text").position(0).build();

        when(reactivePageRepository.findByIdAndWorkspaceId(3L, 1L)).thenReturn(Mono.just(publicPage));
        when(reactiveWorkspaceRepository.findById(1L)).thenReturn(Mono.just(workspaceOwnedByOthers));
        when(reactiveWorkspaceMemberRepository.existsByWorkspaceIdAndUserId(1L, 1L)).thenReturn(Mono.just(false));
        when(reactiveBlockRepository.findAllByPageIdAndIsArchivedFalseOrderByPositionAsc(3L)).thenReturn(Flux.just(block1));

        // when
        Mono<BlockListResponseDto> responseMono = blockService.getBlocks(1L, 3L)
                .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(mockSecurityContext)));

        // then
        StepVerifier.create(responseMono)
                .expectNextMatches(response -> response.getBlocks().size() == 1)
                .verifyComplete();
    }

    @Test
    @DisplayName("블록 목록 조회 실패 - 인증되지 않은 사용자")
    void getBlocks_WhenNotAuthenticated_ShouldThrowException() {
        // given
        try (var mockedHolder = mockStatic(ReactiveSecurityContextHolder.class)) {
            mockedHolder.when(ReactiveSecurityContextHolder::getContext).thenReturn(Mono.empty());

            // when & then
            StepVerifier.create(blockService.getBlocks(1L, 2L))
                    .expectError(WorkspaceException.class)
                    .verify();
        }
    }

    @Test
    @DisplayName("블록 목록 조회 실패 - 존재하지 않는 워크스페이스")
    void getBlocks_WhenWorkspaceNotFound_ShouldThrowException() {
        // given
        when(reactivePageRepository.findByIdAndWorkspaceId(2L, 1L)).thenReturn(Mono.just(mockPage));
        when(reactiveWorkspaceRepository.findById(1L)).thenReturn(Mono.empty());

        // when
        Mono<BlockListResponseDto> responseMono = blockService.getBlocks(1L, 2L)
                .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(mockSecurityContext)));

        // then
        StepVerifier.create(responseMono)
                .expectError(WorkspaceException.class)
                .verify();
    }

    @Test
    @DisplayName("블록 목록 조회 실패 - 존재하지 않는 페이지")
    void getBlocks_WhenPageNotFound_ShouldThrowException() {
        // given
        when(reactivePageRepository.findByIdAndWorkspaceId(2L, 1L)).thenReturn(Mono.empty());

        // when
        Mono<BlockListResponseDto> responseMono = blockService.getBlocks(1L, 2L)
                .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(mockSecurityContext)));

        // then
        StepVerifier.create(responseMono)
                .expectError(PageException.class)
                .verify();
    }

    @Test
    @DisplayName("블록 목록 조회 실패 - 페이지에 권한이 없는 멤버")
    void getBlocks_WhenMemberHasNoPermission_ShouldThrowException() {
        // given
        Workspace workspaceOwnedByOthers = Workspace.builder().id(1L).createdBy(2L).build();
        Page pageOwnedByOthers = Page.builder().id(2L).workspaceId(1L).createdBy(2L).isPublic(false).build();

        when(reactivePageRepository.findByIdAndWorkspaceId(2L, 1L)).thenReturn(Mono.just(pageOwnedByOthers));
        when(reactiveWorkspaceRepository.findById(1L)).thenReturn(Mono.just(workspaceOwnedByOthers));
        when(reactiveWorkspaceMemberRepository.existsByWorkspaceIdAndUserId(1L, 1L)).thenReturn(Mono.just(true));
        when(reactivePagePermissionRepository.findByPageIdAndUserId(2L, 1L)).thenReturn(Mono.empty());

        // when
        Mono<BlockListResponseDto> responseMono = blockService.getBlocks(1L, 2L)
                .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(mockSecurityContext)));

        // then
        StepVerifier.create(responseMono)
                .expectError(PageException.class)
                .verify();
    }

    @Test
    @DisplayName("블록 목록 조회 실패 - 워크스페이스 멤버가 아닌데 비공개 페이지 접근 시")
    void getBlocks_WhenNotMemberAndPageIsPrivate_ShouldThrowException() {
        // given
        Workspace workspaceOwnedByOthers = Workspace.builder().id(1L).createdBy(2L).build();
        Page privatePage = Page.builder().id(2L).workspaceId(1L).createdBy(2L).isPublic(false).build();

        when(reactivePageRepository.findByIdAndWorkspaceId(2L, 1L)).thenReturn(Mono.just(privatePage));
        when(reactiveWorkspaceRepository.findById(1L)).thenReturn(Mono.just(workspaceOwnedByOthers));
        when(reactiveWorkspaceMemberRepository.existsByWorkspaceIdAndUserId(1L, 1L)).thenReturn(Mono.just(false));

        // when
        Mono<BlockListResponseDto> responseMono = blockService.getBlocks(1L, 2L)
                .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(mockSecurityContext)));

        // then
        StepVerifier.create(responseMono)
                .expectError(PageException.class)
                .verify();
    }

    @Test
    @DisplayName("블록 조회 성공 - 워크스페이스 소유자")
    void getBlock_WhenWorkspaceOwner_ShouldSucceed() {
        // given
        when(reactiveBlockRepository.findByIdAndPageId(4L, 2L)).thenReturn(Mono.just(mockBlock));
        when(reactivePageRepository.findByIdAndWorkspaceId(2L, 1L)).thenReturn(Mono.just(mockPage));
        when(reactiveWorkspaceRepository.findById(1L)).thenReturn(Mono.just(mockWorkspace));

        // when
        Mono<BlockResponseDto> result = blockService.getBlock(1L, 2L, 4L)
                .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(mockSecurityContext)));

        // then
        StepVerifier.create(result)
                .expectNextMatches(dto -> dto.getId().equals(4L) && dto.getContent().equals("Test Block Content"))
                .verifyComplete();
    }

    @Test
    @DisplayName("블록 조회 성공 - 페이지에 읽기 권한이 있는 멤버")
    void getBlock_WhenMemberHasReadPermission_ShouldSucceed() {
        // given
        Workspace workspaceOwnedByOthers = Workspace.builder().id(1L).createdBy(2L).build();
        Page privatePageOwnedByOthers = Page.builder().id(2L).workspaceId(1L).createdBy(2L).isPublic(false).build();
        Block blockInPrivatePage = Block.builder().id(4L).pageId(2L).type("text").content("Private Block Content").build();

        when(reactiveBlockRepository.findByIdAndPageId(4L, 2L)).thenReturn(Mono.just(blockInPrivatePage));
        when(reactivePageRepository.findByIdAndWorkspaceId(2L, 1L)).thenReturn(Mono.just(privatePageOwnedByOthers));
        when(reactiveWorkspaceRepository.findById(1L)).thenReturn(Mono.just(workspaceOwnedByOthers));
        when(reactiveWorkspaceMemberRepository.existsByWorkspaceIdAndUserId(1L, 1L)).thenReturn(Mono.just(true));
        when(reactivePagePermissionRepository.findByPageIdAndUserId(2L, 1L)).thenReturn(Mono.just(mockPagePermission));

        // when
        Mono<BlockResponseDto> result = blockService.getBlock(1L, 2L, 4L)
                .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(mockSecurityContext)));

        // then
        StepVerifier.create(result)
                .expectNextMatches(dto -> dto.getId().equals(4L) && dto.getContent().equals("Private Block Content"))
                .verifyComplete();
    }

    @Test
    @DisplayName("블록 조회 성공 - 워크스페이스 멤버가 아니지만 공개 페이지")
    void getBlock_WhenNotMemberAndPageIsPublic_ShouldSucceed() {
        // given
        Workspace workspaceOwnedByOthers = Workspace.builder().id(1L).createdBy(2L).build();
        Page publicPage = Page.builder().id(3L).workspaceId(1L).createdBy(2L).isPublic(true).build();
        Block blockInPublicPage = Block.builder().id(5L).pageId(3L).type("text").content("Public Block Content").build();

        when(reactiveBlockRepository.findByIdAndPageId(5L, 3L)).thenReturn(Mono.just(blockInPublicPage));
        when(reactivePageRepository.findByIdAndWorkspaceId(3L, 1L)).thenReturn(Mono.just(publicPage));
        when(reactiveWorkspaceRepository.findById(1L)).thenReturn(Mono.just(workspaceOwnedByOthers));
        when(reactiveWorkspaceMemberRepository.existsByWorkspaceIdAndUserId(1L, 1L)).thenReturn(Mono.just(false));

        // when
        Mono<BlockResponseDto> result = blockService.getBlock(1L, 3L, 5L)
                .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(mockSecurityContext)));

        // then
        StepVerifier.create(result)
                .expectNextMatches(dto -> dto.getId().equals(5L) && dto.getContent().equals("Public Block Content"))
                .verifyComplete();
    }

    @Test
    @DisplayName("블록 조회 실패 - 인증되지 않은 사용자")
    void getBlock_WhenNotAuthenticated_ShouldThrowException() {
        // given
        try (var mockedHolder = mockStatic(ReactiveSecurityContextHolder.class)) {
            mockedHolder.when(ReactiveSecurityContextHolder::getContext).thenReturn(Mono.empty());

            // when & then
            StepVerifier.create(blockService.getBlock(1L, 2L, 4L))
                    .expectError(WorkspaceException.class)
                    .verify();
        }
    }

    @Test
    @DisplayName("블록 조회 실패 - 존재하지 않는 블록")
    void getBlock_WhenBlockNotFound_ShouldThrowException() {
        // given
        when(reactiveBlockRepository.findByIdAndPageId(4L, 2L)).thenReturn(Mono.empty());

        // when
        Mono<BlockResponseDto> result = blockService.getBlock(1L, 2L, 4L)
                .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(mockSecurityContext)));

        // then
        StepVerifier.create(result)
                .expectError(BlockException.class)
                .verify();
    }

    @Test
    @DisplayName("블록 조회 실패 - 존재하지 않는 페이지")
    void getBlock_WhenPageNotFound_ShouldThrowException() {
        // given
        when(reactiveBlockRepository.findByIdAndPageId(4L, 2L)).thenReturn(Mono.just(mockBlock));
        when(reactivePageRepository.findByIdAndWorkspaceId(2L, 1L)).thenReturn(Mono.empty());

        // when
        Mono<BlockResponseDto> result = blockService.getBlock(1L, 2L, 4L)
                .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(mockSecurityContext)));

        // then
        StepVerifier.create(result)
                .expectError(PageException.class)
                .verify();
    }

    @Test
    @DisplayName("블록 조회 실패 - 존재하지 않는 워크스페이스")
    void getBlock_WhenWorkspaceNotFound_ShouldThrowException() {
        // given
        when(reactiveBlockRepository.findByIdAndPageId(4L, 2L)).thenReturn(Mono.just(mockBlock));
        when(reactivePageRepository.findByIdAndWorkspaceId(2L, 1L)).thenReturn(Mono.just(mockPage));
        when(reactiveWorkspaceRepository.findById(1L)).thenReturn(Mono.empty());

        // when
        Mono<BlockResponseDto> result = blockService.getBlock(1L, 2L, 4L)
                .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(mockSecurityContext)));

        // then
        StepVerifier.create(result)
                .expectError(WorkspaceException.class)
                .verify();
    }

    @Test
    @DisplayName("블록 조회 실패 - 페이지에 권한이 없는 멤버")
    void getBlock_WhenMemberHasNoPermission_ShouldThrowException() {
        // given
        Workspace workspaceOwnedByOthers = Workspace.builder().id(1L).createdBy(2L).build();
        Page privatePageOwnedByOthers = Page.builder().id(2L).workspaceId(1L).createdBy(2L).isPublic(false).build();
        Block blockInPrivatePage = Block.builder().id(4L).pageId(2L).type("text").build();

        when(reactiveBlockRepository.findByIdAndPageId(4L, 2L)).thenReturn(Mono.just(blockInPrivatePage));
        when(reactivePageRepository.findByIdAndWorkspaceId(2L, 1L)).thenReturn(Mono.just(privatePageOwnedByOthers));
        when(reactiveWorkspaceRepository.findById(1L)).thenReturn(Mono.just(workspaceOwnedByOthers));
        when(reactiveWorkspaceMemberRepository.existsByWorkspaceIdAndUserId(1L, 1L)).thenReturn(Mono.just(true));
        when(reactivePagePermissionRepository.findByPageIdAndUserId(2L, 1L)).thenReturn(Mono.empty());

        // when
        Mono<BlockResponseDto> result = blockService.getBlock(1L, 2L, 4L)
                .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(mockSecurityContext)));

        // then
        StepVerifier.create(result)
                .expectError(PageException.class)
                .verify();
    }

    @Test
    @DisplayName("블록 조회 실패 - 워크스페이스 비멤버, 비공개 페이지")
    void getBlock_WhenNotMemberAndPageIsPrivate_ShouldThrowException() {
        // given
        Workspace workspaceOwnedByOthers = Workspace.builder().id(1L).createdBy(2L).build();
        Page privatePageOwnedByOthers = Page.builder().id(2L).workspaceId(1L).createdBy(2L).isPublic(false).build();
        Block blockInPrivatePage = Block.builder().id(4L).pageId(2L).type("text").build();

        when(reactiveBlockRepository.findByIdAndPageId(4L, 2L)).thenReturn(Mono.just(blockInPrivatePage));
        when(reactivePageRepository.findByIdAndWorkspaceId(2L, 1L)).thenReturn(Mono.just(privatePageOwnedByOthers));
        when(reactiveWorkspaceRepository.findById(1L)).thenReturn(Mono.just(workspaceOwnedByOthers));
        when(reactiveWorkspaceMemberRepository.existsByWorkspaceIdAndUserId(1L, 1L)).thenReturn(Mono.just(false));

        // when
        Mono<BlockResponseDto> result = blockService.getBlock(1L, 2L, 4L)
                .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(mockSecurityContext)));

        // then
        StepVerifier.create(result)
                .expectError(PageException.class)
                .verify();
    }

    @Test
    @DisplayName("예상치 못한 예외 발생 시 BlockException으로 변환")
    void getBlock_WhenUnexpectedError_ShouldMapToBlockException() {
        // given
        when(reactiveBlockRepository.findByIdAndPageId(4L, 2L)).thenReturn(Mono.just(mockBlock));
        when(reactivePageRepository.findByIdAndWorkspaceId(2L, 1L)).thenReturn(Mono.just(mockPage));
        when(reactiveWorkspaceRepository.findById(1L)).thenReturn(Mono.error(new RuntimeException("Unexpected error")));

        // when
        Mono<BlockResponseDto> result = blockService.getBlock(1L, 2L, 4L)
                .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(mockSecurityContext)));

        // then
        StepVerifier.create(result)
                .expectError(BlockException.class)
                .verify();
    }

    @Test
    @DisplayName("블록 수정 성공 - 워크스페이스 소유자")
    void updateBlock_WhenWorkspaceOwner_ShouldSucceed() {
        // given
        when(reactiveBlockRepository.findByIdAndPageId(4L, 2L)).thenReturn(Mono.just(mockBlock));
        when(reactivePageRepository.findByIdAndWorkspaceId(2L, 1L)).thenReturn(Mono.just(mockPage));
        when(reactiveWorkspaceRepository.findById(1L)).thenReturn(Mono.just(mockWorkspace));
        when(reactiveBlockRepository.save(any(Block.class))).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        // when
        Mono<BlockResponseDto> result = blockService.updateBlock(1L, 2L, 4L, updateRequestDto)
                .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(mockSecurityContext)));

        // then
        StepVerifier.create(result)
                .expectNextMatches(dto -> "Updated Block Content".equals(dto.getContent()) && "heading".equals(dto.getType()))
                .verifyComplete();
        verify(reactiveBlockRepository).save(any(Block.class));
        verify(reactiveWorkspaceMemberRepository, never()).existsByWorkspaceIdAndUserId(any(), any());
        verify(reactivePagePermissionRepository, never()).findByPageIdAndUserId(any(), any());
    }

    @Test
    @DisplayName("블록 수정 성공 - 워크스페이스 멤버, EDIT 권한 소유")
    void updateBlock_WhenMemberWithEditPermission_ShouldSucceed() {
        // given
        Workspace workspaceOwnedByOthers = Workspace.builder().id(1L).createdBy(2L).build();
        PagePermission editPermission = PagePermission.builder()
                .pageId(2L)
                .userId(1L)
                .permission(PagePermissionType.EDIT.name())
                .build();
        BlockUpdateRequestDto memberUpdateRequest = BlockUpdateRequestDto.builder().content("Member Updated Content").build();

        when(reactiveBlockRepository.findByIdAndPageId(4L, 2L)).thenReturn(Mono.just(mockBlock));
        when(reactivePageRepository.findByIdAndWorkspaceId(2L, 1L)).thenReturn(Mono.just(mockPage));
        when(reactiveWorkspaceRepository.findById(1L)).thenReturn(Mono.just(workspaceOwnedByOthers));
        when(reactiveWorkspaceMemberRepository.existsByWorkspaceIdAndUserId(1L, 1L)).thenReturn(Mono.just(true));
        when(reactivePagePermissionRepository.findByPageIdAndUserId(2L, 1L)).thenReturn(Mono.just(editPermission));
        when(reactiveBlockRepository.save(any(Block.class))).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        // when
        Mono<BlockResponseDto> result = blockService.updateBlock(1L, 2L, 4L, memberUpdateRequest)
                .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(mockSecurityContext)));

        // then
        StepVerifier.create(result)
                .expectNextMatches(dto -> "Member Updated Content".equals(dto.getContent()))
                .verifyComplete();
        verify(reactiveBlockRepository).save(any(Block.class));
    }

    @Test
    @DisplayName("블록 수정 실패 - 워크스페이스 멤버, READ 권한만 소유")
    void updateBlock_WhenMemberWithReadPermission_ShouldThrowException() {
        // given
        Workspace workspaceOwnedByOthers = Workspace.builder().id(1L).createdBy(2L).build();
        BlockUpdateRequestDto memberUpdateRequest = BlockUpdateRequestDto.builder().content("Should Fail").build();

        when(reactiveBlockRepository.findByIdAndPageId(4L, 2L)).thenReturn(Mono.just(mockBlock));
        when(reactivePageRepository.findByIdAndWorkspaceId(2L, 1L)).thenReturn(Mono.just(mockPage));
        when(reactiveWorkspaceRepository.findById(1L)).thenReturn(Mono.just(workspaceOwnedByOthers));
        when(reactiveWorkspaceMemberRepository.existsByWorkspaceIdAndUserId(1L, 1L)).thenReturn(Mono.just(true));
        when(reactivePagePermissionRepository.findByPageIdAndUserId(2L, 1L)).thenReturn(Mono.just(mockReadOnlyPermission));

        // when
        Mono<BlockResponseDto> result = blockService.updateBlock(1L, 2L, 4L, memberUpdateRequest)
                .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(mockSecurityContext)));

        // then
        StepVerifier.create(result)
                .expectErrorMatches(throwable ->
                        throwable instanceof PageException &&
                                ((PageException) throwable).getErrorCode() == ErrorCode.PAGE_PERMISSION_DENIED)
                .verify();
        verify(reactiveBlockRepository, never()).save(any(Block.class));
    }

    @Test
    @DisplayName("블록 수정 실패 - 워크스페이스 멤버 아님")
    void updateBlock_WhenNotAWorkspaceMember_ShouldThrowException() {
        // given
        Workspace workspaceOwnedByOthers = Workspace.builder().id(1L).createdBy(2L).build();
        BlockUpdateRequestDto memberUpdateRequest = BlockUpdateRequestDto.builder().content("Should Fail").build();

        when(reactiveBlockRepository.findByIdAndPageId(4L, 2L)).thenReturn(Mono.just(mockBlock));
        when(reactivePageRepository.findByIdAndWorkspaceId(2L, 1L)).thenReturn(Mono.just(mockPage));
        when(reactiveWorkspaceRepository.findById(1L)).thenReturn(Mono.just(workspaceOwnedByOthers));
        when(reactiveWorkspaceMemberRepository.existsByWorkspaceIdAndUserId(1L, 1L)).thenReturn(Mono.just(false));

        // when
        Mono<BlockResponseDto> result = blockService.updateBlock(1L, 2L, 4L, memberUpdateRequest)
                .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(mockSecurityContext)));

        // then
        StepVerifier.create(result)
                .expectErrorMatches(throwable ->
                        throwable instanceof WorkspaceException &&
                                ((WorkspaceException) throwable).getErrorCode() == ErrorCode.MEMBER_NOT_FOUND)
                .verify();
        verify(reactiveBlockRepository, never()).save(any(Block.class));
    }

    @Test
    @DisplayName("블록 수정 실패 - 인증되지 않은 사용자")
    void updateBlock_WhenNotAuthenticated_ShouldThrowException() {
        // given
        BlockUpdateRequestDto request = BlockUpdateRequestDto.builder().content("Should Fail").build();
        try (var mockedHolder = mockStatic(ReactiveSecurityContextHolder.class)) {
            mockedHolder.when(ReactiveSecurityContextHolder::getContext).thenReturn(Mono.empty());

            // when & then
            StepVerifier.create(blockService.updateBlock(1L, 2L, 4L, request))
                    .expectError(WorkspaceException.class)
                    .verify();
        }
    }

    @Test
    @DisplayName("블록 수정 실패 - 존재하지 않는 워크스페이스")
    void updateBlock_WhenWorkspaceNotFound_ShouldThrowException() {
        // given
        BlockUpdateRequestDto request = BlockUpdateRequestDto.builder().content("Fail").build();
        when(reactiveBlockRepository.findByIdAndPageId(4L, 2L)).thenReturn(Mono.just(mockBlock));
        when(reactivePageRepository.findByIdAndWorkspaceId(2L, 1L)).thenReturn(Mono.just(mockPage));
        when(reactiveWorkspaceRepository.findById(1L)).thenReturn(Mono.empty());

        // when
        Mono<BlockResponseDto> result = blockService.updateBlock(1L, 2L, 4L, request)
                .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(mockSecurityContext)));

        // then
        StepVerifier.create(result)
                .expectErrorMatches(throwable ->
                        throwable instanceof WorkspaceException &&
                                ((WorkspaceException) throwable).getErrorCode() == ErrorCode.WORKSPACE_NOT_FOUND)
                .verify();
        verify(reactiveBlockRepository, never()).save(any(Block.class));
    }

    @Test
    @DisplayName("블록 수정 실패 - 존재하지 않는 페이지")
    void updateBlock_WhenPageNotFound_ShouldThrowException() {
        // given
        BlockUpdateRequestDto request = BlockUpdateRequestDto.builder().content("Fail").build();
        when(reactiveBlockRepository.findByIdAndPageId(4L, 2L)).thenReturn(Mono.just(mockBlock));
        when(reactivePageRepository.findByIdAndWorkspaceId(2L, 1L)).thenReturn(Mono.empty());

        // when
        Mono<BlockResponseDto> result = blockService.updateBlock(1L, 2L, 4L, request)
                .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(mockSecurityContext)));

        // then
        StepVerifier.create(result)
                .expectErrorMatches(throwable ->
                        throwable instanceof PageException &&
                                ((PageException) throwable).getErrorCode() == ErrorCode.PAGE_NOT_FOUND)
                .verify();
        verify(reactiveBlockRepository, never()).save(any(Block.class));
    }

    @Test
    @DisplayName("블록 수정 실패 - 존재하지 않는 블록")
    void updateBlock_WhenBlockNotFound_ShouldThrowException() {
        // given
        BlockUpdateRequestDto request = BlockUpdateRequestDto.builder().content("Fail").build();
        when(reactiveBlockRepository.findByIdAndPageId(4L, 2L)).thenReturn(Mono.empty());

        // when
        Mono<BlockResponseDto> result = blockService.updateBlock(1L, 2L, 4L, request)
                .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(mockSecurityContext)));

        // then
        StepVerifier.create(result)
                .expectErrorMatches(throwable ->
                        throwable instanceof BlockException &&
                                ((BlockException) throwable).getErrorCode() == ErrorCode.BLOCK_NOT_FOUND)
                .verify();
        verify(reactivePageRepository, never()).findByIdAndWorkspaceId(any(), any());
        verify(reactiveBlockRepository, never()).save(any(Block.class));
    }

    @Test
    @DisplayName("블록 수정 실패 - 예상치 못한 예외 발생 시 BlockException으로 변환")
    void updateBlock_WhenUnexpectedError_ShouldMapToBlockException() {
        // given
        BlockUpdateRequestDto request = BlockUpdateRequestDto.builder().content("Fail").build();
        when(reactiveBlockRepository.findByIdAndPageId(4L, 2L)).thenReturn(Mono.error(new RuntimeException("Unexpected error")));

        // when
        Mono<BlockResponseDto> result = blockService.updateBlock(1L, 2L, 4L, request)
                .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(mockSecurityContext)));

        // then
        StepVerifier.create(result)
                .expectErrorMatches(throwable ->
                        throwable instanceof BlockException &&
                                ((BlockException) throwable).getErrorCode() == ErrorCode.UNEXPECTED_ERROR)
                .verify();
    }

    @Test
    @DisplayName("블록 이동 성공 - 워크스페이스 소유자")
    void moveBlock_WhenWorkspaceOwner_ShouldSucceed() {
        // given
        BlockMoveRequestDto request = BlockMoveRequestDto.builder()
                .newParentBlockId(3L)
                .newPosition(5)
                .build();
        Block movedBlock = Block.builder()
                .id(4L)
                .pageId(2L)
                .parentBlockId(3L)
                .position(5)
                .type("text")
                .content("Test Block Content")
                .lastEditedBy(1L)
                .updatedAt(LocalDateTime.now())
                .build();

        when(reactiveBlockRepository.findByIdAndPageId(4L, 2L)).thenReturn(Mono.just(mockBlock));
        when(reactivePageRepository.findByIdAndWorkspaceId(2L, 1L)).thenReturn(Mono.just(mockPage));
        when(reactiveWorkspaceRepository.findById(1L)).thenReturn(Mono.just(mockWorkspace));
        when(reactiveBlockRepository.findByIdAndPageId(3L, 2L)).thenReturn(Mono.just(mockParentBlock));
        when(reactiveBlockRepository.save(any(Block.class))).thenReturn(Mono.just(movedBlock));

        // when
        Mono<BlockResponseDto> result = blockService.moveBlock(1L, 2L, 4L, request)
                .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(mockSecurityContext)));

        // then
        StepVerifier.create(result)
                .expectNextMatches(dto -> dto.getId().equals(4L) && dto.getParentBlockId().equals(3L) && dto.getPosition() == 5)
                .verifyComplete();
        verify(reactiveBlockRepository).save(any(Block.class));
        verify(reactiveWorkspaceMemberRepository, never()).existsByWorkspaceIdAndUserId(any(), any());
        verify(reactivePagePermissionRepository, never()).findByPageIdAndUserId(any(), any());
    }

    @Test
    @DisplayName("블록 이동 성공 - 워크스페이스 멤버, EDIT 권한 소유")
    void moveBlock_WhenMemberWithEditPermission_ShouldSucceed() {
        // given
        Workspace workspaceOwnedByOthers = Workspace.builder().id(1L).createdBy(2L).build();
        PagePermission editPermission = PagePermission.builder()
                .pageId(2L)
                .userId(1L)
                .permission(PagePermissionType.EDIT.name())
                .build();
        BlockMoveRequestDto request = BlockMoveRequestDto.builder().newPosition(1).build();
        Block movedBlock = Block.builder()
                .id(4L)
                .pageId(2L)
                .parentBlockId(null)
                .position(1)
                .lastEditedBy(1L)
                .updatedAt(LocalDateTime.now())
                .build();

        when(reactiveBlockRepository.findByIdAndPageId(4L, 2L)).thenReturn(Mono.just(mockBlock));
        when(reactivePageRepository.findByIdAndWorkspaceId(2L, 1L)).thenReturn(Mono.just(mockPage));
        when(reactiveWorkspaceRepository.findById(1L)).thenReturn(Mono.just(workspaceOwnedByOthers));
        when(reactiveWorkspaceMemberRepository.existsByWorkspaceIdAndUserId(1L, 1L)).thenReturn(Mono.just(true));
        when(reactivePagePermissionRepository.findByPageIdAndUserId(2L, 1L)).thenReturn(Mono.just(editPermission));
        when(reactiveBlockRepository.save(any(Block.class))).thenReturn(Mono.just(movedBlock));

        // when
        Mono<BlockResponseDto> result = blockService.moveBlock(1L, 2L, 4L, request)
                .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(mockSecurityContext)));

        // then
        StepVerifier.create(result)
                .expectNextMatches(dto -> dto.getId().equals(4L) && dto.getParentBlockId() == null && dto.getPosition() == 1)
                .verifyComplete();
        verify(reactiveBlockRepository).save(any(Block.class));
    }

    @Test
    @DisplayName("블록 이동 실패 - 워크스페이스 멤버, READ 권한만 소유")
    void moveBlock_WhenMemberWithReadPermission_ShouldThrowException() {
        // given
        Workspace workspaceOwnedByOthers = Workspace.builder().id(1L).createdBy(2L).build();
        BlockMoveRequestDto request = BlockMoveRequestDto.builder().newPosition(1).build();

        when(reactiveBlockRepository.findByIdAndPageId(4L, 2L)).thenReturn(Mono.just(mockBlock));
        when(reactivePageRepository.findByIdAndWorkspaceId(2L, 1L)).thenReturn(Mono.just(mockPage));
        when(reactiveWorkspaceRepository.findById(1L)).thenReturn(Mono.just(workspaceOwnedByOthers));
        when(reactiveWorkspaceMemberRepository.existsByWorkspaceIdAndUserId(1L, 1L)).thenReturn(Mono.just(true));
        when(reactivePagePermissionRepository.findByPageIdAndUserId(2L, 1L)).thenReturn(Mono.just(mockReadOnlyPermission));

        // when
        Mono<BlockResponseDto> result = blockService.moveBlock(1L, 2L, 4L, request)
                .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(mockSecurityContext)));

        // then
        StepVerifier.create(result)
                .expectErrorMatches(throwable ->
                        throwable instanceof PageException &&
                                ((PageException) throwable).getErrorCode() == ErrorCode.PAGE_PERMISSION_DENIED)
                .verify();
        verify(reactiveBlockRepository, never()).save(any(Block.class));
    }

    @Test
    @DisplayName("블록 이동 실패 - 워크스페이스 멤버 아님")
    void moveBlock_WhenNotAWorkspaceMember_ShouldThrowException() {
        // given
        Workspace workspaceOwnedByOthers = Workspace.builder().id(1L).createdBy(2L).build();
        BlockMoveRequestDto request = BlockMoveRequestDto.builder().newPosition(1).build();

        when(reactiveBlockRepository.findByIdAndPageId(4L, 2L)).thenReturn(Mono.just(mockBlock));
        when(reactivePageRepository.findByIdAndWorkspaceId(2L, 1L)).thenReturn(Mono.just(mockPage));
        when(reactiveWorkspaceRepository.findById(1L)).thenReturn(Mono.just(workspaceOwnedByOthers));
        when(reactiveWorkspaceMemberRepository.existsByWorkspaceIdAndUserId(1L, 1L)).thenReturn(Mono.just(false));

        // when
        Mono<BlockResponseDto> result = blockService.moveBlock(1L, 2L, 4L, request)
                .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(mockSecurityContext)));

        // then
        StepVerifier.create(result)
                .expectErrorMatches(throwable ->
                        throwable instanceof WorkspaceException &&
                                ((WorkspaceException) throwable).getErrorCode() == ErrorCode.MEMBER_NOT_FOUND)
                .verify();
        verify(reactiveBlockRepository, never()).save(any(Block.class));
    }

    @Test
    @DisplayName("블록 이동 실패 - 자기 자신을 부모로 설정 시도")
    void moveBlock_WhenMovingToSelf_ShouldThrowException() {
        // given
        BlockMoveRequestDto request = BlockMoveRequestDto.builder()
                .newParentBlockId(4L)
                .newPosition(0)
                .build();

        when(reactiveBlockRepository.findByIdAndPageId(4L, 2L)).thenReturn(Mono.just(mockBlock));
        when(reactivePageRepository.findByIdAndWorkspaceId(2L, 1L)).thenReturn(Mono.just(mockPage));
        when(reactiveWorkspaceRepository.findById(1L)).thenReturn(Mono.just(mockWorkspace));

        // when
        Mono<BlockResponseDto> result = blockService.moveBlock(1L, 2L, 4L, request)
                .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(mockSecurityContext)));

        // then
        StepVerifier.create(result)
                .expectErrorMatches(throwable ->
                        throwable instanceof BlockException &&
                                ((BlockException) throwable).getErrorCode() == ErrorCode.CANNOT_MOVE_TO_SELF)
                .verify();
        verify(reactiveBlockRepository, never()).save(any(Block.class));
    }

    @Test
    @DisplayName("블록 이동 실패 - 존재하지 않는 새 부모 블록")
    void moveBlock_WhenNewParentBlockNotFound_ShouldThrowException() {
        // given
        BlockMoveRequestDto request = BlockMoveRequestDto.builder()
                .newParentBlockId(999L)
                .newPosition(0)
                .build();

        when(reactiveBlockRepository.findByIdAndPageId(4L, 2L)).thenReturn(Mono.just(mockBlock));
        when(reactivePageRepository.findByIdAndWorkspaceId(2L, 1L)).thenReturn(Mono.just(mockPage));
        when(reactiveWorkspaceRepository.findById(1L)).thenReturn(Mono.just(mockWorkspace));
        when(reactiveBlockRepository.findByIdAndPageId(999L, 2L)).thenReturn(Mono.empty());

        // when
        Mono<BlockResponseDto> result = blockService.moveBlock(1L, 2L, 4L, request)
                .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(mockSecurityContext)));

        // then
        StepVerifier.create(result)
                .expectErrorMatches(throwable ->
                        throwable instanceof BlockException &&
                                ((BlockException) throwable).getErrorCode() == ErrorCode.PARENT_BLOCK_NOT_FOUND)
                .verify();
        verify(reactiveBlockRepository, never()).save(any(Block.class));
    }

    @Test
    @DisplayName("블록 이동 실패 - 존재하지 않는 블록")
    void moveBlock_WhenBlockNotFound_ShouldThrowException() {
        // given
        BlockMoveRequestDto request = BlockMoveRequestDto.builder().newPosition(1).build();

        when(reactiveBlockRepository.findByIdAndPageId(4L, 2L)).thenReturn(Mono.empty());

        // when
        Mono<BlockResponseDto> result = blockService.moveBlock(1L, 2L, 4L, request)
                .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(mockSecurityContext)));

        // then
        StepVerifier.create(result)
                .expectErrorMatches(throwable ->
                        throwable instanceof BlockException &&
                                ((BlockException) throwable).getErrorCode() == ErrorCode.BLOCK_NOT_FOUND)
                .verify();
        verify(reactiveBlockRepository, never()).save(any(Block.class));
    }

    @Test
    @DisplayName("블록 이동 실패 - 존재하지 않는 페이지")
    void moveBlock_WhenPageNotFound_ShouldThrowException() {
        // given
        BlockMoveRequestDto request = BlockMoveRequestDto.builder().newPosition(1).build();

        when(reactiveBlockRepository.findByIdAndPageId(4L, 2L)).thenReturn(Mono.just(mockBlock));
        when(reactivePageRepository.findByIdAndWorkspaceId(2L, 1L)).thenReturn(Mono.empty());

        // when
        Mono<BlockResponseDto> result = blockService.moveBlock(1L, 2L, 4L, request)
                .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(mockSecurityContext)));

        // then
        StepVerifier.create(result)
                .expectErrorMatches(throwable ->
                        throwable instanceof PageException &&
                                ((PageException) throwable).getErrorCode() == ErrorCode.PAGE_NOT_FOUND)
                .verify();
        verify(reactiveBlockRepository, never()).save(any(Block.class));
    }

    @Test
    @DisplayName("블록 이동 실패 - 존재하지 않는 워크스페이스")
    void moveBlock_WhenWorkspaceNotFound_ShouldThrowException() {
        // given
        BlockMoveRequestDto request = BlockMoveRequestDto.builder().newPosition(1).build();

        when(reactiveBlockRepository.findByIdAndPageId(4L, 2L)).thenReturn(Mono.just(mockBlock));
        when(reactivePageRepository.findByIdAndWorkspaceId(2L, 1L)).thenReturn(Mono.just(mockPage));
        when(reactiveWorkspaceRepository.findById(1L)).thenReturn(Mono.empty());

        // when
        Mono<BlockResponseDto> result = blockService.moveBlock(1L, 2L, 4L, request)
                .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(mockSecurityContext)));

        // then
        StepVerifier.create(result)
                .expectErrorMatches(throwable ->
                        throwable instanceof WorkspaceException &&
                                ((WorkspaceException) throwable).getErrorCode() == ErrorCode.WORKSPACE_NOT_FOUND)
                .verify();
        verify(reactiveBlockRepository, never()).save(any(Block.class));
    }

    @Test
    @DisplayName("블록 이동 실패 - 예상치 못한 예외 발생 시 BlockException으로 변환")
    void moveBlock_WhenUnexpectedError_ShouldMapToBlockException() {
        // given
        BlockMoveRequestDto request = BlockMoveRequestDto.builder().newPosition(1).build();
        when(reactiveBlockRepository.findByIdAndPageId(4L, 2L)).thenReturn(Mono.error(new RuntimeException("Unexpected error")));

        // when
        Mono<BlockResponseDto> result = blockService.moveBlock(1L, 2L, 4L, request)
                .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(mockSecurityContext)));

        // then
        StepVerifier.create(result)
                .expectErrorMatches(throwable ->
                        throwable instanceof BlockException &&
                                ((BlockException) throwable).getErrorCode() == ErrorCode.UNEXPECTED_ERROR)
                .verify();
    }

    @Test
    @DisplayName("블록 보관 성공 - 워크스페이스 소유자")
    void archiveBlock_WhenWorkspaceOwner_ShouldSucceed() {
        // given
        when(reactiveBlockRepository.findByIdAndPageId(4L, 2L)).thenReturn(Mono.just(mockBlock));
        when(reactivePageRepository.findByIdAndWorkspaceId(2L, 1L)).thenReturn(Mono.just(mockPage));
        when(reactiveWorkspaceRepository.findById(1L)).thenReturn(Mono.just(mockWorkspace));
        when(reactiveBlockRepository.updateArchiveStatusForBlockTree(4L, true, 1L)).thenReturn(Mono.just(1));

        // when
        Mono<BlockStatusResponseDto> result = blockService.archiveBlock(1L, 2L, 4L)
                .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(mockSecurityContext)));

        // then
        StepVerifier.create(result)
                .expectNextMatches(dto -> dto.getBlockId().equals(4L) && dto.getIsArchived())
                .verifyComplete();
        verify(reactiveBlockRepository).updateArchiveStatusForBlockTree(4L, true, 1L);
    }

    @Test
    @DisplayName("블록 보관 성공 - 워크스페이스 멤버, EDIT 권한 소유")
    void archiveBlock_WhenMemberWithEditPermission_ShouldSucceed() {
        // given
        Workspace workspaceOwnedByOthers = Workspace.builder().id(1L).createdBy(2L).build();
        PagePermission editPermission = PagePermission.builder()
                .pageId(2L)
                .userId(1L)
                .permission(PagePermissionType.EDIT.name())
                .build();

        when(reactiveBlockRepository.findByIdAndPageId(4L, 2L)).thenReturn(Mono.just(mockBlock));
        when(reactivePageRepository.findByIdAndWorkspaceId(2L, 1L)).thenReturn(Mono.just(mockPage));
        when(reactiveWorkspaceRepository.findById(1L)).thenReturn(Mono.just(workspaceOwnedByOthers));
        when(reactiveWorkspaceMemberRepository.existsByWorkspaceIdAndUserId(1L, 1L)).thenReturn(Mono.just(true));
        when(reactivePagePermissionRepository.findByPageIdAndUserId(2L, 1L)).thenReturn(Mono.just(editPermission));
        when(reactiveBlockRepository.updateArchiveStatusForBlockTree(4L, true, 1L)).thenReturn(Mono.just(1));

        // when
        Mono<BlockStatusResponseDto> result = blockService.archiveBlock(1L, 2L, 4L)
                .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(mockSecurityContext)));

        // then
        StepVerifier.create(result)
                .expectNextMatches(dto -> dto.getBlockId().equals(4L) && dto.getIsArchived())
                .verifyComplete();
        verify(reactiveBlockRepository).updateArchiveStatusForBlockTree(4L, true, 1L);
    }

    @Test
    @DisplayName("블록 보관 실패 - 워크스페이스 멤버, READ 권한만 소유")
    void archiveBlock_WhenMemberWithReadPermission_ShouldThrowException() {
        // given
        Workspace workspaceOwnedByOthers = Workspace.builder().id(1L).createdBy(2L).build();

        when(reactiveBlockRepository.findByIdAndPageId(4L, 2L)).thenReturn(Mono.just(mockBlock));
        when(reactivePageRepository.findByIdAndWorkspaceId(2L, 1L)).thenReturn(Mono.just(mockPage));
        when(reactiveWorkspaceRepository.findById(1L)).thenReturn(Mono.just(workspaceOwnedByOthers));
        when(reactiveWorkspaceMemberRepository.existsByWorkspaceIdAndUserId(1L, 1L)).thenReturn(Mono.just(true));
        when(reactivePagePermissionRepository.findByPageIdAndUserId(2L, 1L)).thenReturn(Mono.just(mockReadOnlyPermission));

        // when
        Mono<BlockStatusResponseDto> result = blockService.archiveBlock(1L, 2L, 4L)
                .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(mockSecurityContext)));

        // then
        StepVerifier.create(result)
                .expectErrorMatches(throwable ->
                        throwable instanceof PageException &&
                                ((PageException) throwable).getErrorCode() == ErrorCode.PAGE_PERMISSION_DENIED)
                .verify();
        verify(reactiveBlockRepository, never()).updateArchiveStatusForBlockTree(any(), anyBoolean(), any());
    }

    @Test
    @DisplayName("블록 보관 실패 - 워크스페이스 멤버 아님")
    void archiveBlock_WhenNotAWorkspaceMember_ShouldThrowException() {
        // given
        Workspace workspaceOwnedByOthers = Workspace.builder().id(1L).createdBy(2L).build();

        when(reactiveBlockRepository.findByIdAndPageId(4L, 2L)).thenReturn(Mono.just(mockBlock));
        when(reactivePageRepository.findByIdAndWorkspaceId(2L, 1L)).thenReturn(Mono.just(mockPage));
        when(reactiveWorkspaceRepository.findById(1L)).thenReturn(Mono.just(workspaceOwnedByOthers));
        when(reactiveWorkspaceMemberRepository.existsByWorkspaceIdAndUserId(1L, 1L)).thenReturn(Mono.just(false));

        // when
        Mono<BlockStatusResponseDto> result = blockService.archiveBlock(1L, 2L, 4L)
                .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(mockSecurityContext)));

        // then
        StepVerifier.create(result)
                .expectErrorMatches(throwable ->
                        throwable instanceof WorkspaceException &&
                                ((WorkspaceException) throwable).getErrorCode() == ErrorCode.MEMBER_NOT_FOUND)
                .verify();
        verify(reactiveBlockRepository, never()).updateArchiveStatusForBlockTree(any(), anyBoolean(), any());
    }

    @Test
    @DisplayName("블록 보관 실패 - 존재하지 않는 블록")
    void archiveBlock_WhenBlockNotFound_ShouldThrowException() {
        // given
        when(reactiveBlockRepository.findByIdAndPageId(4L, 2L)).thenReturn(Mono.empty());

        // when
        Mono<BlockStatusResponseDto> result = blockService.archiveBlock(1L, 2L, 4L)
                .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(mockSecurityContext)));

        // then
        StepVerifier.create(result)
                .expectErrorMatches(throwable ->
                        throwable instanceof BlockException &&
                                ((BlockException) throwable).getErrorCode() == ErrorCode.BLOCK_NOT_FOUND)
                .verify();
    }


    @Test
    @DisplayName("블록 복원 성공 - 워크스페이스 소유자")
    void restoreBlock_WhenWorkspaceOwner_ShouldSucceed() {
        // given
        when(reactiveBlockRepository.findByIdAndPageId(4L, 2L)).thenReturn(Mono.just(mockBlock));
        when(reactivePageRepository.findByIdAndWorkspaceId(2L, 1L)).thenReturn(Mono.just(mockPage));
        when(reactiveWorkspaceRepository.findById(1L)).thenReturn(Mono.just(mockWorkspace));
        when(reactiveBlockRepository.updateArchiveStatusForBlockTree(4L, false, 1L)).thenReturn(Mono.just(1));

        // when
        Mono<BlockStatusResponseDto> result = blockService.restoreBlock(1L, 2L, 4L)
                .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(mockSecurityContext)));

        // then
        StepVerifier.create(result)
                .expectNextMatches(dto -> dto.getBlockId().equals(4L) && !dto.getIsArchived())
                .verifyComplete();
        verify(reactiveBlockRepository).updateArchiveStatusForBlockTree(4L, false, 1L);
    }

    @Test
    @DisplayName("블록 복원 성공 - 워크스페이스 멤버, EDIT 권한 소유")
    void restoreBlock_WhenMemberWithEditPermission_ShouldSucceed() {
        // given
        Workspace workspaceOwnedByOthers = Workspace.builder().id(1L).createdBy(2L).build();
        PagePermission editPermission = PagePermission.builder()
                .pageId(2L)
                .userId(1L)
                .permission(PagePermissionType.EDIT.name())
                .build();

        when(reactiveBlockRepository.findByIdAndPageId(4L, 2L)).thenReturn(Mono.just(mockBlock));
        when(reactivePageRepository.findByIdAndWorkspaceId(2L, 1L)).thenReturn(Mono.just(mockPage));
        when(reactiveWorkspaceRepository.findById(1L)).thenReturn(Mono.just(workspaceOwnedByOthers));
        when(reactiveWorkspaceMemberRepository.existsByWorkspaceIdAndUserId(1L, 1L)).thenReturn(Mono.just(true));
        when(reactivePagePermissionRepository.findByPageIdAndUserId(2L, 1L)).thenReturn(Mono.just(editPermission));
        when(reactiveBlockRepository.updateArchiveStatusForBlockTree(4L, false, 1L)).thenReturn(Mono.just(1));

        // when
        Mono<BlockStatusResponseDto> result = blockService.restoreBlock(1L, 2L, 4L)
                .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(mockSecurityContext)));

        // then
        StepVerifier.create(result)
                .expectNextMatches(dto -> dto.getBlockId().equals(4L) && !dto.getIsArchived())
                .verifyComplete();
        verify(reactiveBlockRepository).updateArchiveStatusForBlockTree(4L, false, 1L);
    }

    @Test
    @DisplayName("블록 복원 실패 - 워크스페이스 멤버, READ 권한만 소유")
    void restoreBlock_WhenMemberWithReadPermission_ShouldThrowException() {
        // given
        Workspace workspaceOwnedByOthers = Workspace.builder().id(1L).createdBy(2L).build();

        when(reactiveBlockRepository.findByIdAndPageId(4L, 2L)).thenReturn(Mono.just(mockBlock));
        when(reactivePageRepository.findByIdAndWorkspaceId(2L, 1L)).thenReturn(Mono.just(mockPage));
        when(reactiveWorkspaceRepository.findById(1L)).thenReturn(Mono.just(workspaceOwnedByOthers));
        when(reactiveWorkspaceMemberRepository.existsByWorkspaceIdAndUserId(1L, 1L)).thenReturn(Mono.just(true));
        when(reactivePagePermissionRepository.findByPageIdAndUserId(2L, 1L)).thenReturn(Mono.just(mockReadOnlyPermission));

        // when
        Mono<BlockStatusResponseDto> result = blockService.restoreBlock(1L, 2L, 4L)
                .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(mockSecurityContext)));

        // then
        StepVerifier.create(result)
                .expectErrorMatches(throwable ->
                        throwable instanceof PageException &&
                                ((PageException) throwable).getErrorCode() == ErrorCode.PAGE_PERMISSION_DENIED)
                .verify();
        verify(reactiveBlockRepository, never()).updateArchiveStatusForBlockTree(any(), anyBoolean(), any());
    }

    @Test
    @DisplayName("블록 복원 실패 - 워크스페이스 멤버 아님")
    void restoreBlock_WhenNotAWorkspaceMember_ShouldThrowException() {
        // given
        Workspace workspaceOwnedByOthers = Workspace.builder().id(1L).createdBy(2L).build();

        when(reactiveBlockRepository.findByIdAndPageId(4L, 2L)).thenReturn(Mono.just(mockBlock));
        when(reactivePageRepository.findByIdAndWorkspaceId(2L, 1L)).thenReturn(Mono.just(mockPage));
        when(reactiveWorkspaceRepository.findById(1L)).thenReturn(Mono.just(workspaceOwnedByOthers));
        when(reactiveWorkspaceMemberRepository.existsByWorkspaceIdAndUserId(1L, 1L)).thenReturn(Mono.just(false));

        // when
        Mono<BlockStatusResponseDto> result = blockService.restoreBlock(1L, 2L, 4L)
                .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(mockSecurityContext)));

        // then
        StepVerifier.create(result)
                .expectErrorMatches(throwable ->
                        throwable instanceof WorkspaceException &&
                                ((WorkspaceException) throwable).getErrorCode() == ErrorCode.MEMBER_NOT_FOUND)
                .verify();
        verify(reactiveBlockRepository, never()).updateArchiveStatusForBlockTree(any(), anyBoolean(), any());
    }

    @Test
    @DisplayName("블록 복원 실패 - 존재하지 않는 블록")
    void restoreBlock_WhenBlockNotFound_ShouldThrowException() {
        // given
        when(reactiveBlockRepository.findByIdAndPageId(4L, 2L)).thenReturn(Mono.empty());

        // when
        Mono<BlockStatusResponseDto> result = blockService.restoreBlock(1L, 2L, 4L)
                .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(mockSecurityContext)));

        // then
        StepVerifier.create(result)
                .expectErrorMatches(throwable ->
                        throwable instanceof BlockException &&
                                ((BlockException) throwable).getErrorCode() == ErrorCode.BLOCK_NOT_FOUND)
                .verify();
    }
}
