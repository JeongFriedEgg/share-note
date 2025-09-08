package com.example.share_note.integration;

import com.example.share_note.domain.Block;
import com.example.share_note.domain.Page;
import com.example.share_note.domain.PagePermission;
import com.example.share_note.domain.Workspace;
import com.example.share_note.dto.CustomUserDetails;
import com.example.share_note.dto.block.BlockCreateRequestDto;
import com.example.share_note.dto.block.BlockMoveRequestDto;
import com.example.share_note.dto.block.BlockUpdateRequestDto;
import com.example.share_note.enums.PagePermissionType;
import com.example.share_note.exception.ErrorCode;
import com.example.share_note.repository.*;
import com.example.share_note.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
public class BlockIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private ReactiveBlockRepository reactiveBlockRepository;

    @MockBean
    private ReactivePageRepository reactivePageRepository;

    @MockBean
    private ReactivePagePermissionRepository reactivePagePermissionRepository;

    @MockBean
    private ReactiveWorkspaceRepository reactiveWorkspaceRepository;

    @MockBean
    private ReactiveWorkspaceMemberRepository reactiveWorkspaceMemberRepository;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    private final String VALID_TOKEN = "Bearer validAccessToken";

    private CustomUserDetails ownerDetails;
    private CustomUserDetails memberDetails;
    private CustomUserDetails nonMemberDetails;

    private Workspace workspace;
    private Page page;
    private Page publicPage;
    private Block rootBlock;
    private Block childBlock;
    private Block anotherBlock;
    private PagePermission editPermission;
    private PagePermission readOnlyPermission;

    private Authentication ownerAuth;
    private Authentication memberAuth;
    private Authentication nonMemberAuth;

    @BeforeEach
    void setUp() {
        ownerDetails = new CustomUserDetails(1L, "owner", "password", "ROLE_USER", "owner@example.com");
        memberDetails = new CustomUserDetails(2L, "member", "password", "ROLE_USER", "member@example.com");
        nonMemberDetails = new CustomUserDetails(99L, "nonmember", "password", "ROLE_USER", "nonmember@example.com");

        ownerAuth = new UsernamePasswordAuthenticationToken(ownerDetails, null,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")));
        memberAuth = new UsernamePasswordAuthenticationToken(memberDetails, null,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")));
        nonMemberAuth = new UsernamePasswordAuthenticationToken(nonMemberDetails, null,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")));

        workspace = Workspace.builder().id(1L).createdBy(1L).build();
        page = Page.builder().id(2L).workspaceId(1L).isPublic(false).createdBy(1L).build();
        publicPage = Page.builder().id(3L).workspaceId(1L).isPublic(true).createdBy(1L).build();
        rootBlock = Block.builder().id(4L).pageId(2L).parentBlockId(null).position(0).isArchived(false).build();
        childBlock = Block.builder().id(5L).pageId(2L).parentBlockId(4L).position(1).isArchived(false).build();
        anotherBlock = Block.builder().id(6L).pageId(2L).parentBlockId(null).position(1).isArchived(false).build();

        editPermission = PagePermission.builder().id(1L).pageId(2L).userId(2L).permission(PagePermissionType.EDIT.name()).build();
        readOnlyPermission = PagePermission.builder().id(2L).pageId(2L).userId(2L).permission(PagePermissionType.READ.name()).build();

        when(jwtTokenProvider.validateToken(anyString())).thenReturn(true);
    }

    @Test
    @DisplayName("블록 생성 성공 - 워크스페이스 소유자")
    void createBlock_success_asOwner() {
        // given
        BlockCreateRequestDto request = BlockCreateRequestDto.builder().type("text").content("New Block").build();
        Block savedBlock = Block.builder().id(7L).pageId(2L).type("text").content("New Block").createdBy(1L).build();

        when(jwtTokenProvider.getAuthentication(anyString())).thenReturn(ownerAuth);
        when(reactivePageRepository.findByIdAndWorkspaceId(2L, 1L)).thenReturn(Mono.just(page));
        when(reactiveWorkspaceRepository.findById(1L)).thenReturn(Mono.just(workspace));
        when(reactiveBlockRepository.save(any(Block.class))).thenReturn(Mono.just(savedBlock));

        // when & then
        webTestClient.post()
                .uri("/api/workspaces/{workspaceId}/pages/{pageId}/blocks", 1L, 2L)
                .header("Authorization", VALID_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.id").isEqualTo(7L);
    }

    @Test
    @DisplayName("블록 생성 실패 - 편집 권한이 없는 멤버")
    void createBlock_failure_noEditPermission() {
        // given
        BlockCreateRequestDto request = BlockCreateRequestDto.builder().type("text").content("New Block").build();
        Workspace workspaceOwnedByOthers = Workspace.builder().id(1L).createdBy(3L).build();

        when(jwtTokenProvider.getAuthentication(anyString())).thenReturn(memberAuth);
        when(reactivePageRepository.findByIdAndWorkspaceId(2L, 1L)).thenReturn(Mono.just(page));
        when(reactiveWorkspaceRepository.findById(1L)).thenReturn(Mono.just(workspaceOwnedByOthers));
        when(reactiveWorkspaceMemberRepository.existsByWorkspaceIdAndUserId(1L, 2L)).thenReturn(Mono.just(true));
        when(reactivePagePermissionRepository.findByPageIdAndUserId(2L, 2L)).thenReturn(Mono.just(readOnlyPermission));

        // when & then
        webTestClient.post()
                .uri("/api/workspaces/{workspaceId}/pages/{pageId}/blocks", 1L, 2L)
                .header("Authorization", VALID_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.message").isEqualTo(ErrorCode.PAGE_PERMISSION_DENIED.getMessage());
    }

    @Test
    @DisplayName("블록 목록 조회 성공 - 읽기 권한이 있는 멤버")
    void getBlocks_success_asMemberWithReadPermission() {
        // given
        List<Block> blocks = List.of(rootBlock, childBlock);
        Workspace workspaceOwnedByOthers = Workspace.builder().id(1L).createdBy(3L).build();

        when(jwtTokenProvider.getAuthentication(anyString())).thenReturn(memberAuth);
        when(reactivePageRepository.findByIdAndWorkspaceId(2L, 1L)).thenReturn(Mono.just(page));
        when(reactiveWorkspaceRepository.findById(1L)).thenReturn(Mono.just(workspaceOwnedByOthers));
        when(reactiveWorkspaceMemberRepository.existsByWorkspaceIdAndUserId(1L, 2L)).thenReturn(Mono.just(true));
        when(reactivePagePermissionRepository.findByPageIdAndUserId(2L, 2L)).thenReturn(Mono.just(readOnlyPermission));
        when(reactiveBlockRepository.findAllByPageIdAndIsArchivedFalseOrderByPositionAsc(2L)).thenReturn(Flux.fromIterable(blocks));

        // when & then
        webTestClient.get()
                .uri("/api/workspaces/{workspaceId}/pages/{pageId}/blocks", 1L, 2L)
                .header("Authorization", VALID_TOKEN)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.blocks").isArray()
                .jsonPath("$.blocks.length()").isEqualTo(2);
    }

    @Test
    @DisplayName("블록 목록 조회 실패 - 비공개 페이지에 대한 권한 없음")
    void getBlocks_failure_noPermissionPrivatePage() {
        // given
        Workspace workspaceOwnedByOthers = Workspace.builder().id(1L).createdBy(3L).build();

        when(jwtTokenProvider.getAuthentication(anyString())).thenReturn(nonMemberAuth);
        when(reactivePageRepository.findByIdAndWorkspaceId(2L, 1L)).thenReturn(Mono.just(page));
        when(reactiveWorkspaceRepository.findById(1L)).thenReturn(Mono.just(workspaceOwnedByOthers));
        when(reactiveWorkspaceMemberRepository.existsByWorkspaceIdAndUserId(1L, 99L)).thenReturn(Mono.just(false));

        // when & then
        webTestClient.get()
                .uri("/api/workspaces/{workspaceId}/pages/{pageId}/blocks", 1L, 2L)
                .header("Authorization", VALID_TOKEN)
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.message").isEqualTo(ErrorCode.PAGE_PERMISSION_DENIED.getMessage());
    }

    @Test
    @DisplayName("단일 블록 조회 성공 - 공개 페이지에 비멤버가 접근")
    void getBlock_success_asNonMember_onPublicPage() {
        // given
        Block publicBlock = Block.builder().id(7L).pageId(3L).isArchived(false).build();
        when(jwtTokenProvider.getAuthentication(anyString())).thenReturn(nonMemberAuth);
        when(reactiveBlockRepository.findByIdAndPageId(7L, 3L)).thenReturn(Mono.just(publicBlock));
        when(reactivePageRepository.findByIdAndWorkspaceId(3L, 1L)).thenReturn(Mono.just(publicPage));
        when(reactiveWorkspaceRepository.findById(1L)).thenReturn(Mono.just(workspace));
        when(reactiveWorkspaceMemberRepository.existsByWorkspaceIdAndUserId(1L, 99L)).thenReturn(Mono.just(false));

        // when & then
        webTestClient.get()
                .uri("/api/workspaces/{workspaceId}/pages/{pageId}/blocks/{blockId}", 1L, 3L, 7L)
                .header("Authorization", VALID_TOKEN)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo(7L);
    }

    @Test
    @DisplayName("블록 수정 성공 - 편집 권한이 있는 멤버")
    void updateBlock_success_asMemberWithEditPermission() {
        // given
        BlockUpdateRequestDto request = BlockUpdateRequestDto.builder().content("Updated Content").build();
        Block updatedBlock = Block.builder().id(4L).content("Updated Content").build();
        Workspace workspaceOwnedByOthers = Workspace.builder().id(1L).createdBy(3L).build();

        when(jwtTokenProvider.getAuthentication(anyString())).thenReturn(memberAuth);
        when(reactiveBlockRepository.findByIdAndPageId(4L, 2L)).thenReturn(Mono.just(rootBlock));
        when(reactivePageRepository.findByIdAndWorkspaceId(2L, 1L)).thenReturn(Mono.just(page));
        when(reactiveWorkspaceRepository.findById(1L)).thenReturn(Mono.just(workspaceOwnedByOthers));
        when(reactiveWorkspaceMemberRepository.existsByWorkspaceIdAndUserId(1L, 2L)).thenReturn(Mono.just(true));
        when(reactivePagePermissionRepository.findByPageIdAndUserId(2L, 2L)).thenReturn(Mono.just(editPermission));
        when(reactiveBlockRepository.save(any(Block.class))).thenReturn(Mono.just(updatedBlock));

        // when & then
        webTestClient.put()
                .uri("/api/workspaces/{workspaceId}/pages/{pageId}/blocks/{blockId}", 1L, 2L, 4L)
                .header("Authorization", VALID_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.content").isEqualTo("Updated Content");
    }

    @Test
    @DisplayName("블록 이동 성공 - 워크스페이스 소유자")
    void moveBlock_success_asOwner() {
        // given
        BlockMoveRequestDto request = BlockMoveRequestDto.builder().newParentBlockId(6L).newPosition(0).build();
        Block movedBlock = Block.builder().id(4L).parentBlockId(6L).position(0).build();

        when(jwtTokenProvider.getAuthentication(anyString())).thenReturn(ownerAuth);
        when(reactiveBlockRepository.findByIdAndPageId(4L, 2L)).thenReturn(Mono.just(rootBlock));
        when(reactivePageRepository.findByIdAndWorkspaceId(2L, 1L)).thenReturn(Mono.just(page));
        when(reactiveWorkspaceRepository.findById(1L)).thenReturn(Mono.just(workspace));
        when(reactiveBlockRepository.findByIdAndPageId(6L, 2L)).thenReturn(Mono.just(anotherBlock));
        when(reactiveBlockRepository.save(any(Block.class))).thenReturn(Mono.just(movedBlock));

        // when & then
        webTestClient.put()
                .uri("/api/workspaces/{workspaceId}/pages/{pageId}/blocks/{blockId}/move", 1L, 2L, 4L)
                .header("Authorization", VALID_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.parentBlockId").isEqualTo(6L)
                .jsonPath("$.position").isEqualTo(0);
    }

    @Test
    @DisplayName("블록 이동 실패 - 부모 블록이 존재하지 않음")
    void moveBlock_failure_parentBlockNotFound() {
        // given
        BlockMoveRequestDto request = BlockMoveRequestDto.builder().newParentBlockId(99L).newPosition(0).build();

        when(jwtTokenProvider.getAuthentication(anyString())).thenReturn(ownerAuth);
        when(reactiveBlockRepository.findByIdAndPageId(4L, 2L)).thenReturn(Mono.just(rootBlock));
        when(reactivePageRepository.findByIdAndWorkspaceId(2L, 1L)).thenReturn(Mono.just(page));
        when(reactiveWorkspaceRepository.findById(1L)).thenReturn(Mono.just(workspace));
        when(reactiveBlockRepository.findByIdAndPageId(99L, 2L)).thenReturn(Mono.empty());

        // when & then
        webTestClient.put()
                .uri("/api/workspaces/{workspaceId}/pages/{pageId}/blocks/{blockId}/move", 1L, 2L, 4L)
                .header("Authorization", VALID_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.message").isEqualTo(ErrorCode.PARENT_BLOCK_NOT_FOUND.getMessage());
    }

    @Test
    @DisplayName("블록 보관 성공 - 워크스페이스 소유자")
    void archiveBlock_success_asOwner() {
        // given
        when(jwtTokenProvider.getAuthentication(anyString())).thenReturn(ownerAuth);
        when(reactiveBlockRepository.findByIdAndPageId(4L, 2L)).thenReturn(Mono.just(rootBlock));
        when(reactivePageRepository.findByIdAndWorkspaceId(2L, 1L)).thenReturn(Mono.just(page));
        when(reactiveWorkspaceRepository.findById(1L)).thenReturn(Mono.just(workspace));
        when(reactiveBlockRepository.updateArchiveStatusForBlockTree(anyLong(), anyBoolean(), anyLong())).thenReturn(Mono.just(1));

        // when & then
        webTestClient.put()
                .uri("/api/workspaces/{workspaceId}/pages/{pageId}/blocks/{blockId}/archive", 1L, 2L, 4L)
                .header("Authorization", VALID_TOKEN)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.blockId").isEqualTo(4L)
                .jsonPath("$.isArchived").isEqualTo(true);
    }

    @Test
    @DisplayName("블록 복원 성공 - 편집 권한이 있는 멤버")
    void restoreBlock_success_asMemberWithEditPermission() {
        // given
        rootBlock.setArchived(true);
        Workspace workspaceOwnedByOthers = Workspace.builder().id(1L).createdBy(3L).build();

        when(jwtTokenProvider.getAuthentication(anyString())).thenReturn(memberAuth);
        when(reactiveBlockRepository.findByIdAndPageId(4L, 2L)).thenReturn(Mono.just(rootBlock));
        when(reactivePageRepository.findByIdAndWorkspaceId(2L, 1L)).thenReturn(Mono.just(page));
        when(reactiveWorkspaceRepository.findById(1L)).thenReturn(Mono.just(workspaceOwnedByOthers));
        when(reactiveWorkspaceMemberRepository.existsByWorkspaceIdAndUserId(1L, 2L)).thenReturn(Mono.just(true));
        when(reactivePagePermissionRepository.findByPageIdAndUserId(2L, 2L)).thenReturn(Mono.just(editPermission));
        when(reactiveBlockRepository.updateArchiveStatusForBlockTree(anyLong(), anyBoolean(), anyLong())).thenReturn(Mono.just(1));

        // when & then
        webTestClient.put()
                .uri("/api/workspaces/{workspaceId}/pages/{pageId}/blocks/{blockId}/restore", 1L, 2L, 4L)
                .header("Authorization", VALID_TOKEN)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.blockId").isEqualTo(4L)
                .jsonPath("$.isArchived").isEqualTo(false);
    }

}
