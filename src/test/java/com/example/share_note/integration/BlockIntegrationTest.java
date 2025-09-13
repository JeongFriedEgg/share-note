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
import com.example.share_note.util.UuidUtils;
import org.junit.jupiter.api.*;
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
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
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

    @MockBean
    private UuidUtils uuidUtils;

    private final String VALID_TOKEN = "Bearer validAccessToken";

    private UUID workspaceId;
    private UUID pageId;
    private UUID publicPageId;
    private UUID ownerId;
    private UUID memberId;
    private UUID nonMemberId;
    private UUID rootBlockId;
    private UUID childBlockId;
    private UUID anotherBlockId;
    private UUID newBlockId;

    private String workspaceIdStr;
    private String pageIdStr;
    private String publicPageIdStr;
    private String ownerIdStr;
    private String memberIdStr;
    private String nonMemberIdStr;
    private String rootBlockIdStr;
    private String childBlockIdStr;
    private String anotherBlockIdStr;
    private String newBlockIdStr;

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
        // UUID 생성
        workspaceId = UUID.randomUUID();
        pageId = UUID.randomUUID();
        publicPageId = UUID.randomUUID();
        ownerId = UUID.randomUUID();
        memberId = UUID.randomUUID();
        nonMemberId = UUID.randomUUID();
        rootBlockId = UUID.randomUUID();
        childBlockId = UUID.randomUUID();
        anotherBlockId = UUID.randomUUID();
        newBlockId = UUID.randomUUID();

        workspaceIdStr = workspaceId.toString();
        pageIdStr = pageId.toString();
        publicPageIdStr = publicPageId.toString();
        ownerIdStr = ownerId.toString();
        memberIdStr = memberId.toString();
        nonMemberIdStr = nonMemberId.toString();
        rootBlockIdStr = rootBlockId.toString();
        childBlockIdStr = childBlockId.toString();
        anotherBlockIdStr = anotherBlockId.toString();
        newBlockIdStr = newBlockId.toString();

        ownerDetails = new CustomUserDetails(ownerId, "owner", "password", "ROLE_USER", "owner@example.com");
        memberDetails = new CustomUserDetails(memberId, "member", "password", "ROLE_USER", "member@example.com");
        nonMemberDetails = new CustomUserDetails(nonMemberId, "nonmember", "password", "ROLE_USER", "nonmember@example.com");

        ownerAuth = new UsernamePasswordAuthenticationToken(ownerDetails, null,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")));
        memberAuth = new UsernamePasswordAuthenticationToken(memberDetails, null,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")));
        nonMemberAuth = new UsernamePasswordAuthenticationToken(nonMemberDetails, null,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")));

        // 엔티티 객체들 초기화
        workspace = Workspace.builder().id(workspaceId).createdBy(ownerId).build();
        page = Page.builder().id(pageId).workspaceId(workspaceId).isPublic(false).createdBy(ownerId).build();
        publicPage = Page.builder().id(publicPageId).workspaceId(workspaceId).isPublic(true).createdBy(ownerId).build();
        rootBlock = Block.builder().id(rootBlockId).pageId(pageId).parentBlockId(null).position(0).isArchived(false).build();
        childBlock = Block.builder().id(childBlockId).pageId(pageId).parentBlockId(rootBlockId).position(1).isArchived(false).build();
        anotherBlock = Block.builder().id(anotherBlockId).pageId(pageId).parentBlockId(null).position(1).isArchived(false).build();

        editPermission = PagePermission.builder()
                .id(UUID.randomUUID())
                .pageId(pageId)
                .userId(memberId)
                .permission(PagePermissionType.EDIT.name())
                .build();
        readOnlyPermission = PagePermission.builder()
                .id(UUID.randomUUID())
                .pageId(pageId)
                .userId(memberId)
                .permission(PagePermissionType.READ.name())
                .build();

        when(jwtTokenProvider.validateToken(anyString())).thenReturn(true);

        when(uuidUtils.fromString(workspaceIdStr)).thenReturn(workspaceId);
        when(uuidUtils.fromString(pageIdStr)).thenReturn(pageId);
        when(uuidUtils.fromString(publicPageIdStr)).thenReturn(publicPageId);
        when(uuidUtils.fromString(ownerIdStr)).thenReturn(ownerId);
        when(uuidUtils.fromString(memberIdStr)).thenReturn(memberId);
        when(uuidUtils.fromString(nonMemberIdStr)).thenReturn(nonMemberId);
        when(uuidUtils.fromString(rootBlockIdStr)).thenReturn(rootBlockId);
        when(uuidUtils.fromString(childBlockIdStr)).thenReturn(childBlockId);
        when(uuidUtils.fromString(anotherBlockIdStr)).thenReturn(anotherBlockId);
        when(uuidUtils.fromString(newBlockIdStr)).thenReturn(newBlockId);
    }

    @Test
    @Order(1)
    @DisplayName("블록 생성 성공 - 워크스페이스 소유자")
    void createBlock_success_asOwner() {
        // given
        BlockCreateRequestDto request = BlockCreateRequestDto.builder()
                .type("text")
                .content("New Block")
                .build();

        Block savedBlock = Block.builder()
                .id(newBlockId)
                .pageId(pageId)
                .isArchived(false)
                .type("text")
                .content("New Block")
                .createdBy(ownerId)
                .build();

        when(jwtTokenProvider.getAuthentication(anyString())).thenReturn(ownerAuth);
        when(reactivePageRepository.findByIdAndWorkspaceId(pageId, workspaceId)).thenReturn(Mono.just(page));
        when(reactiveWorkspaceRepository.findById(workspaceId)).thenReturn(Mono.just(workspace));
        when(reactiveBlockRepository.save(any(Block.class))).thenReturn(Mono.just(savedBlock));

        // when & then
        webTestClient.post()
                .uri("/api/workspaces/{workspaceId}/pages/{pageId}/blocks", workspaceIdStr, pageIdStr)
                .header("Authorization", VALID_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.id").isEqualTo(newBlockIdStr);
    }

    @Test
    @Order(2)
    @DisplayName("블록 생성 성공 - 편집 권한이 있는 멤버")
    void createBlock_success_asMemberWithEditPermission() {
        // given
        BlockCreateRequestDto request = BlockCreateRequestDto.builder()
                .type("text")
                .content("Member Block")
                .build();

        Block savedBlock = Block.builder()
                .id(newBlockId)
                .pageId(pageId)
                .type("text")
                .content("Member Block")
                .createdBy(memberId)
                .build();

        Workspace workspaceOwnedByOthers = Workspace.builder().id(workspaceId).createdBy(UUID.randomUUID()).build();

        when(jwtTokenProvider.getAuthentication(anyString())).thenReturn(memberAuth);
        when(reactivePageRepository.findByIdAndWorkspaceId(pageId, workspaceId)).thenReturn(Mono.just(page));
        when(reactiveWorkspaceRepository.findById(workspaceId)).thenReturn(Mono.just(workspaceOwnedByOthers));
        when(reactiveWorkspaceMemberRepository.existsByWorkspaceIdAndUserId(workspaceId, memberId)).thenReturn(Mono.just(true));
        when(reactivePagePermissionRepository.findByPageIdAndUserId(pageId, memberId)).thenReturn(Mono.just(editPermission));
        when(reactiveBlockRepository.save(any(Block.class))).thenReturn(Mono.just(savedBlock));

        // when & then
        webTestClient.post()
                .uri("/api/workspaces/{workspaceId}/pages/{pageId}/blocks", workspaceIdStr, pageIdStr)
                .header("Authorization", VALID_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.id").isEqualTo(newBlockIdStr);
    }

    @Test
    @Order(3)
    @DisplayName("블록 생성 실패 - 편집 권한이 없는 멤버")
    void createBlock_failure_noEditPermission() {
        // given
        BlockCreateRequestDto request = BlockCreateRequestDto.builder()
                .type("text")
                .content("New Block")
                .build();

        Workspace workspaceOwnedByOthers = Workspace.builder().id(workspaceId).createdBy(UUID.randomUUID()).build();

        when(jwtTokenProvider.getAuthentication(anyString())).thenReturn(memberAuth);
        when(reactivePageRepository.findByIdAndWorkspaceId(pageId, workspaceId)).thenReturn(Mono.just(page));
        when(reactiveWorkspaceRepository.findById(workspaceId)).thenReturn(Mono.just(workspaceOwnedByOthers));
        when(reactiveWorkspaceMemberRepository.existsByWorkspaceIdAndUserId(workspaceId, memberId)).thenReturn(Mono.just(true));
        when(reactivePagePermissionRepository.findByPageIdAndUserId(pageId, memberId)).thenReturn(Mono.just(readOnlyPermission));

        // when & then
        webTestClient.post()
                .uri("/api/workspaces/{workspaceId}/pages/{pageId}/blocks", workspaceIdStr, pageIdStr)
                .header("Authorization", VALID_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.message").isEqualTo(ErrorCode.PAGE_PERMISSION_DENIED.getMessage());
    }

    @Test
    @Order(4)
    @DisplayName("블록 생성 실패 - 페이지 없음")
    void createBlock_failure_pageNotFound() {
        // given
        BlockCreateRequestDto request = BlockCreateRequestDto.builder()
                .type("text")
                .content("New Block")
                .build();

        when(jwtTokenProvider.getAuthentication(anyString())).thenReturn(ownerAuth);
        when(reactivePageRepository.findByIdAndWorkspaceId(pageId, workspaceId)).thenReturn(Mono.empty());

        // when & then
        webTestClient.post()
                .uri("/api/workspaces/{workspaceId}/pages/{pageId}/blocks", workspaceIdStr, pageIdStr)
                .header("Authorization", VALID_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.message").isEqualTo(ErrorCode.PAGE_NOT_FOUND.getMessage());
    }

    @Test
    @Order(5)
    @DisplayName("블록 목록 조회 성공 - 읽기 권한이 있는 멤버")
    void getBlocks_success_asMemberWithReadPermission() {
        // given
        List<Block> blocks = List.of(rootBlock, childBlock);
        Workspace workspaceOwnedByOthers = Workspace.builder().id(workspaceId).createdBy(UUID.randomUUID()).build();

        when(jwtTokenProvider.getAuthentication(anyString())).thenReturn(memberAuth);
        when(reactivePageRepository.findByIdAndWorkspaceId(pageId, workspaceId)).thenReturn(Mono.just(page));
        when(reactiveWorkspaceRepository.findById(workspaceId)).thenReturn(Mono.just(workspaceOwnedByOthers));
        when(reactiveWorkspaceMemberRepository.existsByWorkspaceIdAndUserId(workspaceId, memberId)).thenReturn(Mono.just(true));
        when(reactivePagePermissionRepository.findByPageIdAndUserId(pageId, memberId)).thenReturn(Mono.just(readOnlyPermission));
        when(reactiveBlockRepository.findAllByPageIdAndIsArchivedFalseOrderByPositionAsc(pageId)).thenReturn(Flux.fromIterable(blocks));

        // when & then
        webTestClient.get()
                .uri("/api/workspaces/{workspaceId}/pages/{pageId}/blocks", workspaceIdStr, pageIdStr)
                .header("Authorization", VALID_TOKEN)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.blocks").isArray()
                .jsonPath("$.blocks.length()").isEqualTo(2);
    }

    @Test
    @Order(6)
    @DisplayName("블록 목록 조회 실패 - 비공개 페이지에 대한 권한 없음")
    void getBlocks_failure_noPermissionPrivatePage() {
        // given
        Workspace workspaceOwnedByOthers = Workspace.builder().id(workspaceId).createdBy(UUID.randomUUID()).build();

        when(jwtTokenProvider.getAuthentication(anyString())).thenReturn(nonMemberAuth);
        when(reactivePageRepository.findByIdAndWorkspaceId(pageId, workspaceId)).thenReturn(Mono.just(page));
        when(reactiveWorkspaceRepository.findById(workspaceId)).thenReturn(Mono.just(workspaceOwnedByOthers));
        when(reactiveWorkspaceMemberRepository.existsByWorkspaceIdAndUserId(workspaceId, nonMemberId)).thenReturn(Mono.just(false));

        // when & then
        webTestClient.get()
                .uri("/api/workspaces/{workspaceId}/pages/{pageId}/blocks", workspaceIdStr, pageIdStr)
                .header("Authorization", VALID_TOKEN)
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.message").isEqualTo(ErrorCode.PAGE_PERMISSION_DENIED.getMessage());
    }

    @Test
    @Order(7)
    @DisplayName("단일 블록 조회 성공 - 공개 페이지에 비멤버가 접근")
    void getBlock_success_asNonMember_onPublicPage() {
        // given
        Block publicBlock = Block.builder().id(newBlockId).pageId(publicPageId).isArchived(false).createdBy(ownerId).lastEditedBy(ownerId).build();

        when(jwtTokenProvider.getAuthentication(anyString())).thenReturn(nonMemberAuth);
        when(reactiveBlockRepository.findByIdAndPageId(newBlockId, publicPageId)).thenReturn(Mono.just(publicBlock));
        when(reactivePageRepository.findByIdAndWorkspaceId(publicPageId, workspaceId)).thenReturn(Mono.just(publicPage));
        when(reactiveWorkspaceRepository.findById(workspaceId)).thenReturn(Mono.just(workspace));
        when(reactiveWorkspaceMemberRepository.existsByWorkspaceIdAndUserId(workspaceId, nonMemberId)).thenReturn(Mono.just(false));

        // when & then
        webTestClient.get()
                .uri("/api/workspaces/{workspaceId}/pages/{pageId}/blocks/{blockId}", workspaceIdStr, publicPageIdStr, newBlockIdStr)
                .header("Authorization", VALID_TOKEN)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo(newBlockIdStr);
    }

    @Test
    @Order(8)
    @DisplayName("단일 블록 조회 실패 - 블록 없음")
    void getBlock_failure_blockNotFound() {
        // given
        when(jwtTokenProvider.getAuthentication(anyString())).thenReturn(ownerAuth);
        when(reactiveBlockRepository.findByIdAndPageId(newBlockId, pageId)).thenReturn(Mono.empty());

        // when & then
        webTestClient.get()
                .uri("/api/workspaces/{workspaceId}/pages/{pageId}/blocks/{blockId}", workspaceIdStr, pageIdStr, newBlockIdStr)
                .header("Authorization", VALID_TOKEN)
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.message").isEqualTo(ErrorCode.BLOCK_NOT_FOUND.getMessage());
    }

    @Test
    @Order(9)
    @DisplayName("블록 수정 성공 - 편집 권한이 있는 멤버")
    void updateBlock_success_asMemberWithEditPermission() {
        // given
        BlockUpdateRequestDto request = BlockUpdateRequestDto.builder()
                .content("Updated Content")
                .build();

        Block updatedBlock = Block.builder()
                .id(rootBlockId)
                .pageId(pageId)
                .content("Updated Content")
                .createdBy(ownerId)
                .lastEditedBy(ownerId)
                .build();

        Workspace workspaceOwnedByOthers = Workspace.builder().id(workspaceId).createdBy(UUID.randomUUID()).build();

        when(jwtTokenProvider.getAuthentication(anyString())).thenReturn(memberAuth);
        when(reactiveBlockRepository.findByIdAndPageId(rootBlockId, pageId)).thenReturn(Mono.just(rootBlock));
        when(reactivePageRepository.findByIdAndWorkspaceId(pageId, workspaceId)).thenReturn(Mono.just(page));
        when(reactiveWorkspaceRepository.findById(workspaceId)).thenReturn(Mono.just(workspaceOwnedByOthers));
        when(reactiveWorkspaceMemberRepository.existsByWorkspaceIdAndUserId(workspaceId, memberId)).thenReturn(Mono.just(true));
        when(reactivePagePermissionRepository.findByPageIdAndUserId(pageId, memberId)).thenReturn(Mono.just(editPermission));
        when(reactiveBlockRepository.save(any(Block.class))).thenReturn(Mono.just(updatedBlock));

        // when & then
        webTestClient.put()
                .uri("/api/workspaces/{workspaceId}/pages/{pageId}/blocks/{blockId}", workspaceIdStr, pageIdStr, rootBlockIdStr)
                .header("Authorization", VALID_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.content").isEqualTo("Updated Content");
    }

    @Test
    @Order(10)
    @DisplayName("블록 이동 성공 - 워크스페이스 소유자")
    void moveBlock_success_asOwner() {
        // given
        BlockMoveRequestDto request = BlockMoveRequestDto.builder()
                .newParentBlockId(anotherBlockIdStr)
                .newPosition(0)
                .build();

        Block movedBlock = Block.builder()
                .id(rootBlockId)
                .pageId(pageId)
                .parentBlockId(anotherBlockId)
                .position(0)
                .createdBy(ownerId)
                .lastEditedBy(ownerId)
                .build();

        when(jwtTokenProvider.getAuthentication(anyString())).thenReturn(ownerAuth);
        when(reactiveBlockRepository.findByIdAndPageId(rootBlockId, pageId)).thenReturn(Mono.just(rootBlock));
        when(reactivePageRepository.findByIdAndWorkspaceId(pageId, workspaceId)).thenReturn(Mono.just(page));
        when(reactiveWorkspaceRepository.findById(workspaceId)).thenReturn(Mono.just(workspace));
        when(reactiveBlockRepository.findByIdAndPageId(anotherBlockId, pageId)).thenReturn(Mono.just(anotherBlock));
        when(reactiveBlockRepository.save(any(Block.class))).thenReturn(Mono.just(movedBlock));

        // when & then
        webTestClient.put()
                .uri("/api/workspaces/{workspaceId}/pages/{pageId}/blocks/{blockId}/move", workspaceIdStr, pageIdStr, rootBlockIdStr)
                .header("Authorization", VALID_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.parentBlockId").isEqualTo(anotherBlockIdStr)
                .jsonPath("$.position").isEqualTo(0);
    }

    @Test
    @Order(11)
    @DisplayName("블록 이동 실패 - 부모 블록이 존재하지 않음")
    void moveBlock_failure_parentBlockNotFound() {
        // given
        UUID nonExistentBlockId = UUID.randomUUID();
        String nonExistentBlockIdStr = nonExistentBlockId.toString();

        BlockMoveRequestDto request = BlockMoveRequestDto.builder()
                .newParentBlockId(nonExistentBlockIdStr)
                .newPosition(0)
                .build();

        when(uuidUtils.fromString(nonExistentBlockIdStr)).thenReturn(nonExistentBlockId);
        when(jwtTokenProvider.getAuthentication(anyString())).thenReturn(ownerAuth);
        when(reactiveBlockRepository.findByIdAndPageId(rootBlockId, pageId)).thenReturn(Mono.just(rootBlock));
        when(reactivePageRepository.findByIdAndWorkspaceId(pageId, workspaceId)).thenReturn(Mono.just(page));
        when(reactiveWorkspaceRepository.findById(workspaceId)).thenReturn(Mono.just(workspace));
        when(reactiveBlockRepository.findByIdAndPageId(nonExistentBlockId, pageId)).thenReturn(Mono.empty());

        // when & then
        webTestClient.put()
                .uri("/api/workspaces/{workspaceId}/pages/{pageId}/blocks/{blockId}/move", workspaceIdStr, pageIdStr, rootBlockIdStr)
                .header("Authorization", VALID_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.message").isEqualTo(ErrorCode.PARENT_BLOCK_NOT_FOUND.getMessage());
    }

    @Test
    @Order(12)
    @DisplayName("블록 이동 실패 - 자기 자신을 부모로 설정")
    void moveBlock_failure_selfParent() {
        // given
        BlockMoveRequestDto request = BlockMoveRequestDto.builder()
                .newParentBlockId(rootBlockIdStr) // 자기 자신
                .newPosition(0)
                .build();

        when(jwtTokenProvider.getAuthentication(anyString())).thenReturn(ownerAuth);
        when(reactiveBlockRepository.findByIdAndPageId(rootBlockId, pageId)).thenReturn(Mono.just(rootBlock));
        when(reactivePageRepository.findByIdAndWorkspaceId(pageId, workspaceId)).thenReturn(Mono.just(page));
        when(reactiveWorkspaceRepository.findById(workspaceId)).thenReturn(Mono.just(workspace));

        // when & then
        webTestClient.put()
                .uri("/api/workspaces/{workspaceId}/pages/{pageId}/blocks/{blockId}/move", workspaceIdStr, pageIdStr, rootBlockIdStr)
                .header("Authorization", VALID_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.message").isEqualTo(ErrorCode.CANNOT_MOVE_TO_SELF.getMessage());
    }

    @Test
    @Order(13)
    @DisplayName("블록 보관 성공 - 워크스페이스 소유자")
    void archiveBlock_success_asOwner() {
        // given
        when(jwtTokenProvider.getAuthentication(anyString())).thenReturn(ownerAuth);
        when(reactiveBlockRepository.findByIdAndPageId(rootBlockId, pageId)).thenReturn(Mono.just(rootBlock));
        when(reactivePageRepository.findByIdAndWorkspaceId(pageId, workspaceId)).thenReturn(Mono.just(page));
        when(reactiveWorkspaceRepository.findById(workspaceId)).thenReturn(Mono.just(workspace));
        when(reactiveBlockRepository.updateArchiveStatusForBlockTree(eq(rootBlockId), eq(true), eq(ownerId))).thenReturn(Mono.empty());

        // when & then
        webTestClient.put()
                .uri("/api/workspaces/{workspaceId}/pages/{pageId}/blocks/{blockId}/archive", workspaceIdStr, pageIdStr, rootBlockIdStr)
                .header("Authorization", VALID_TOKEN)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.blockId").isEqualTo(rootBlockIdStr)
                .jsonPath("$.isArchived").isEqualTo(true);
    }

    @Test
    @Order(14)
    @DisplayName("블록 복원 성공 - 편집 권한이 있는 멤버")
    void restoreBlock_success_asMemberWithEditPermission() {
        // given
        Block archivedBlock = Block.builder()
                .id(rootBlockId)
                .pageId(pageId)
                .parentBlockId(null)
                .position(0)
                .isArchived(true) // 보관된 상태
                .build();

        Workspace workspaceOwnedByOthers = Workspace.builder().id(workspaceId).createdBy(UUID.randomUUID()).build();

        when(jwtTokenProvider.getAuthentication(anyString())).thenReturn(memberAuth);
        when(reactiveBlockRepository.findByIdAndPageId(rootBlockId, pageId)).thenReturn(Mono.just(archivedBlock));
        when(reactivePageRepository.findByIdAndWorkspaceId(pageId, workspaceId)).thenReturn(Mono.just(page));
        when(reactiveWorkspaceRepository.findById(workspaceId)).thenReturn(Mono.just(workspaceOwnedByOthers));
        when(reactiveWorkspaceMemberRepository.existsByWorkspaceIdAndUserId(workspaceId, memberId)).thenReturn(Mono.just(true));
        when(reactivePagePermissionRepository.findByPageIdAndUserId(pageId, memberId)).thenReturn(Mono.just(editPermission));
        when(reactiveBlockRepository.updateArchiveStatusForBlockTree(eq(rootBlockId), eq(false), eq(memberId))).thenReturn(Mono.empty());

        // when & then
        webTestClient.put()
                .uri("/api/workspaces/{workspaceId}/pages/{pageId}/blocks/{blockId}/restore", workspaceIdStr, pageIdStr, rootBlockIdStr)
                .header("Authorization", VALID_TOKEN)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.blockId").isEqualTo(rootBlockIdStr)
                .jsonPath("$.isArchived").isEqualTo(false);
    }
}