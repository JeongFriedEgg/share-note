package com.example.share_note.integration;

import com.example.share_note.domain.Page;
import com.example.share_note.domain.PagePermission;
import com.example.share_note.domain.Workspace;
import com.example.share_note.dto.CustomUserDetails;
import com.example.share_note.dto.page.*;
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
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class PageIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private ReactivePageRepository reactivePageRepository;

    @MockBean
    private ReactivePagePermissionRepository reactivePagePermissionRepository;

    @MockBean
    private ReactiveWorkspaceRepository reactiveWorkspaceRepository;

    @MockBean
    private ReactiveWorkspaceMemberRepository reactiveWorkspaceMemberRepository;

    @MockBean
    private ReactiveBlockRepository reactiveBlockRepository;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private UuidUtils uuidUtils;

    private final String VALID_TOKEN = "Bearer validAccessToken";

    private UUID workspaceId;
    private UUID rootPageId;
    private UUID childPageId;
    private UUID ownerId;
    private UUID memberId;
    private UUID nonMemberId;
    private UUID newPageId;
    private UUID newPermissionId;

    private String workspaceIdStr;
    private String rootPageIdStr;
    private String childPageIdStr;
    private String ownerIdStr;
    private String memberIdStr;
    private String nonMemberIdStr;
    private String newPageIdStr;

    private CustomUserDetails ownerDetails;
    private CustomUserDetails memberDetails;
    private CustomUserDetails nonMemberDetails;

    private Workspace workspace;
    private Page rootPage;
    private Page childPage;
    private PagePermission pagePermission;

    private Authentication ownerAuth;
    private Authentication memberAuth;
    private Authentication nonMemberAuth;

    @BeforeEach
    void setUp() {
        workspaceId = UUID.randomUUID();
        rootPageId = UUID.randomUUID();
        childPageId = UUID.randomUUID();
        ownerId = UUID.randomUUID();
        memberId = UUID.randomUUID();
        nonMemberId = UUID.randomUUID();
        newPageId = UUID.randomUUID();
        newPermissionId = UUID.randomUUID();

        workspaceIdStr = workspaceId.toString();
        rootPageIdStr = rootPageId.toString();
        childPageIdStr = childPageId.toString();
        ownerIdStr = ownerId.toString();
        memberIdStr = memberId.toString();
        nonMemberIdStr = nonMemberId.toString();
        newPageIdStr = newPageId.toString();

        ownerDetails = new CustomUserDetails(ownerId, "owner", "password", "ROLE_USER", "owner@example.com");
        memberDetails = new CustomUserDetails(memberId, "member", "password", "ROLE_USER", "member@example.com");
        nonMemberDetails = new CustomUserDetails(nonMemberId, "nonmember", "password", "ROLE_USER", "nonmember@example.com");

        ownerAuth = new UsernamePasswordAuthenticationToken(ownerDetails, null,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")));
        memberAuth = new UsernamePasswordAuthenticationToken(memberDetails, null,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")));
        nonMemberAuth = new UsernamePasswordAuthenticationToken(nonMemberDetails, null,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")));

        workspace = Workspace.builder()
                .id(workspaceId)
                .name("Test Workspace")
                .description("Test Description")
                .createdBy(ownerId)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        rootPage = Page.builder()
                .id(rootPageId)
                .workspaceId(workspaceId)
                .parentPageId(null)
                .title("Root Page")
                .icon("📄")
                .cover("cover.jpg")
                .isPublic(false)
                .isArchived(false)
                .isTemplate(false)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .createdBy(ownerId)
                .lastEditedBy(ownerId)
                .build();

        childPage = Page.builder()
                .id(childPageId)
                .workspaceId(workspaceId)
                .parentPageId(rootPageId)
                .title("Child Page")
                .icon("📝")
                .isPublic(false)
                .isArchived(false)
                .isTemplate(false)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .createdBy(ownerId)
                .lastEditedBy(ownerId)
                .build();

        pagePermission = PagePermission.builder()
                .id(UUID.randomUUID())
                .pageId(rootPageId)
                .userId(memberId)
                .permission(PagePermissionType.EDIT.name())
                .build();

        // JWT Token Provider Mock 설정
        when(jwtTokenProvider.validateToken(anyString())).thenReturn(true);
        when(uuidUtils.fromString(workspaceIdStr)).thenReturn(workspaceId);
        when(uuidUtils.fromString(rootPageIdStr)).thenReturn(rootPageId);
        when(uuidUtils.fromString(childPageIdStr)).thenReturn(childPageId);
        when(uuidUtils.fromString(ownerIdStr)).thenReturn(ownerId);
        when(uuidUtils.fromString(memberIdStr)).thenReturn(memberId);
        when(uuidUtils.fromString(nonMemberIdStr)).thenReturn(nonMemberId);
        when(uuidUtils.fromString(newPageIdStr)).thenReturn(newPageId);
    }

    @Test
    @Order(1)
    @DisplayName("페이지 생성 성공 - 워크스페이스 소유자가 루트 페이지 생성")
    void createPage_success_asOwner_rootPage() {
        // given
        PageCreateRequestDto request = PageCreateRequestDto.builder()
                .title("New Root Page")
                .icon("🆕")
                .cover("new-cover.jpg")
                .properties("{\"color\": \"blue\"}")
                .build();

        Page savedPage = Page.builder()
                .id(newPageId)
                .workspaceId(workspaceId)
                .title("New Root Page")
                .icon("🆕")
                .cover("new-cover.jpg")
                .properties("{\"color\": \"blue\"}")
                .createdAt(LocalDateTime.now())
                .createdBy(ownerId)
                .build();

        when(jwtTokenProvider.getAuthentication(anyString())).thenReturn(ownerAuth);
        when(reactiveWorkspaceRepository.findById(workspaceId)).thenReturn(Mono.just(workspace));
        when(reactivePageRepository.save(any(Page.class))).thenReturn(Mono.just(savedPage));

        // when & then
        webTestClient.post()
                .uri("/api/workspaces/{workspaceId}/pages", workspaceIdStr)
                .header("Authorization", VALID_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.pageId").isEqualTo(newPageIdStr)
                .jsonPath("$.workspaceId").isEqualTo(workspaceIdStr)
                .jsonPath("$.createdBy").isEqualTo(ownerIdStr);
    }

    @Test
    @Order(2)
    @DisplayName("페이지 생성 성공 - 부모 페이지에 편집 권한이 있는 멤버가 자식 페이지 생성")
    void createPage_success_asMember_withEditPermission() {
        // given
        PageCreateRequestDto request = PageCreateRequestDto.builder()
                .parentPageId(rootPageIdStr)
                .title("Child Page")
                .build();

        Page savedPage = Page.builder()
                .id(newPageId)
                .workspaceId(workspaceId)
                .parentPageId(rootPageId)
                .title("Child Page")
                .createdAt(LocalDateTime.now())
                .createdBy(memberId)
                .build();

        when(jwtTokenProvider.getAuthentication(anyString())).thenReturn(memberAuth);
        when(reactiveWorkspaceRepository.findById(workspaceId)).thenReturn(Mono.just(workspace));
        when(reactiveWorkspaceMemberRepository.existsByWorkspaceIdAndUserId(workspaceId, memberId)).thenReturn(Mono.just(true));
        when(reactivePageRepository.findByIdAndWorkspaceId(rootPageId, workspaceId)).thenReturn(Mono.just(rootPage));
        when(reactivePagePermissionRepository.findByPageIdAndUserId(rootPageId, memberId)).thenReturn(Mono.just(pagePermission));
        when(reactivePageRepository.save(any(Page.class))).thenReturn(Mono.just(savedPage));

        // when & then
        webTestClient.post()
                .uri("/api/workspaces/{workspaceId}/pages", workspaceIdStr)
                .header("Authorization", VALID_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.pageId").isEqualTo(newPageIdStr)
                .jsonPath("$.workspaceId").isEqualTo(workspaceIdStr)
                .jsonPath("$.createdBy").isEqualTo(memberIdStr);
    }

    @Test
    @Order(3)
    @DisplayName("페이지 생성 실패 - 워크스페이스 멤버가 아님")
    void createPage_failure_notWorkspaceMember() {
        // given
        PageCreateRequestDto request = PageCreateRequestDto.builder()
                .title("Unauthorized Page")
                .build();

        Workspace workspaceOwnedByOthers = Workspace.builder()
                .id(workspaceId)
                .name("Test Workspace")
                .createdBy(UUID.randomUUID()) // 다른 사용자가 소유
                .build();

        when(jwtTokenProvider.getAuthentication(anyString())).thenReturn(nonMemberAuth);
        when(reactiveWorkspaceRepository.findById(workspaceId)).thenReturn(Mono.just(workspaceOwnedByOthers));
        when(reactiveWorkspaceMemberRepository.existsByWorkspaceIdAndUserId(workspaceId, nonMemberId)).thenReturn(Mono.just(false));

        // when & then
        webTestClient.post()
                .uri("/api/workspaces/{workspaceId}/pages", workspaceIdStr)
                .header("Authorization", VALID_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.message").isEqualTo(ErrorCode.MEMBER_NOT_FOUND.getMessage());
    }

    @Test
    @Order(4)
    @DisplayName("페이지 생성 실패 - 부모 페이지에 대한 권한 없음")
    void createPage_failure_noParentPagePermission() {
        // given
        PageCreateRequestDto request = PageCreateRequestDto.builder()
                .parentPageId(rootPageIdStr)
                .title("Unauthorized Child Page")
                .build();

        when(jwtTokenProvider.getAuthentication(anyString())).thenReturn(memberAuth);
        when(reactiveWorkspaceRepository.findById(workspaceId)).thenReturn(Mono.just(workspace));
        when(reactiveWorkspaceMemberRepository.existsByWorkspaceIdAndUserId(workspaceId, memberId)).thenReturn(Mono.just(true));
        when(reactivePageRepository.findByIdAndWorkspaceId(rootPageId, workspaceId)).thenReturn(Mono.just(rootPage));
        when(reactivePagePermissionRepository.findByPageIdAndUserId(rootPageId, memberId)).thenReturn(Mono.empty());

        // when & then
        webTestClient.post()
                .uri("/api/workspaces/{workspaceId}/pages", workspaceIdStr)
                .header("Authorization", VALID_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.message").isEqualTo(ErrorCode.PARENT_PAGE_PERMISSION_DENIED.getMessage());
    }

    @Test
    @Order(5)
    @DisplayName("페이지 목록 조회 성공 - 워크스페이스 멤버")
    void getPages_success() {
        // given
        List<Page> pages = List.of(rootPage);

        when(jwtTokenProvider.getAuthentication(anyString())).thenReturn(memberAuth);
        when(reactiveWorkspaceRepository.findById(workspaceId)).thenReturn(Mono.just(workspace));
        when(reactiveWorkspaceMemberRepository.existsByWorkspaceIdAndUserId(workspaceId, memberId)).thenReturn(Mono.just(true));
        when(reactivePageRepository.findAllByWorkspaceIdAndParentPageIdIsNull(workspaceId))
                .thenReturn(Flux.fromIterable(pages));

        // when & then
        webTestClient.get()
                .uri("/api/workspaces/{workspaceId}/pages", workspaceIdStr)
                .header("Authorization", VALID_TOKEN)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.pages").isArray()
                .jsonPath("$.pages[0].pageId").isEqualTo(rootPageIdStr)
                .jsonPath("$.pages[0].title").isEqualTo("Root Page")
                .jsonPath("$.pages[0].icon").isEqualTo("📄");
    }

    @Test
    @Order(6)
    @DisplayName("페이지 목록 조회 실패 - 워크스페이스 멤버가 아님")
    void getPages_failure_notWorkspaceMember() {
        // given
        Workspace workspaceOwnedByOthers = Workspace.builder()
                .id(workspaceId)
                .name("Test Workspace")
                .createdBy(UUID.randomUUID())
                .build();

        when(jwtTokenProvider.getAuthentication(anyString())).thenReturn(nonMemberAuth);
        when(reactiveWorkspaceRepository.findById(workspaceId)).thenReturn(Mono.just(workspaceOwnedByOthers));
        when(reactiveWorkspaceMemberRepository.existsByWorkspaceIdAndUserId(workspaceId, nonMemberId)).thenReturn(Mono.just(false));

        // when & then
        webTestClient.get()
                .uri("/api/workspaces/{workspaceId}/pages", workspaceIdStr)
                .header("Authorization", VALID_TOKEN)
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.message").isEqualTo(ErrorCode.MEMBER_NOT_FOUND.getMessage());
    }

    @Test
    @Order(7)
    @DisplayName("페이지 조회 성공 - 읽기 권한이 있는 멤버")
    void getPage_success_memberWithReadPermission() {
        // given
        PagePermission readPermission = PagePermission.builder()
                .pageId(rootPageId)
                .userId(memberId)
                .permission(PagePermissionType.READ.name())
                .build();

        when(jwtTokenProvider.getAuthentication(anyString())).thenReturn(memberAuth);
        when(reactiveWorkspaceRepository.findById(workspaceId)).thenReturn(Mono.just(workspace));
        when(reactiveWorkspaceMemberRepository.existsByWorkspaceIdAndUserId(workspaceId, memberId)).thenReturn(Mono.just(true));
        when(reactivePageRepository.findByIdAndWorkspaceId(rootPageId, workspaceId)).thenReturn(Mono.just(rootPage));
        when(reactivePagePermissionRepository.findByPageIdAndUserId(rootPageId, memberId)).thenReturn(Mono.just(readPermission));

        // when & then
        webTestClient.get()
                .uri("/api/workspaces/{workspaceId}/pages/{pageId}", workspaceIdStr, rootPageIdStr)
                .header("Authorization", VALID_TOKEN)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo(rootPageIdStr)
                .jsonPath("$.title").isEqualTo("Root Page")
                .jsonPath("$.icon").isEqualTo("📄");
    }

    @Test
    @Order(8)
    @DisplayName("페이지 조회 성공 - 공개 페이지에 비멤버가 접근")
    void getPage_success_publicPageByNonMember() {
        // given
        Page publicPage = Page.builder()
                .id(rootPageId)
                .workspaceId(workspaceId)
                .title("Public Page")
                .isPublic(true)
                .createdBy(ownerId)
                .lastEditedBy(ownerId)
                .build();

        when(jwtTokenProvider.getAuthentication(anyString())).thenReturn(nonMemberAuth);
        when(reactiveWorkspaceRepository.findById(workspaceId)).thenReturn(Mono.just(workspace));
        when(reactiveWorkspaceMemberRepository.existsByWorkspaceIdAndUserId(workspaceId, nonMemberId)).thenReturn(Mono.just(false));
        when(reactivePageRepository.findByIdAndWorkspaceId(rootPageId, workspaceId)).thenReturn(Mono.just(publicPage));

        // when & then
        webTestClient.get()
                .uri("/api/workspaces/{workspaceId}/pages/{pageId}", workspaceIdStr, rootPageIdStr)
                .header("Authorization", VALID_TOKEN)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo(rootPageIdStr)
                .jsonPath("$.title").isEqualTo("Public Page");
    }

    @Test
    @Order(9)
    @DisplayName("페이지 조회 실패 - 권한이 없는 비공개 페이지")
    void getPage_failure_noPermissionPrivatePage() {
        // given
        when(jwtTokenProvider.getAuthentication(anyString())).thenReturn(memberAuth);
        when(reactiveWorkspaceRepository.findById(workspaceId)).thenReturn(Mono.just(workspace));
        when(reactiveWorkspaceMemberRepository.existsByWorkspaceIdAndUserId(workspaceId, memberId)).thenReturn(Mono.just(true));
        when(reactivePageRepository.findByIdAndWorkspaceId(rootPageId, workspaceId)).thenReturn(Mono.just(rootPage));
        when(reactivePagePermissionRepository.findByPageIdAndUserId(rootPageId, memberId)).thenReturn(Mono.empty());

        // when & then
        webTestClient.get()
                .uri("/api/workspaces/{workspaceId}/pages/{pageId}", workspaceIdStr, rootPageIdStr)
                .header("Authorization", VALID_TOKEN)
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.message").isEqualTo(ErrorCode.PAGE_PERMISSION_DENIED.getMessage());
    }

    @Test
    @Order(10)
    @DisplayName("페이지 수정 성공 - 워크스페이스 소유자")
    void updatePage_success_asOwner() {
        // given
        PageUpdateRequestDto request = PageUpdateRequestDto.builder()
                .title("Updated Page")
                .icon("✅")
                .cover("updated-cover.jpg")
                .build();

        Page updatedPage = Page.builder()
                .id(rootPageId)
                .workspaceId(workspaceId)
                .title("Updated Page")
                .icon("✅")
                .cover("updated-cover.jpg")
                .updatedAt(LocalDateTime.now())
                .createdBy(ownerId)
                .lastEditedBy(ownerId)
                .build();

        when(jwtTokenProvider.getAuthentication(anyString())).thenReturn(ownerAuth);
        when(reactiveWorkspaceRepository.findById(workspaceId)).thenReturn(Mono.just(workspace));
        when(reactivePageRepository.findByIdAndWorkspaceId(rootPageId, workspaceId)).thenReturn(Mono.just(rootPage));
        when(reactivePageRepository.save(any(Page.class))).thenReturn(Mono.just(updatedPage));

        // when & then
        webTestClient.put()
                .uri("/api/workspaces/{workspaceId}/pages/{pageId}", workspaceIdStr, rootPageIdStr)
                .header("Authorization", VALID_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo(rootPageIdStr)
                .jsonPath("$.title").isEqualTo("Updated Page")
                .jsonPath("$.icon").isEqualTo("✅");
    }

    @Test
    @Order(11)
    @DisplayName("페이지 수정 실패 - 편집 권한이 없는 멤버")
    void updatePage_failure_noEditPermission() {
        // given
        PageUpdateRequestDto request = PageUpdateRequestDto.builder()
                .title("Unauthorized Update")
                .build();

        PagePermission readOnlyPermission = PagePermission.builder()
                .pageId(rootPageId)
                .userId(memberId)
                .permission(PagePermissionType.READ.name())
                .build();

        when(jwtTokenProvider.getAuthentication(anyString())).thenReturn(memberAuth);
        when(reactiveWorkspaceRepository.findById(workspaceId)).thenReturn(Mono.just(workspace));
        when(reactivePageRepository.findByIdAndWorkspaceId(rootPageId, workspaceId)).thenReturn(Mono.just(rootPage));
        when(reactiveWorkspaceMemberRepository.existsByWorkspaceIdAndUserId(workspaceId, memberId)).thenReturn(Mono.just(true));
        when(reactivePagePermissionRepository.findByPageIdAndUserId(rootPageId, memberId)).thenReturn(Mono.just(readOnlyPermission));

        // when & then
        webTestClient.put()
                .uri("/api/workspaces/{workspaceId}/pages/{pageId}", workspaceIdStr, rootPageIdStr)
                .header("Authorization", VALID_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.message").isEqualTo(ErrorCode.PAGE_PERMISSION_DENIED.getMessage());
    }

    @Test
    @Order(12)
    @DisplayName("페이지 멤버 초대 성공 - 워크스페이스 소유자가 멤버 초대")
    void inviteMemberToPage_success_asOwner() {
        // given
        PageInviteRequestDto request = PageInviteRequestDto.builder()
                .userId(memberIdStr)
                .permissionType(PagePermissionType.EDIT.name())
                .build();

        PagePermission savedPermission = PagePermission.builder()
                .id(newPermissionId)
                .pageId(rootPageId)
                .userId(memberId)
                .permission(PagePermissionType.EDIT.name())
                .build();

        when(jwtTokenProvider.getAuthentication(anyString())).thenReturn(ownerAuth);
        when(reactiveWorkspaceRepository.findById(workspaceId)).thenReturn(Mono.just(workspace));
        when(reactivePageRepository.findByIdAndWorkspaceId(rootPageId, workspaceId)).thenReturn(Mono.just(rootPage));
        when(reactiveWorkspaceMemberRepository.existsByWorkspaceIdAndUserId(workspaceId, memberId)).thenReturn(Mono.just(true));
        when(reactivePagePermissionRepository.findByPageIdAndUserId(rootPageId, memberId)).thenReturn(Mono.empty());
        when(reactivePagePermissionRepository.save(any(PagePermission.class))).thenReturn(Mono.just(savedPermission));

        // when & then
        webTestClient.post()
                .uri("/api/workspaces/{workspaceId}/pages/{pageId}/members", workspaceIdStr, rootPageIdStr)
                .header("Authorization", VALID_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.pageId").isEqualTo(rootPageIdStr)
                .jsonPath("$.userId").isEqualTo(memberIdStr)
                .jsonPath("$.permission").isEqualTo(PagePermissionType.EDIT.name());
    }

    @Test
    @Order(13)
    @DisplayName("페이지 멤버 초대 실패 - 초대할 사용자가 워크스페이스 멤버가 아님")
    void inviteMemberToPage_failure_invitedUserNotWorkspaceMember() {
        // given
        PageInviteRequestDto request = PageInviteRequestDto.builder()
                .userId(nonMemberIdStr) // 워크스페이스 멤버가 아닌 사용자
                .permissionType(PagePermissionType.EDIT.name())
                .build();

        Workspace workspaceOwnedByOthers = Workspace.builder()
                .id(workspaceId)
                .name("Test Workspace")
                .createdBy(UUID.randomUUID()) // 다른 사용자가 소유
                .build();

        when(jwtTokenProvider.getAuthentication(anyString())).thenReturn(ownerAuth);
        when(reactiveWorkspaceRepository.findById(workspaceId)).thenReturn(Mono.just(workspaceOwnedByOthers));
        when(reactivePageRepository.findByIdAndWorkspaceId(rootPageId, workspaceId)).thenReturn(Mono.just(rootPage));
        when(reactiveWorkspaceMemberRepository.existsByWorkspaceIdAndUserId(workspaceId, nonMemberId)).thenReturn(Mono.just(false));
        when(reactivePagePermissionRepository.findByPageIdAndUserId(any(UUID.class), any(UUID.class))).thenReturn(Mono.empty());

        // when & then
        webTestClient.post()
                .uri("/api/workspaces/{workspaceId}/pages/{pageId}/members", workspaceIdStr, rootPageIdStr)
                .header("Authorization", VALID_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.message").isEqualTo(ErrorCode.PAGE_PERMISSION_DENIED.getMessage());
    }

    @Test
    @Order(14)
    @DisplayName("페이지 공개 상태 변경 성공 - 워크스페이스 소유자가 공개로 변경")
    void updatePagePublicStatus_success_makePublic() {
        // given
        PagePublicStatusUpdateRequestDto request = PagePublicStatusUpdateRequestDto.builder()
                .isPublic(true)
                .build();

        Page updatedPage = Page.builder()
                .id(rootPageId)
                .workspaceId(workspaceId)
                .title("Root Page")
                .isPublic(true)
                .updatedAt(LocalDateTime.now())
                .lastEditedBy(ownerId)
                .build();

        when(jwtTokenProvider.getAuthentication(anyString())).thenReturn(ownerAuth);
        when(reactiveWorkspaceRepository.findById(workspaceId)).thenReturn(Mono.just(workspace));
        when(reactivePageRepository.findByIdAndWorkspaceId(rootPageId, workspaceId)).thenReturn(Mono.just(rootPage));
        when(reactivePageRepository.save(any(Page.class))).thenReturn(Mono.just(updatedPage));

        // when & then
        webTestClient.put()
                .uri("/api/workspaces/{workspaceId}/pages/{pageId}/public", workspaceIdStr, rootPageIdStr)
                .header("Authorization", VALID_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.pageId").isEqualTo(rootPageIdStr)
                .jsonPath("$.isPublic").isEqualTo(true);
    }

    @Test
    @Order(15)
    @DisplayName("페이지 보관 성공 - 페이지 소유자")
    void archivePage_success_asPageOwner() {
        // given
        when(jwtTokenProvider.getAuthentication(anyString())).thenReturn(ownerAuth);
        when(reactiveWorkspaceRepository.findById(workspaceId)).thenReturn(Mono.just(workspace));
        when(reactivePageRepository.findByIdAndWorkspaceId(rootPageId, workspaceId)).thenReturn(Mono.just(rootPage));
        when(reactivePageRepository.updateArchiveStatusForTree(rootPageId, true, ownerId)).thenReturn(Mono.empty());
        when(reactiveBlockRepository.updateArchiveStatusForPageTree(rootPageId, true)).thenReturn(Mono.empty());

        // when & then
        webTestClient.put()
                .uri("/api/workspaces/{workspaceId}/pages/{pageId}/archive", workspaceIdStr, rootPageIdStr)
                .header("Authorization", VALID_TOKEN)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.pageId").isEqualTo(rootPageIdStr)
                .jsonPath("$.isArchived").isEqualTo(true);
    }

    @Test
    @Order(16)
    @DisplayName("페이지 복원 성공 - 페이지 소유자")
    void restorePage_success_asPageOwner() {
        // given
        when(jwtTokenProvider.getAuthentication(anyString())).thenReturn(ownerAuth);
        when(reactiveWorkspaceRepository.findById(workspaceId)).thenReturn(Mono.just(workspace));
        when(reactivePageRepository.findByIdAndWorkspaceId(rootPageId, workspaceId)).thenReturn(Mono.just(rootPage));
        when(reactivePageRepository.updateArchiveStatusForTree(rootPageId, false, ownerId)).thenReturn(Mono.empty());
        when(reactiveBlockRepository.updateArchiveStatusForPageTree(rootPageId, false)).thenReturn(Mono.empty());

        // when & then
        webTestClient.put()
                .uri("/api/workspaces/{workspaceId}/pages/{pageId}/restore", workspaceIdStr, rootPageIdStr)
                .header("Authorization", VALID_TOKEN)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.pageId").isEqualTo(rootPageIdStr)
                .jsonPath("$.isArchived").isEqualTo(false);
    }

    @Test
    @Order(17)
    @DisplayName("페이지 삭제 성공 - 페이지 소유자")
    void deletePage_success_asPageOwner() {
        // given
        when(jwtTokenProvider.getAuthentication(anyString())).thenReturn(ownerAuth);
        when(reactiveWorkspaceRepository.findById(workspaceId)).thenReturn(Mono.just(workspace));
        when(reactivePageRepository.findByIdAndWorkspaceId(rootPageId, workspaceId)).thenReturn(Mono.just(rootPage));
        when(reactiveBlockRepository.deleteAllByPageTree(rootPageId)).thenReturn(Mono.empty());
        when(reactivePageRepository.deletePageAndDescendants(rootPageId)).thenReturn(Mono.empty());

        // when & then
        webTestClient.delete()
                .uri("/api/workspaces/{workspaceId}/pages/{pageId}", workspaceIdStr, rootPageIdStr)
                .header("Authorization", VALID_TOKEN)
                .exchange()
                .expectStatus().isNoContent();
    }

    @Test
    @Order(18)
    @DisplayName("페이지 삭제 실패 - 권한 없는 사용자")
    void deletePage_failure_noPermission() {
        // given
        when(jwtTokenProvider.getAuthentication(anyString())).thenReturn(memberAuth);
        when(reactiveWorkspaceRepository.findById(workspaceId)).thenReturn(Mono.just(workspace));
        when(reactivePageRepository.findByIdAndWorkspaceId(rootPageId, workspaceId)).thenReturn(Mono.just(rootPage));
        when(reactiveWorkspaceMemberRepository.existsByWorkspaceIdAndUserId(workspaceId, memberId)).thenReturn(Mono.just(true));
        when(reactivePagePermissionRepository.findByPageIdAndUserId(rootPageId, memberId)).thenReturn(Mono.just(pagePermission));

        // when & then
        webTestClient.delete()
                .uri("/api/workspaces/{workspaceId}/pages/{pageId}", workspaceIdStr, rootPageIdStr)
                .header("Authorization", VALID_TOKEN)
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.message").isEqualTo(ErrorCode.PAGE_PERMISSION_DENIED.getMessage());
    }

    @Test
    @Order(19)
    @DisplayName("페이지 멤버 권한 수정 성공 - 워크스페이스 소유자가 멤버 권한 수정")
    void updateMemberPagePermission_success_asOwner() {
        // given
        PageUpdatePermissionRequestDto request = PageUpdatePermissionRequestDto.builder()
                .permissionType(PagePermissionType.FULL_ACCESS.name())
                .build();

        PagePermission existingPermission = PagePermission.builder()
                .id(newPermissionId)
                .pageId(rootPageId)
                .userId(memberId)
                .permission(PagePermissionType.EDIT.name())
                .build();

        PagePermission updatedPermission = PagePermission.builder()
                .id(newPermissionId)
                .pageId(rootPageId)
                .userId(memberId)
                .permission(PagePermissionType.FULL_ACCESS.name())
                .build();

        when(jwtTokenProvider.getAuthentication(anyString())).thenReturn(ownerAuth);
        when(reactiveWorkspaceRepository.findById(workspaceId)).thenReturn(Mono.just(workspace));
        when(reactivePageRepository.findByIdAndWorkspaceId(rootPageId, workspaceId)).thenReturn(Mono.just(rootPage));
        when(reactiveWorkspaceMemberRepository.existsByWorkspaceIdAndUserId(workspaceId, memberId)).thenReturn(Mono.just(true));
        when(reactivePagePermissionRepository.findByPageIdAndUserId(rootPageId, memberId)).thenReturn(Mono.just(existingPermission));
        when(reactivePagePermissionRepository.save(any(PagePermission.class))).thenReturn(Mono.just(updatedPermission));

        // when & then
        webTestClient.put()
                .uri("/api/workspaces/{workspaceId}/pages/{pageId}/members/{userId}", workspaceIdStr, rootPageIdStr, memberIdStr)
                .header("Authorization", VALID_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.pageId").isEqualTo(rootPageIdStr)
                .jsonPath("$.userId").isEqualTo(memberIdStr)
                .jsonPath("$.permission").isEqualTo(PagePermissionType.FULL_ACCESS.name());
    }

    @Test
    @Order(20)
    @DisplayName("페이지 멤버 권한 수정 실패 - 페이지 소유자의 권한은 수정할 수 없음")
    void updateMemberPagePermission_failure_cannotChangeOwnerPermission() {
        // given
        PageUpdatePermissionRequestDto request = PageUpdatePermissionRequestDto.builder()
                .permissionType(PagePermissionType.READ.name())
                .build();

        when(jwtTokenProvider.getAuthentication(anyString())).thenReturn(ownerAuth);
        when(reactiveWorkspaceRepository.findById(workspaceId)).thenReturn(Mono.just(workspace));
        when(reactivePageRepository.findByIdAndWorkspaceId(rootPageId, workspaceId)).thenReturn(Mono.just(rootPage));
        when(reactiveWorkspaceMemberRepository.existsByWorkspaceIdAndUserId(workspaceId, ownerId)).thenReturn(Mono.just(true));

        // when & then
        webTestClient.put()
                .uri("/api/workspaces/{workspaceId}/pages/{pageId}/members/{userId}", workspaceIdStr, rootPageIdStr, ownerIdStr)
                .header("Authorization", VALID_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.message").isEqualTo(ErrorCode.CANNOT_CHANGE_OWNER_PERMISSION.getMessage());
    }

    // ========== 인증 실패 테스트 ==========

    @Test
    @Order(21)
    @DisplayName("인증 실패 - 유효하지 않은 토큰")
    void createPage_failure_invalidToken() {
        // given
        PageCreateRequestDto request = PageCreateRequestDto.builder()
                .title("Test Page")
                .build();

        when(jwtTokenProvider.validateToken(anyString())).thenReturn(false);

        // when & then
        webTestClient.post()
                .uri("/api/workspaces/{workspaceId}/pages", workspaceIdStr)
                .header("Authorization", "Bearer invalidToken")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    @Order(22)
    @DisplayName("워크스페이스 존재하지 않음")
    void createPage_failure_workspaceNotFound() {
        // given
        PageCreateRequestDto request = PageCreateRequestDto.builder()
                .title("Test Page")
                .build();

        when(jwtTokenProvider.getAuthentication(anyString())).thenReturn(ownerAuth);
        when(reactiveWorkspaceRepository.findById(any(UUID.class))).thenReturn(Mono.empty());
        when(uuidUtils.fromString("99999999-9999-9999-9999-999999999999")).thenReturn(UUID.fromString("99999999-9999-9999-9999-999999999999"));

        // when & then
        webTestClient.post()
                .uri("/api/workspaces/{workspaceId}/pages", "99999999-9999-9999-9999-999999999999")
                .header("Authorization", VALID_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.message").isEqualTo(ErrorCode.WORKSPACE_NOT_FOUND.getMessage());
    }

    @Test
    @Order(23)
    @DisplayName("제목이 null인 페이지 생성 - Untitled로 설정됨")
    void createPage_success_nullTitle_setsUntitled() {
        // given
        PageCreateRequestDto request = PageCreateRequestDto.builder()
                .title(null)
                .build();

        Page savedPage = Page.builder()
                .id(newPageId)
                .workspaceId(workspaceId)
                .title("Untitled")
                .createdAt(LocalDateTime.now())
                .createdBy(ownerId)
                .build();

        when(jwtTokenProvider.getAuthentication(anyString())).thenReturn(ownerAuth);
        when(reactiveWorkspaceRepository.findById(workspaceId)).thenReturn(Mono.just(workspace));
        when(reactivePageRepository.save(any(Page.class))).thenReturn(Mono.just(savedPage));

        // when & then
        webTestClient.post()
                .uri("/api/workspaces/{workspaceId}/pages", workspaceIdStr)
                .header("Authorization", VALID_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.pageId").isEqualTo(newPageIdStr);
    }

    @Test
    @Order(24)
    @DisplayName("이미 권한이 있는 사용자를 페이지에 다시 초대 - 권한 업데이트")
    void inviteMemberToPage_success_updateExistingPermission() {
        // given
        PageInviteRequestDto request = PageInviteRequestDto.builder()
                .userId(memberIdStr)
                .permissionType(PagePermissionType.FULL_ACCESS.name())
                .build();

        PagePermission existingPermission = PagePermission.builder()
                .id(newPermissionId)
                .pageId(rootPageId)
                .userId(memberId)
                .permission(PagePermissionType.READ.name())
                .build();

        PagePermission updatedPermission = PagePermission.builder()
                .id(newPermissionId)
                .pageId(rootPageId)
                .userId(memberId)
                .permission(PagePermissionType.FULL_ACCESS.name())
                .build();

        when(jwtTokenProvider.getAuthentication(anyString())).thenReturn(ownerAuth);
        when(reactiveWorkspaceRepository.findById(workspaceId)).thenReturn(Mono.just(workspace));
        when(reactivePageRepository.findByIdAndWorkspaceId(rootPageId, workspaceId)).thenReturn(Mono.just(rootPage));
        when(reactiveWorkspaceMemberRepository.existsByWorkspaceIdAndUserId(workspaceId, memberId)).thenReturn(Mono.just(true));
        when(reactivePagePermissionRepository.findByPageIdAndUserId(rootPageId, memberId)).thenReturn(Mono.just(existingPermission));
        when(reactivePagePermissionRepository.save(any(PagePermission.class))).thenReturn(Mono.just(updatedPermission));

        // when & then
        webTestClient.post()
                .uri("/api/workspaces/{workspaceId}/pages/{pageId}/members", workspaceIdStr, rootPageIdStr)
                .header("Authorization", VALID_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.pageId").isEqualTo(rootPageIdStr)
                .jsonPath("$.userId").isEqualTo(memberIdStr)
                .jsonPath("$.permission").isEqualTo(PagePermissionType.FULL_ACCESS.name());
    }
}