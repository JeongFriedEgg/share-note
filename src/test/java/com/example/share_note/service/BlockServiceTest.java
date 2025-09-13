package com.example.share_note.service;

import com.example.share_note.domain.Block;
import com.example.share_note.domain.Page;
import com.example.share_note.domain.PagePermission;
import com.example.share_note.domain.Workspace;
import com.example.share_note.dto.CustomUserDetails;
import com.example.share_note.dto.block.BlockCreateRequestDto;
import com.example.share_note.dto.block.BlockMoveRequestDto;
import com.example.share_note.dto.block.BlockUpdateRequestDto;
import com.example.share_note.enums.PagePermissionType;
import com.example.share_note.exception.BlockException;
import com.example.share_note.exception.PageException;
import com.example.share_note.exception.PagePermissionException;
import com.example.share_note.exception.WorkspaceMemberException;
import com.example.share_note.repository.*;
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
class BlockServiceTest {

    @Mock
    private ReactiveBlockRepository reactiveBlockRepository;

    @Mock
    private ReactivePageRepository reactivePageRepository;

    @Mock
    private ReactivePagePermissionRepository reactivePagePermissionRepository;

    @Mock
    private ReactiveWorkspaceRepository reactiveWorkspaceRepository;

    @Mock
    private ReactiveWorkspaceMemberRepository reactiveWorkspaceMemberRepository;

    @Mock
    private UuidUtils uuidUtils;

    @InjectMocks
    private BlockService blockService;

    private UUID workspaceId;
    private UUID userId;
    private UUID pageId;
    private UUID blockId;
    private UUID parentBlockId;
    private String workspaceIdStr;
    private String pageIdStr;
    private String blockIdStr;
    private String parentBlockIdStr;

    private CustomUserDetails customUserDetails;
    private Workspace workspace;
    private Page page;
    private Block block;
    private Block parentBlock;
    private PagePermission pagePermission;

    private SecurityContext securityContext;
    private Authentication authentication;

    @BeforeEach
    void setUp() {
        workspaceId = UUID.randomUUID();
        userId = UUID.randomUUID();
        pageId = UUID.randomUUID();
        blockId = UUID.randomUUID();
        parentBlockId = UUID.randomUUID();
        workspaceIdStr = workspaceId.toString();
        pageIdStr = pageId.toString();
        blockIdStr = blockId.toString();
        parentBlockIdStr = parentBlockId.toString();

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
                .title("Test Page")
                .isPublic(false)
                .isArchived(false)
                .isTemplate(false)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .createdBy(userId)
                .lastEditedBy(userId)
                .build();

        block = Block.builder()
                .id(blockId)
                .pageId(pageId)
                .parentBlockId(parentBlockId)
                .type("text")
                .content("Test block content")
                .position(0)
                .isArchived(false)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .createdBy(userId)
                .lastEditedBy(userId)
                .build();

        parentBlock = Block.builder()
                .id(parentBlockId)
                .pageId(pageId)
                .parentBlockId(null)
                .type("text")
                .content("Parent block content")
                .position(0)
                .isArchived(false)
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
    }

    @Test
    @Order(1)
    @DisplayName("블록 생성 성공 - 워크스페이스 소유자가 루트 블록 생성")
    void createBlock_Success_WorkspaceOwner_RootBlock() {
        // given
        BlockCreateRequestDto request = BlockCreateRequestDto.builder()
                .type("text")
                .content("New block content")
                .position(0)
                .build();

        when(uuidUtils.fromString(workspaceIdStr)).thenReturn(workspaceId);
        when(uuidUtils.fromString(pageIdStr)).thenReturn(pageId);
        when(reactivePageRepository.findByIdAndWorkspaceId(pageId, workspaceId))
                .thenReturn(Mono.just(page));
        when(reactiveWorkspaceRepository.findById(workspaceId)).thenReturn(Mono.just(workspace));
        when(reactiveBlockRepository.save(any(Block.class))).thenReturn(Mono.just(block));

        try (MockedStatic<ReactiveSecurityContextHolder> mockedSecurityContext =
                     mockStatic(ReactiveSecurityContextHolder.class)) {

            mockedSecurityContext.when(ReactiveSecurityContextHolder::getContext)
                    .thenReturn(Mono.just(securityContext));

            // when & then
            StepVerifier.create(blockService.createBlock(workspaceIdStr, pageIdStr, request))
                    .expectNextMatches(response -> {
                        assertThat(response.getId()).isNotNull();
                        assertThat(response.getPageId()).isEqualTo(pageIdStr);
                        assertThat(response.getType()).isEqualTo("text");
                        return true;
                    })
                    .verifyComplete();
        }

        verify(reactiveBlockRepository).save(any(Block.class));
    }

    @Test
    @Order(2)
    @DisplayName("블록 생성 성공 - 워크스페이스 멤버가 하위 블록 생성")
    void createBlock_Success_WorkspaceMember_ChildBlock() {
        // given
        BlockCreateRequestDto request = BlockCreateRequestDto.builder()
                .parentBlockId(parentBlockIdStr)
                .type("text")
                .content("Child block content")
                .position(1)
                .build();

        Workspace memberWorkspace = Workspace.builder()
                .id(workspaceId)
                .name("Test Workspace")
                .createdBy(UUID.randomUUID()) // 다른 사용자가 소유자
                .build();

        when(uuidUtils.fromString(workspaceIdStr)).thenReturn(workspaceId);
        when(uuidUtils.fromString(pageIdStr)).thenReturn(pageId);
        when(uuidUtils.fromString(parentBlockIdStr)).thenReturn(parentBlockId);
        when(reactivePageRepository.findByIdAndWorkspaceId(pageId, workspaceId))
                .thenReturn(Mono.just(page));
        when(reactiveWorkspaceRepository.findById(workspaceId)).thenReturn(Mono.just(memberWorkspace));
        when(reactiveWorkspaceMemberRepository.existsByWorkspaceIdAndUserId(workspaceId, userId))
                .thenReturn(Mono.just(true));
        when(reactivePagePermissionRepository.findByPageIdAndUserId(pageId, userId))
                .thenReturn(Mono.just(pagePermission));
        when(reactiveBlockRepository.findByIdAndPageId(parentBlockId, pageId))
                .thenReturn(Mono.just(parentBlock));
        when(reactiveBlockRepository.save(any(Block.class))).thenReturn(Mono.just(block));

        try (MockedStatic<ReactiveSecurityContextHolder> mockedSecurityContext =
                     mockStatic(ReactiveSecurityContextHolder.class)) {

            mockedSecurityContext.when(ReactiveSecurityContextHolder::getContext)
                    .thenReturn(Mono.just(securityContext));

            // when & then
            StepVerifier.create(blockService.createBlock(workspaceIdStr, pageIdStr, request))
                    .expectNextMatches(response -> {
                        assertThat(response.getId()).isNotNull();
                        assertThat(response.getParentBlockId()).isEqualTo(parentBlockIdStr);
                        return true;
                    })
                    .verifyComplete();
        }
    }

    @Test
    @Order(3)
    @DisplayName("블록 생성 실패 - 페이지 없음")
    void createBlock_Fail_PageNotFound() {
        // given
        BlockCreateRequestDto request = BlockCreateRequestDto.builder()
                .type("text")
                .content("New block content")
                .build();

        when(uuidUtils.fromString(workspaceIdStr)).thenReturn(workspaceId);
        when(uuidUtils.fromString(pageIdStr)).thenReturn(pageId);
        when(reactivePageRepository.findByIdAndWorkspaceId(pageId, workspaceId))
                .thenReturn(Mono.empty());

        try (MockedStatic<ReactiveSecurityContextHolder> mockedSecurityContext =
                     mockStatic(ReactiveSecurityContextHolder.class)) {

            mockedSecurityContext.when(ReactiveSecurityContextHolder::getContext)
                    .thenReturn(Mono.just(securityContext));

            // when & then
            StepVerifier.create(blockService.createBlock(workspaceIdStr, pageIdStr, request))
                    .expectError(PageException.class)
                    .verify();
        }
    }

    @Test
    @Order(4)
    @DisplayName("블록 생성 실패 - 편집 권한 없음")
    void createBlock_Fail_NoEditPermission() {
        // given
        BlockCreateRequestDto request = BlockCreateRequestDto.builder()
                .type("text")
                .content("New block content")
                .build();

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
        when(reactivePageRepository.findByIdAndWorkspaceId(pageId, workspaceId))
                .thenReturn(Mono.just(page));
        when(reactiveWorkspaceRepository.findById(workspaceId)).thenReturn(Mono.just(memberWorkspace));
        when(reactiveWorkspaceMemberRepository.existsByWorkspaceIdAndUserId(workspaceId, userId))
                .thenReturn(Mono.just(true));
        when(reactivePagePermissionRepository.findByPageIdAndUserId(pageId, userId))
                .thenReturn(Mono.just(readPermission));

        try (MockedStatic<ReactiveSecurityContextHolder> mockedSecurityContext =
                     mockStatic(ReactiveSecurityContextHolder.class)) {

            mockedSecurityContext.when(ReactiveSecurityContextHolder::getContext)
                    .thenReturn(Mono.just(securityContext));

            // when & then
            StepVerifier.create(blockService.createBlock(workspaceIdStr, pageIdStr, request))
                    .expectError(PagePermissionException.class)
                    .verify();
        }
    }

    @Test
    @Order(5)
    @DisplayName("블록 생성 실패 - 부모 블록 없음")
    void createBlock_Fail_ParentBlockNotFound() {
        // given
        BlockCreateRequestDto request = BlockCreateRequestDto.builder()
                .parentBlockId(parentBlockIdStr)
                .type("text")
                .content("Child block content")
                .build();

        when(uuidUtils.fromString(workspaceIdStr)).thenReturn(workspaceId);
        when(uuidUtils.fromString(pageIdStr)).thenReturn(pageId);
        when(uuidUtils.fromString(parentBlockIdStr)).thenReturn(parentBlockId);
        when(reactivePageRepository.findByIdAndWorkspaceId(pageId, workspaceId))
                .thenReturn(Mono.just(page));
        when(reactiveWorkspaceRepository.findById(workspaceId)).thenReturn(Mono.just(workspace));
        when(reactiveBlockRepository.findByIdAndPageId(parentBlockId, pageId))
                .thenReturn(Mono.empty()); // 부모 블록 없음

        try (MockedStatic<ReactiveSecurityContextHolder> mockedSecurityContext =
                     mockStatic(ReactiveSecurityContextHolder.class)) {

            mockedSecurityContext.when(ReactiveSecurityContextHolder::getContext)
                    .thenReturn(Mono.just(securityContext));

            // when & then
            StepVerifier.create(blockService.createBlock(workspaceIdStr, pageIdStr, request))
                    .expectError(BlockException.class)
                    .verify();
        }
    }

    @Test
    @Order(6)
    @DisplayName("블록 생성 실패 - 워크스페이스 멤버 아님")
    void createBlock_Fail_NotWorkspaceMember() {
        // given
        BlockCreateRequestDto request = BlockCreateRequestDto.builder()
                .type("text")
                .content("New block content")
                .build();

        Workspace memberWorkspace = Workspace.builder()
                .id(workspaceId)
                .name("Test Workspace")
                .createdBy(UUID.randomUUID())
                .build();

        when(uuidUtils.fromString(workspaceIdStr)).thenReturn(workspaceId);
        when(uuidUtils.fromString(pageIdStr)).thenReturn(pageId);
        when(reactivePageRepository.findByIdAndWorkspaceId(pageId, workspaceId))
                .thenReturn(Mono.just(page));
        when(reactiveWorkspaceRepository.findById(workspaceId)).thenReturn(Mono.just(memberWorkspace));
        when(reactiveWorkspaceMemberRepository.existsByWorkspaceIdAndUserId(workspaceId, userId))
                .thenReturn(Mono.just(false));

        try (MockedStatic<ReactiveSecurityContextHolder> mockedSecurityContext =
                     mockStatic(ReactiveSecurityContextHolder.class)) {

            mockedSecurityContext.when(ReactiveSecurityContextHolder::getContext)
                    .thenReturn(Mono.just(securityContext));

            // when & then
            StepVerifier.create(blockService.createBlock(workspaceIdStr, pageIdStr, request))
                    .expectError(WorkspaceMemberException.class)
                    .verify();
        }
    }

    @Test
    @Order(7)
    @DisplayName("블록 목록 조회 성공 - 워크스페이스 소유자")
    void getBlocks_Success_WorkspaceOwner() {
        // given
        Block block1 = Block.builder().id(UUID.randomUUID()).type("text").position(0).build();
        Block block2 = Block.builder().id(UUID.randomUUID()).type("heading").position(1).build();

        when(uuidUtils.fromString(workspaceIdStr)).thenReturn(workspaceId);
        when(uuidUtils.fromString(pageIdStr)).thenReturn(pageId);
        when(uuidUtils.fromUUID(any(UUID.class))).thenAnswer(invocation -> {
            UUID uuid = invocation.getArgument(0);
            return uuid != null ? uuid.toString() : null;
        });
        when(reactivePageRepository.findByIdAndWorkspaceId(pageId, workspaceId))
                .thenReturn(Mono.just(page));
        when(reactiveWorkspaceRepository.findById(workspaceId)).thenReturn(Mono.just(workspace));
        when(reactiveBlockRepository.findAllByPageIdAndIsArchivedFalseOrderByPositionAsc(pageId))
                .thenReturn(Flux.just(block1, block2));

        try (MockedStatic<ReactiveSecurityContextHolder> mockedSecurityContext =
                     mockStatic(ReactiveSecurityContextHolder.class)) {

            mockedSecurityContext.when(ReactiveSecurityContextHolder::getContext)
                    .thenReturn(Mono.just(securityContext));

            // when & then
            StepVerifier.create(blockService.getBlocks(workspaceIdStr, pageIdStr))
                    .expectNextMatches(response -> {
                        assertThat(response.getBlocks()).hasSize(2);
                        assertThat(response.getBlocks().get(0).getType()).isEqualTo("text");
                        assertThat(response.getBlocks().get(1).getType()).isEqualTo("heading");
                        return true;
                    })
                    .verifyComplete();
        }
    }

    @Test
    @Order(8)
    @DisplayName("블록 목록 조회 성공 - 워크스페이스 멤버, 읽기 권한 있음")
    void getBlocks_Success_WorkspaceMember_WithReadPermission() {
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
        when(reactivePageRepository.findByIdAndWorkspaceId(pageId, workspaceId))
                .thenReturn(Mono.just(page));
        when(reactiveWorkspaceRepository.findById(workspaceId)).thenReturn(Mono.just(memberWorkspace));
        when(reactiveWorkspaceMemberRepository.existsByWorkspaceIdAndUserId(workspaceId, userId))
                .thenReturn(Mono.just(true));
        when(reactivePagePermissionRepository.findByPageIdAndUserId(pageId, userId))
                .thenReturn(Mono.just(readPermission));
        when(reactiveBlockRepository.findAllByPageIdAndIsArchivedFalseOrderByPositionAsc(pageId))
                .thenReturn(Flux.empty());

        try (MockedStatic<ReactiveSecurityContextHolder> mockedSecurityContext =
                     mockStatic(ReactiveSecurityContextHolder.class)) {

            mockedSecurityContext.when(ReactiveSecurityContextHolder::getContext)
                    .thenReturn(Mono.just(securityContext));

            // when & then
            StepVerifier.create(blockService.getBlocks(workspaceIdStr, pageIdStr))
                    .expectNextMatches(response -> {
                        assertThat(response.getBlocks()).isEmpty();
                        return true;
                    })
                    .verifyComplete();
        }
    }

    @Test
    @Order(9)
    @DisplayName("블록 목록 조회 성공 - 비멤버, 공개 페이지")
    void getBlocks_Success_NonMember_PublicPage() {
        // given
        UUID otherUserId = UUID.randomUUID();
        Page publicPage = Page.builder()
                .id(pageId)
                .workspaceId(workspaceId)
                .title("Public Page")
                .isPublic(true)
                .createdBy(otherUserId)
                .build();

        Workspace otherWorkspace = Workspace.builder()
                .id(workspaceId)
                .name("Test Workspace")
                .createdBy(otherUserId)
                .build();

        when(uuidUtils.fromString(workspaceIdStr)).thenReturn(workspaceId);
        when(uuidUtils.fromString(pageIdStr)).thenReturn(pageId);
        when(reactivePageRepository.findByIdAndWorkspaceId(pageId, workspaceId))
                .thenReturn(Mono.just(publicPage));
        when(reactiveWorkspaceRepository.findById(workspaceId)).thenReturn(Mono.just(otherWorkspace));
        when(reactiveWorkspaceMemberRepository.existsByWorkspaceIdAndUserId(workspaceId, userId))
                .thenReturn(Mono.just(false));
        when(reactiveBlockRepository.findAllByPageIdAndIsArchivedFalseOrderByPositionAsc(pageId))
                .thenReturn(Flux.empty());

        try (MockedStatic<ReactiveSecurityContextHolder> mockedSecurityContext =
                     mockStatic(ReactiveSecurityContextHolder.class)) {

            mockedSecurityContext.when(ReactiveSecurityContextHolder::getContext)
                    .thenReturn(Mono.just(securityContext));

            // when & then
            StepVerifier.create(blockService.getBlocks(workspaceIdStr, pageIdStr))
                    .expectNextMatches(response -> {
                        assertThat(response.getBlocks()).isEmpty();
                        return true;
                    })
                    .verifyComplete();
        }
    }

    @Test
    @Order(10)
    @DisplayName("블록 목록 조회 실패 - 권한 없음")
    void getBlocks_Fail_PermissionDenied() {
        // given
        Workspace otherWorkspace = Workspace.builder()
                .id(workspaceId)
                .name("Test Workspace")
                .createdBy(UUID.randomUUID())
                .build();

        when(uuidUtils.fromString(workspaceIdStr)).thenReturn(workspaceId);
        when(uuidUtils.fromString(pageIdStr)).thenReturn(pageId);
        when(reactivePageRepository.findByIdAndWorkspaceId(pageId, workspaceId))
                .thenReturn(Mono.just(page)); // isPublic = false
        when(reactiveWorkspaceRepository.findById(workspaceId)).thenReturn(Mono.just(otherWorkspace));
        when(reactiveWorkspaceMemberRepository.existsByWorkspaceIdAndUserId(workspaceId, userId))
                .thenReturn(Mono.just(false));

        try (MockedStatic<ReactiveSecurityContextHolder> mockedSecurityContext =
                     mockStatic(ReactiveSecurityContextHolder.class)) {

            mockedSecurityContext.when(ReactiveSecurityContextHolder::getContext)
                    .thenReturn(Mono.just(securityContext));

            // when & then
            StepVerifier.create(blockService.getBlocks(workspaceIdStr, pageIdStr))
                    .expectError(PagePermissionException.class)
                    .verify();
        }
    }

    @Test
    @Order(11)
    @DisplayName("블록 조회 성공 - 워크스페이스 소유자")
    void getBlock_Success_WorkspaceOwner() {
        // given
        when(uuidUtils.fromString(workspaceIdStr)).thenReturn(workspaceId);
        when(uuidUtils.fromString(pageIdStr)).thenReturn(pageId);
        when(uuidUtils.fromString(blockIdStr)).thenReturn(blockId);
        when(reactiveBlockRepository.findByIdAndPageId(blockId, pageId))
                .thenReturn(Mono.just(block));
        when(reactivePageRepository.findByIdAndWorkspaceId(pageId, workspaceId))
                .thenReturn(Mono.just(page));
        when(reactiveWorkspaceRepository.findById(workspaceId)).thenReturn(Mono.just(workspace));

        try (MockedStatic<ReactiveSecurityContextHolder> mockedSecurityContext =
                     mockStatic(ReactiveSecurityContextHolder.class)) {

            mockedSecurityContext.when(ReactiveSecurityContextHolder::getContext)
                    .thenReturn(Mono.just(securityContext));

            // when & then
            StepVerifier.create(blockService.getBlock(workspaceIdStr, pageIdStr, blockIdStr))
                    .expectNextMatches(response -> {
                        assertThat(response.getId()).isEqualTo(blockIdStr);
                        assertThat(response.getType()).isEqualTo("text");
                        assertThat(response.getContent()).isEqualTo("Test block content");
                        return true;
                    })
                    .verifyComplete();
        }
    }

    @Test
    @Order(12)
    @DisplayName("블록 조회 실패 - 블록 없음")
    void getBlock_Fail_BlockNotFound() {
        // given
        when(uuidUtils.fromString(workspaceIdStr)).thenReturn(workspaceId);
        when(uuidUtils.fromString(pageIdStr)).thenReturn(pageId);
        when(uuidUtils.fromString(blockIdStr)).thenReturn(blockId);
        when(reactiveBlockRepository.findByIdAndPageId(blockId, pageId))
                .thenReturn(Mono.empty()); // 블록 없음

        try (MockedStatic<ReactiveSecurityContextHolder> mockedSecurityContext =
                     mockStatic(ReactiveSecurityContextHolder.class)) {

            mockedSecurityContext.when(ReactiveSecurityContextHolder::getContext)
                    .thenReturn(Mono.just(securityContext));

            // when & then
            StepVerifier.create(blockService.getBlock(workspaceIdStr, pageIdStr, blockIdStr))
                    .expectError(BlockException.class)
                    .verify();
        }
    }

    @Test
    @Order(13)
    @DisplayName("블록 조회 실패 - 페이지 권한 없음")
    void getBlock_Fail_NoPagePermission() {
        // given
        Workspace otherWorkspace = Workspace.builder()
                .id(workspaceId)
                .name("Test Workspace")
                .createdBy(UUID.randomUUID())
                .build();

        when(uuidUtils.fromString(workspaceIdStr)).thenReturn(workspaceId);
        when(uuidUtils.fromString(pageIdStr)).thenReturn(pageId);
        when(uuidUtils.fromString(blockIdStr)).thenReturn(blockId);
        when(reactiveBlockRepository.findByIdAndPageId(blockId, pageId))
                .thenReturn(Mono.just(block));
        when(reactivePageRepository.findByIdAndWorkspaceId(pageId, workspaceId))
                .thenReturn(Mono.just(page)); // isPublic = false
        when(reactiveWorkspaceRepository.findById(workspaceId)).thenReturn(Mono.just(otherWorkspace));
        when(reactiveWorkspaceMemberRepository.existsByWorkspaceIdAndUserId(workspaceId, userId))
                .thenReturn(Mono.just(false));

        try (MockedStatic<ReactiveSecurityContextHolder> mockedSecurityContext =
                     mockStatic(ReactiveSecurityContextHolder.class)) {

            mockedSecurityContext.when(ReactiveSecurityContextHolder::getContext)
                    .thenReturn(Mono.just(securityContext));

            // when & then
            StepVerifier.create(blockService.getBlock(workspaceIdStr, pageIdStr, blockIdStr))
                    .expectError(PagePermissionException.class)
                    .verify();
        }
    }

    @Test
    @Order(14)
    @DisplayName("블록 수정 성공 - 워크스페이스 소유자")
    void updateBlock_Success_WorkspaceOwner() {
        // given
        BlockUpdateRequestDto request = BlockUpdateRequestDto.builder()
                .type("heading")
                .content("Updated content")
                .position(1)
                .build();

        Block updatedBlock = Block.builder()
                .id(blockId)
                .pageId(pageId)
                .parentBlockId(parentBlockId)
                .type("heading")
                .content("Updated content")
                .position(1)
                .isArchived(false)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .createdBy(userId)
                .lastEditedBy(userId)
                .build();

        when(uuidUtils.fromString(workspaceIdStr)).thenReturn(workspaceId);
        when(uuidUtils.fromString(pageIdStr)).thenReturn(pageId);
        when(uuidUtils.fromString(blockIdStr)).thenReturn(blockId);
        when(reactiveBlockRepository.findByIdAndPageId(blockId, pageId))
                .thenReturn(Mono.just(block));
        when(reactivePageRepository.findByIdAndWorkspaceId(pageId, workspaceId))
                .thenReturn(Mono.just(page));
        when(reactiveWorkspaceRepository.findById(workspaceId)).thenReturn(Mono.just(workspace));
        when(reactiveBlockRepository.save(any(Block.class))).thenReturn(Mono.just(updatedBlock));

        try (MockedStatic<ReactiveSecurityContextHolder> mockedSecurityContext =
                     mockStatic(ReactiveSecurityContextHolder.class)) {

            mockedSecurityContext.when(ReactiveSecurityContextHolder::getContext)
                    .thenReturn(Mono.just(securityContext));

            // when & then
            StepVerifier.create(blockService.updateBlock(workspaceIdStr, pageIdStr, blockIdStr, request))
                    .expectNextMatches(response -> {
                        assertThat(response.getId()).isEqualTo(blockIdStr);
                        assertThat(response.getType()).isEqualTo("heading");
                        assertThat(response.getContent()).isEqualTo("Updated content");
                        assertThat(response.getPosition()).isEqualTo(1);
                        return true;
                    })
                    .verifyComplete();
        }

        verify(reactiveBlockRepository).save(any(Block.class));
    }

    @Test
    @Order(15)
    @DisplayName("블록 수정 성공 - 워크스페이스 멤버, 편집 권한 있음")
    void updateBlock_Success_WorkspaceMember_WithEditPermission() {
        // given
        BlockUpdateRequestDto request = BlockUpdateRequestDto.builder()
                .content("Member updated content")
                .build();

        Workspace memberWorkspace = Workspace.builder()
                .id(workspaceId)
                .name("Test Workspace")
                .createdBy(UUID.randomUUID())
                .build();

        when(uuidUtils.fromString(workspaceIdStr)).thenReturn(workspaceId);
        when(uuidUtils.fromString(pageIdStr)).thenReturn(pageId);
        when(uuidUtils.fromString(blockIdStr)).thenReturn(blockId);
        when(reactiveBlockRepository.findByIdAndPageId(blockId, pageId))
                .thenReturn(Mono.just(block));
        when(reactivePageRepository.findByIdAndWorkspaceId(pageId, workspaceId))
                .thenReturn(Mono.just(page));
        when(reactiveWorkspaceRepository.findById(workspaceId)).thenReturn(Mono.just(memberWorkspace));
        when(reactiveWorkspaceMemberRepository.existsByWorkspaceIdAndUserId(workspaceId, userId))
                .thenReturn(Mono.just(true));
        when(reactivePagePermissionRepository.findByPageIdAndUserId(pageId, userId))
                .thenReturn(Mono.just(pagePermission));
        when(reactiveBlockRepository.save(any(Block.class))).thenReturn(Mono.just(block));

        try (MockedStatic<ReactiveSecurityContextHolder> mockedSecurityContext =
                     mockStatic(ReactiveSecurityContextHolder.class)) {

            mockedSecurityContext.when(ReactiveSecurityContextHolder::getContext)
                    .thenReturn(Mono.just(securityContext));

            // when & then
            StepVerifier.create(blockService.updateBlock(workspaceIdStr, pageIdStr, blockIdStr, request))
                    .expectNextCount(1)
                    .verifyComplete();
        }
    }

    @Test
    @Order(16)
    @DisplayName("블록 수정 실패 - 블록 없음")
    void updateBlock_Fail_BlockNotFound() {
        // given
        BlockUpdateRequestDto request = BlockUpdateRequestDto.builder()
                .content("Updated content")
                .build();

        when(uuidUtils.fromString(workspaceIdStr)).thenReturn(workspaceId);
        when(uuidUtils.fromString(pageIdStr)).thenReturn(pageId);
        when(uuidUtils.fromString(blockIdStr)).thenReturn(blockId);
        when(reactiveBlockRepository.findByIdAndPageId(blockId, pageId))
                .thenReturn(Mono.empty());

        try (MockedStatic<ReactiveSecurityContextHolder> mockedSecurityContext =
                     mockStatic(ReactiveSecurityContextHolder.class)) {

            mockedSecurityContext.when(ReactiveSecurityContextHolder::getContext)
                    .thenReturn(Mono.just(securityContext));

            // when & then
            StepVerifier.create(blockService.updateBlock(workspaceIdStr, pageIdStr, blockIdStr, request))
                    .expectError(BlockException.class)
                    .verify();
        }
    }

    @Test
    @Order(17)
    @DisplayName("블록 수정 실패 - 편집 권한 없음")
    void updateBlock_Fail_NoEditPermission() {
        // given
        BlockUpdateRequestDto request = BlockUpdateRequestDto.builder()
                .content("Updated content")
                .build();

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
        when(uuidUtils.fromString(blockIdStr)).thenReturn(blockId);
        when(reactiveBlockRepository.findByIdAndPageId(blockId, pageId))
                .thenReturn(Mono.just(block));
        when(reactivePageRepository.findByIdAndWorkspaceId(pageId, workspaceId))
                .thenReturn(Mono.just(page));
        when(reactiveWorkspaceRepository.findById(workspaceId)).thenReturn(Mono.just(memberWorkspace));
        when(reactiveWorkspaceMemberRepository.existsByWorkspaceIdAndUserId(workspaceId, userId))
                .thenReturn(Mono.just(true));
        when(reactivePagePermissionRepository.findByPageIdAndUserId(pageId, userId))
                .thenReturn(Mono.just(readPermission));

        try (MockedStatic<ReactiveSecurityContextHolder> mockedSecurityContext =
                     mockStatic(ReactiveSecurityContextHolder.class)) {

            mockedSecurityContext.when(ReactiveSecurityContextHolder::getContext)
                    .thenReturn(Mono.just(securityContext));

            // when & then
            StepVerifier.create(blockService.updateBlock(workspaceIdStr, pageIdStr, blockIdStr, request))
                    .expectError(PagePermissionException.class)
                    .verify();
        }
    }

    @Test
    @Order(18)
    @DisplayName("블록 이동 성공 - 새 부모 블록으로 이동")
    void moveBlock_Success_ToNewParent() {
        // given
        UUID newParentBlockId = UUID.randomUUID();
        String newParentBlockIdStr = newParentBlockId.toString();

        BlockMoveRequestDto request = BlockMoveRequestDto.builder()
                .newParentBlockId(newParentBlockIdStr)
                .newPosition(2)
                .build();

        Block newParentBlock = Block.builder()
                .id(newParentBlockId)
                .pageId(pageId)
                .type("container")
                .build();

        when(uuidUtils.fromString(workspaceIdStr)).thenReturn(workspaceId);
        when(uuidUtils.fromString(pageIdStr)).thenReturn(pageId);
        when(uuidUtils.fromString(blockIdStr)).thenReturn(blockId);
        when(uuidUtils.fromString(newParentBlockIdStr)).thenReturn(newParentBlockId);
        when(reactiveBlockRepository.findByIdAndPageId(blockId, pageId))
                .thenReturn(Mono.just(block));
        when(reactivePageRepository.findByIdAndWorkspaceId(pageId, workspaceId))
                .thenReturn(Mono.just(page));
        when(reactiveWorkspaceRepository.findById(workspaceId)).thenReturn(Mono.just(workspace));
        when(reactiveBlockRepository.findByIdAndPageId(newParentBlockId, pageId))
                .thenReturn(Mono.just(newParentBlock));
        when(reactiveBlockRepository.save(any(Block.class))).thenReturn(Mono.just(block));

        try (MockedStatic<ReactiveSecurityContextHolder> mockedSecurityContext =
                     mockStatic(ReactiveSecurityContextHolder.class)) {

            mockedSecurityContext.when(ReactiveSecurityContextHolder::getContext)
                    .thenReturn(Mono.just(securityContext));

            // when & then
            StepVerifier.create(blockService.moveBlock(workspaceIdStr, pageIdStr, blockIdStr, request))
                    .expectNextCount(1)
                    .verifyComplete();
        }

        verify(reactiveBlockRepository).save(any(Block.class));
    }

    @Test
    @Order(19)
    @DisplayName("블록 이동 실패 - 자기 자신을 부모로 설정")
    void moveBlock_Fail_SelfParent() {
        // given
        BlockMoveRequestDto request = BlockMoveRequestDto.builder()
                .newParentBlockId(blockIdStr) // 자기 자신을 부모로 설정
                .newPosition(1)
                .build();

        when(uuidUtils.fromString(workspaceIdStr)).thenReturn(workspaceId);
        when(uuidUtils.fromString(pageIdStr)).thenReturn(pageId);
        when(uuidUtils.fromString(blockIdStr)).thenReturn(blockId);
        when(reactiveBlockRepository.findByIdAndPageId(blockId, pageId))
                .thenReturn(Mono.just(block));
        when(reactivePageRepository.findByIdAndWorkspaceId(pageId, workspaceId))
                .thenReturn(Mono.just(page));
        when(reactiveWorkspaceRepository.findById(workspaceId)).thenReturn(Mono.just(workspace));

        try (MockedStatic<ReactiveSecurityContextHolder> mockedSecurityContext =
                     mockStatic(ReactiveSecurityContextHolder.class)) {

            mockedSecurityContext.when(ReactiveSecurityContextHolder::getContext)
                    .thenReturn(Mono.just(securityContext));

            // when & then
            StepVerifier.create(blockService.moveBlock(workspaceIdStr, pageIdStr, blockIdStr, request))
                    .expectError(BlockException.class)
                    .verify();
        }
    }

    @Test
    @Order(20)
    @DisplayName("블록 이동 실패 - 새 부모 블록이 존재하지 않음")
    void moveBlock_Fail_NewParentBlockNotFound() {
        // given
        UUID nonExistentParentId = UUID.randomUUID();
        String nonExistentParentIdStr = nonExistentParentId.toString();

        BlockMoveRequestDto request = BlockMoveRequestDto.builder()
                .newParentBlockId(nonExistentParentIdStr)
                .newPosition(1)
                .build();

        when(uuidUtils.fromString(workspaceIdStr)).thenReturn(workspaceId);
        when(uuidUtils.fromString(pageIdStr)).thenReturn(pageId);
        when(uuidUtils.fromString(blockIdStr)).thenReturn(blockId);
        when(uuidUtils.fromString(nonExistentParentIdStr)).thenReturn(nonExistentParentId);
        when(reactiveBlockRepository.findByIdAndPageId(blockId, pageId))
                .thenReturn(Mono.just(block));
        when(reactivePageRepository.findByIdAndWorkspaceId(pageId, workspaceId))
                .thenReturn(Mono.just(page));
        when(reactiveWorkspaceRepository.findById(workspaceId)).thenReturn(Mono.just(workspace));
        when(reactiveBlockRepository.findByIdAndPageId(nonExistentParentId, pageId))
                .thenReturn(Mono.empty());

        try (MockedStatic<ReactiveSecurityContextHolder> mockedSecurityContext =
                     mockStatic(ReactiveSecurityContextHolder.class)) {

            mockedSecurityContext.when(ReactiveSecurityContextHolder::getContext)
                    .thenReturn(Mono.just(securityContext));

            // when & then
            StepVerifier.create(blockService.moveBlock(workspaceIdStr, pageIdStr, blockIdStr, request))
                    .expectError(BlockException.class)
                    .verify();
        }
    }

    @Test
    @Order(21)
    @DisplayName("블록 보관 성공 - 워크스페이스 소유자")
    void archiveBlock_Success_WorkspaceOwner() {
        // given
        when(uuidUtils.fromString(workspaceIdStr)).thenReturn(workspaceId);
        when(uuidUtils.fromString(pageIdStr)).thenReturn(pageId);
        when(uuidUtils.fromString(blockIdStr)).thenReturn(blockId);
        when(reactiveBlockRepository.findByIdAndPageId(blockId, pageId))
                .thenReturn(Mono.just(block));
        when(reactivePageRepository.findByIdAndWorkspaceId(pageId, workspaceId))
                .thenReturn(Mono.just(page));
        when(reactiveWorkspaceRepository.findById(workspaceId)).thenReturn(Mono.just(workspace));
        when(reactiveBlockRepository.updateArchiveStatusForBlockTree(blockId, true, userId))
                .thenReturn(Mono.empty());

        try (MockedStatic<ReactiveSecurityContextHolder> mockedSecurityContext =
                     mockStatic(ReactiveSecurityContextHolder.class)) {

            mockedSecurityContext.when(ReactiveSecurityContextHolder::getContext)
                    .thenReturn(Mono.just(securityContext));

            // when & then
            StepVerifier.create(blockService.archiveBlock(workspaceIdStr, pageIdStr, blockIdStr))
                    .expectNextMatches(response -> {
                        assertThat(response.getBlockId()).isEqualTo(blockIdStr);
                        assertThat(response.getIsArchived()).isTrue();
                        return true;
                    })
                    .verifyComplete();
        }

        verify(reactiveBlockRepository).updateArchiveStatusForBlockTree(blockId, true, userId);
    }

    @Test
    @Order(22)
    @DisplayName("블록 보관 성공 - 워크스페이스 멤버, 편집 권한 있음")
    void archiveBlock_Success_WorkspaceMember_WithEditPermission() {
        // given
        Workspace memberWorkspace = Workspace.builder()
                .id(workspaceId)
                .name("Test Workspace")
                .createdBy(UUID.randomUUID())
                .build();

        when(uuidUtils.fromString(workspaceIdStr)).thenReturn(workspaceId);
        when(uuidUtils.fromString(pageIdStr)).thenReturn(pageId);
        when(uuidUtils.fromString(blockIdStr)).thenReturn(blockId);
        when(reactiveBlockRepository.findByIdAndPageId(blockId, pageId))
                .thenReturn(Mono.just(block));
        when(reactivePageRepository.findByIdAndWorkspaceId(pageId, workspaceId))
                .thenReturn(Mono.just(page));
        when(reactiveWorkspaceRepository.findById(workspaceId)).thenReturn(Mono.just(memberWorkspace));
        when(reactiveWorkspaceMemberRepository.existsByWorkspaceIdAndUserId(workspaceId, userId))
                .thenReturn(Mono.just(true));
        when(reactivePagePermissionRepository.findByPageIdAndUserId(pageId, userId))
                .thenReturn(Mono.just(pagePermission));
        when(reactiveBlockRepository.updateArchiveStatusForBlockTree(blockId, true, userId))
                .thenReturn(Mono.empty());

        try (MockedStatic<ReactiveSecurityContextHolder> mockedSecurityContext =
                     mockStatic(ReactiveSecurityContextHolder.class)) {

            mockedSecurityContext.when(ReactiveSecurityContextHolder::getContext)
                    .thenReturn(Mono.just(securityContext));

            // when & then
            StepVerifier.create(blockService.archiveBlock(workspaceIdStr, pageIdStr, blockIdStr))
                    .expectNextMatches(response -> {
                        assertThat(response.getBlockId()).isEqualTo(blockIdStr);
                        assertThat(response.getIsArchived()).isTrue();
                        return true;
                    })
                    .verifyComplete();
        }
    }

    @Test
    @Order(23)
    @DisplayName("블록 보관 실패 - 편집 권한 없음")
    void archiveBlock_Fail_NoEditPermission() {
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
        when(uuidUtils.fromString(blockIdStr)).thenReturn(blockId);
        when(reactiveBlockRepository.findByIdAndPageId(blockId, pageId))
                .thenReturn(Mono.just(block));
        when(reactivePageRepository.findByIdAndWorkspaceId(pageId, workspaceId))
                .thenReturn(Mono.just(page));
        when(reactiveWorkspaceRepository.findById(workspaceId)).thenReturn(Mono.just(memberWorkspace));
        when(reactiveWorkspaceMemberRepository.existsByWorkspaceIdAndUserId(workspaceId, userId))
                .thenReturn(Mono.just(true));
        when(reactivePagePermissionRepository.findByPageIdAndUserId(pageId, userId))
                .thenReturn(Mono.just(readPermission));

        try (MockedStatic<ReactiveSecurityContextHolder> mockedSecurityContext =
                     mockStatic(ReactiveSecurityContextHolder.class)) {

            mockedSecurityContext.when(ReactiveSecurityContextHolder::getContext)
                    .thenReturn(Mono.just(securityContext));

            // when & then
            StepVerifier.create(blockService.archiveBlock(workspaceIdStr, pageIdStr, blockIdStr))
                    .expectError(PagePermissionException.class)
                    .verify();
        }
    }

    @Test
    @Order(24)
    @DisplayName("블록 복원 성공 - 워크스페이스 소유자")
    void restoreBlock_Success_WorkspaceOwner() {
        // given
        Block archivedBlock = Block.builder()
                .id(blockId)
                .pageId(pageId)
                .isArchived(true)
                .createdBy(userId)
                .build();

        when(uuidUtils.fromString(workspaceIdStr)).thenReturn(workspaceId);
        when(uuidUtils.fromString(pageIdStr)).thenReturn(pageId);
        when(uuidUtils.fromString(blockIdStr)).thenReturn(blockId);
        when(reactiveBlockRepository.findByIdAndPageId(blockId, pageId))
                .thenReturn(Mono.just(archivedBlock));
        when(reactivePageRepository.findByIdAndWorkspaceId(pageId, workspaceId))
                .thenReturn(Mono.just(page));
        when(reactiveWorkspaceRepository.findById(workspaceId)).thenReturn(Mono.just(workspace));
        when(reactiveBlockRepository.updateArchiveStatusForBlockTree(blockId, false, userId))
                .thenReturn(Mono.empty());

        try (MockedStatic<ReactiveSecurityContextHolder> mockedSecurityContext =
                     mockStatic(ReactiveSecurityContextHolder.class)) {

            mockedSecurityContext.when(ReactiveSecurityContextHolder::getContext)
                    .thenReturn(Mono.just(securityContext));

            // when & then
            StepVerifier.create(blockService.restoreBlock(workspaceIdStr, pageIdStr, blockIdStr))
                    .expectNextMatches(response -> {
                        assertThat(response.getBlockId()).isEqualTo(blockIdStr);
                        assertThat(response.getIsArchived()).isFalse();
                        return true;
                    })
                    .verifyComplete();
        }

        verify(reactiveBlockRepository).updateArchiveStatusForBlockTree(blockId, false, userId);
    }

    @Test
    @Order(25)
    @DisplayName("블록 복원 성공 - 워크스페이스 멤버, 편집 권한 있음")
    void restoreBlock_Success_WorkspaceMember_WithEditPermission() {
        // given
        Block archivedBlock = Block.builder()
                .id(blockId)
                .pageId(pageId)
                .isArchived(true)
                .createdBy(userId)
                .build();

        Workspace memberWorkspace = Workspace.builder()
                .id(workspaceId)
                .name("Test Workspace")
                .createdBy(UUID.randomUUID())
                .build();

        when(uuidUtils.fromString(workspaceIdStr)).thenReturn(workspaceId);
        when(uuidUtils.fromString(pageIdStr)).thenReturn(pageId);
        when(uuidUtils.fromString(blockIdStr)).thenReturn(blockId);
        when(reactiveBlockRepository.findByIdAndPageId(blockId, pageId))
                .thenReturn(Mono.just(archivedBlock));
        when(reactivePageRepository.findByIdAndWorkspaceId(pageId, workspaceId))
                .thenReturn(Mono.just(page));
        when(reactiveWorkspaceRepository.findById(workspaceId)).thenReturn(Mono.just(memberWorkspace));
        when(reactiveWorkspaceMemberRepository.existsByWorkspaceIdAndUserId(workspaceId, userId))
                .thenReturn(Mono.just(true));
        when(reactivePagePermissionRepository.findByPageIdAndUserId(pageId, userId))
                .thenReturn(Mono.just(pagePermission));
        when(reactiveBlockRepository.updateArchiveStatusForBlockTree(blockId, false, userId))
                .thenReturn(Mono.empty());

        try (MockedStatic<ReactiveSecurityContextHolder> mockedSecurityContext =
                     mockStatic(ReactiveSecurityContextHolder.class)) {

            mockedSecurityContext.when(ReactiveSecurityContextHolder::getContext)
                    .thenReturn(Mono.just(securityContext));

            // when & then
            StepVerifier.create(blockService.restoreBlock(workspaceIdStr, pageIdStr, blockIdStr))
                    .expectNextMatches(response -> {
                        assertThat(response.getBlockId()).isEqualTo(blockIdStr);
                        assertThat(response.getIsArchived()).isFalse();
                        return true;
                    })
                    .verifyComplete();
        }
    }

    @Test
    @Order(26)
    @DisplayName("블록 복원 실패 - 블록 없음")
    void restoreBlock_Fail_BlockNotFound() {
        // given
        when(uuidUtils.fromString(workspaceIdStr)).thenReturn(workspaceId);
        when(uuidUtils.fromString(pageIdStr)).thenReturn(pageId);
        when(uuidUtils.fromString(blockIdStr)).thenReturn(blockId);
        when(reactiveBlockRepository.findByIdAndPageId(blockId, pageId))
                .thenReturn(Mono.empty());

        try (MockedStatic<ReactiveSecurityContextHolder> mockedSecurityContext =
                     mockStatic(ReactiveSecurityContextHolder.class)) {

            mockedSecurityContext.when(ReactiveSecurityContextHolder::getContext)
                    .thenReturn(Mono.just(securityContext));

            // when & then
            StepVerifier.create(blockService.restoreBlock(workspaceIdStr, pageIdStr, blockIdStr))
                    .expectError(BlockException.class)
                    .verify();
        }
    }

    @Test
    @Order(27)
    @DisplayName("블록 복원 실패 - 워크스페이스 멤버 아님")
    void restoreBlock_Fail_NotWorkspaceMember() {
        // given
        Block archivedBlock = Block.builder()
                .id(blockId)
                .pageId(pageId)
                .isArchived(true)
                .createdBy(userId)
                .build();

        Workspace memberWorkspace = Workspace.builder()
                .id(workspaceId)
                .name("Test Workspace")
                .createdBy(UUID.randomUUID())
                .build();

        when(uuidUtils.fromString(workspaceIdStr)).thenReturn(workspaceId);
        when(uuidUtils.fromString(pageIdStr)).thenReturn(pageId);
        when(uuidUtils.fromString(blockIdStr)).thenReturn(blockId);
        when(reactiveBlockRepository.findByIdAndPageId(blockId, pageId))
                .thenReturn(Mono.just(archivedBlock));
        when(reactivePageRepository.findByIdAndWorkspaceId(pageId, workspaceId))
                .thenReturn(Mono.just(page));
        when(reactiveWorkspaceRepository.findById(workspaceId)).thenReturn(Mono.just(memberWorkspace));
        when(reactiveWorkspaceMemberRepository.existsByWorkspaceIdAndUserId(workspaceId, userId))
                .thenReturn(Mono.just(false));

        try (MockedStatic<ReactiveSecurityContextHolder> mockedSecurityContext =
                     mockStatic(ReactiveSecurityContextHolder.class)) {

            mockedSecurityContext.when(ReactiveSecurityContextHolder::getContext)
                    .thenReturn(Mono.just(securityContext));

            // when & then
            StepVerifier.create(blockService.restoreBlock(workspaceIdStr, pageIdStr, blockIdStr))
                    .expectError(WorkspaceMemberException.class)
                    .verify();
        }
    }

    @Test
    @Order(28)
    @DisplayName("블록 복원 실패 - 편집 권한 없음")
    void restoreBlock_Fail_NoEditPermission() {
        // given
        Block archivedBlock = Block.builder()
                .id(blockId)
                .pageId(pageId)
                .isArchived(true)
                .createdBy(userId)
                .build();

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
        when(uuidUtils.fromString(blockIdStr)).thenReturn(blockId);
        when(reactiveBlockRepository.findByIdAndPageId(blockId, pageId))
                .thenReturn(Mono.just(archivedBlock));
        when(reactivePageRepository.findByIdAndWorkspaceId(pageId, workspaceId))
                .thenReturn(Mono.just(page));
        when(reactiveWorkspaceRepository.findById(workspaceId)).thenReturn(Mono.just(memberWorkspace));
        when(reactiveWorkspaceMemberRepository.existsByWorkspaceIdAndUserId(workspaceId, userId))
                .thenReturn(Mono.just(true));
        when(reactivePagePermissionRepository.findByPageIdAndUserId(pageId, userId))
                .thenReturn(Mono.just(readPermission));

        try (MockedStatic<ReactiveSecurityContextHolder> mockedSecurityContext =
                     mockStatic(ReactiveSecurityContextHolder.class)) {

            mockedSecurityContext.when(ReactiveSecurityContextHolder::getContext)
                    .thenReturn(Mono.just(securityContext));

            // when & then
            StepVerifier.create(blockService.restoreBlock(workspaceIdStr, pageIdStr, blockIdStr))
                    .expectError(PagePermissionException.class)
                    .verify();
        }
    }
}
