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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
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

    private final String VALID_TOKEN = "Bearer validAccessToken";

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
        ownerDetails = new CustomUserDetails(1L, "owner", "password", "ROLE_USER", "owner@example.com");
        memberDetails = new CustomUserDetails(2L, "member", "password", "ROLE_USER", "member@example.com");
        nonMemberDetails = new CustomUserDetails(99L, "nonmember", "password", "ROLE_USER", "nonmember@example.com");

        ownerAuth = new UsernamePasswordAuthenticationToken(ownerDetails, null,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")));
        memberAuth = new UsernamePasswordAuthenticationToken(memberDetails, null,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")));
        nonMemberAuth = new UsernamePasswordAuthenticationToken(nonMemberDetails, null,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")));

        workspace = Workspace.builder()
                .id(1L)
                .name("Test Workspace")
                .description("Test Description")
                .createdBy(1L)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        rootPage = Page.builder()
                .id(1L)
                .workspaceId(1L)
                .parentPageId(null)
                .title("Root Page")
                .icon("📄")
                .cover("cover.jpg")
                .isPublic(false)
                .isArchived(false)
                .isTemplate(false)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .createdBy(1L)
                .lastEditedBy(1L)
                .build();

        childPage = Page.builder()
                .id(2L)
                .workspaceId(1L)
                .parentPageId(1L)
                .title("Child Page")
                .icon("📝")
                .isPublic(false)
                .isArchived(false)
                .isTemplate(false)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .createdBy(1L)
                .lastEditedBy(1L)
                .build();

        pagePermission = PagePermission.builder()
                .id(1L)
                .pageId(1L)
                .userId(2L)
                .permission(PagePermissionType.EDIT.name())
                .build();

        when(jwtTokenProvider.validateToken(anyString())).thenReturn(true);
    }

    // ========== 페이지 생성 테스트 ==========

    @Test
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
                .id(3L)
                .workspaceId(1L)
                .title("New Root Page")
                .icon("🆕")
                .cover("new-cover.jpg")
                .properties("{\"color\": \"blue\"}")
                .createdAt(LocalDateTime.now())
                .createdBy(1L)
                .build();

        when(jwtTokenProvider.getAuthentication(anyString())).thenReturn(ownerAuth);
        when(reactiveWorkspaceRepository.findById(1L)).thenReturn(Mono.just(workspace));
        when(reactivePageRepository.save(any(Page.class))).thenReturn(Mono.just(savedPage));

        // when & then
        webTestClient.post()
                .uri("/api/workspaces/{workspaceId}/pages", 1L)
                .header("Authorization", VALID_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.pageId").isEqualTo(3L)
                .jsonPath("$.workspaceId").isEqualTo(1L)
                .jsonPath("$.createdBy").isEqualTo(1L);
    }

    @Test
    @DisplayName("페이지 생성 성공 - 부모 페이지에 편집 권한이 있는 멤버가 자식 페이지 생성")
    void createPage_success_asMember_withEditPermission() {
        // given
        PageCreateRequestDto request = PageCreateRequestDto.builder()
                .parentPageId(1L)
                .title("Child Page")
                .build();

        Page savedPage = Page.builder()
                .id(4L)
                .workspaceId(1L)
                .parentPageId(1L)
                .title("Child Page")
                .createdAt(LocalDateTime.now())
                .createdBy(2L)
                .build();

        when(jwtTokenProvider.getAuthentication(anyString())).thenReturn(memberAuth);
        when(reactiveWorkspaceRepository.findById(1L)).thenReturn(Mono.just(workspace));
        when(reactiveWorkspaceMemberRepository.existsByWorkspaceIdAndUserId(1L, 2L)).thenReturn(Mono.just(true));
        when(reactivePageRepository.findByIdAndWorkspaceId(1L, 1L)).thenReturn(Mono.just(rootPage));
        when(reactivePagePermissionRepository.findByPageIdAndUserId(1L, 2L)).thenReturn(Mono.just(pagePermission));
        when(reactivePageRepository.save(any(Page.class))).thenReturn(Mono.just(savedPage));

        // when & then
        webTestClient.post()
                .uri("/api/workspaces/{workspaceId}/pages", 1L)
                .header("Authorization", VALID_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.pageId").isEqualTo(4L)
                .jsonPath("$.workspaceId").isEqualTo(1L)
                .jsonPath("$.createdBy").isEqualTo(2L);
    }

    @Test
    @DisplayName("페이지 생성 실패 - 워크스페이스 멤버가 아님")
    void createPage_failure_notWorkspaceMember() {
        // given
        PageCreateRequestDto request = PageCreateRequestDto.builder()
                .title("Unauthorized Page")
                .build();

        Workspace workspaceOwnedByOthers = Workspace.builder()
                .id(1L)
                .name("Test Workspace")
                .createdBy(5L) // 다른 사용자가 소유
                .build();

        when(jwtTokenProvider.getAuthentication(anyString())).thenReturn(nonMemberAuth);
        when(reactiveWorkspaceRepository.findById(1L)).thenReturn(Mono.just(workspaceOwnedByOthers));
        when(reactiveWorkspaceMemberRepository.existsByWorkspaceIdAndUserId(1L, 99L)).thenReturn(Mono.just(false));

        // when & then
        webTestClient.post()
                .uri("/api/workspaces/{workspaceId}/pages", 1L)
                .header("Authorization", VALID_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.message").isEqualTo(ErrorCode.MEMBER_NOT_FOUND.getMessage());
    }

    @Test
    @DisplayName("페이지 생성 실패 - 부모 페이지에 대한 권한 없음")
    void createPage_failure_noParentPagePermission() {
        // given
        PageCreateRequestDto request = PageCreateRequestDto.builder()
                .parentPageId(1L)
                .title("Unauthorized Child Page")
                .build();

        when(jwtTokenProvider.getAuthentication(anyString())).thenReturn(memberAuth);
        when(reactiveWorkspaceRepository.findById(1L)).thenReturn(Mono.just(workspace));
        when(reactiveWorkspaceMemberRepository.existsByWorkspaceIdAndUserId(1L, 2L)).thenReturn(Mono.just(true));
        when(reactivePageRepository.findByIdAndWorkspaceId(1L, 1L)).thenReturn(Mono.just(rootPage));
        when(reactivePagePermissionRepository.findByPageIdAndUserId(1L, 2L)).thenReturn(Mono.empty());

        // when & then
        webTestClient.post()
                .uri("/api/workspaces/{workspaceId}/pages", 1L)
                .header("Authorization", VALID_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.message").isEqualTo(ErrorCode.PARENT_PAGE_PERMISSION_DENIED.getMessage());
    }

    // ========== 페이지 목록 조회 테스트 ==========

    @Test
    @DisplayName("페이지 목록 조회 성공 - 워크스페이스 멤버")
    void getPages_success() {
        // given
        List<Page> pages = List.of(rootPage);

        when(jwtTokenProvider.getAuthentication(anyString())).thenReturn(memberAuth);
        when(reactiveWorkspaceRepository.findById(1L)).thenReturn(Mono.just(workspace));
        when(reactiveWorkspaceMemberRepository.existsByWorkspaceIdAndUserId(1L, 2L)).thenReturn(Mono.just(true));
        when(reactivePageRepository.findAllByWorkspaceIdAndParentPageIdIsNull(1L))
                .thenReturn(Flux.fromIterable(pages));

        // when & then
        webTestClient.get()
                .uri("/api/workspaces/{workspaceId}/pages", 1L)
                .header("Authorization", VALID_TOKEN)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.pages").isArray()
                .jsonPath("$.pages[0].pageId").isEqualTo(1L)
                .jsonPath("$.pages[0].title").isEqualTo("Root Page")
                .jsonPath("$.pages[0].icon").isEqualTo("📄");
    }

    @Test
    @DisplayName("페이지 목록 조회 실패 - 워크스페이스 멤버가 아님")
    void getPages_failure_notWorkspaceMember() {
        // given
        Workspace workspaceOwnedByOthers = Workspace.builder()
                .id(1L)
                .name("Test Workspace")
                .createdBy(5L)
                .build();

        when(jwtTokenProvider.getAuthentication(anyString())).thenReturn(nonMemberAuth);
        when(reactiveWorkspaceRepository.findById(1L)).thenReturn(Mono.just(workspaceOwnedByOthers));
        when(reactiveWorkspaceMemberRepository.existsByWorkspaceIdAndUserId(1L, 99L)).thenReturn(Mono.just(false));

        // when & then
        webTestClient.get()
                .uri("/api/workspaces/{workspaceId}/pages", 1L)
                .header("Authorization", VALID_TOKEN)
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.message").isEqualTo(ErrorCode.MEMBER_NOT_FOUND.getMessage());
    }

    // ========== 단일 페이지 조회 테스트 ==========

    @Test
    @DisplayName("페이지 조회 성공 - 읽기 권한이 있는 멤버")
    void getPage_success_memberWithReadPermission() {
        // given
        PagePermission readPermission = PagePermission.builder()
                .pageId(1L)
                .userId(2L)
                .permission(PagePermissionType.READ.name())
                .build();

        when(jwtTokenProvider.getAuthentication(anyString())).thenReturn(memberAuth);
        when(reactiveWorkspaceRepository.findById(1L)).thenReturn(Mono.just(workspace));
        when(reactiveWorkspaceMemberRepository.existsByWorkspaceIdAndUserId(1L, 2L)).thenReturn(Mono.just(true));
        when(reactivePageRepository.findByIdAndWorkspaceId(1L, 1L)).thenReturn(Mono.just(rootPage));
        when(reactivePagePermissionRepository.findByPageIdAndUserId(1L, 2L)).thenReturn(Mono.just(readPermission));

        // when & then
        webTestClient.get()
                .uri("/api/workspaces/{workspaceId}/pages/{pageId}", 1L, 1L)
                .header("Authorization", VALID_TOKEN)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo(1L)
                .jsonPath("$.title").isEqualTo("Root Page")
                .jsonPath("$.icon").isEqualTo("📄");
    }

    @Test
    @DisplayName("페이지 조회 성공 - 공개 페이지에 비멤버가 접근")
    void getPage_success_publicPageByNonMember() {
        // given
        Page publicPage = Page.builder()
                .id(1L)
                .workspaceId(1L)
                .title("Public Page")
                .isPublic(true)
                .createdBy(1L)
                .build();

        when(jwtTokenProvider.getAuthentication(anyString())).thenReturn(nonMemberAuth);
        when(reactiveWorkspaceRepository.findById(1L)).thenReturn(Mono.just(workspace));
        when(reactiveWorkspaceMemberRepository.existsByWorkspaceIdAndUserId(1L, 99L)).thenReturn(Mono.just(false));
        when(reactivePageRepository.findByIdAndWorkspaceId(1L, 1L)).thenReturn(Mono.just(publicPage));

        // when & then
        webTestClient.get()
                .uri("/api/workspaces/{workspaceId}/pages/{pageId}", 1L, 1L)
                .header("Authorization", VALID_TOKEN)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo(1L)
                .jsonPath("$.title").isEqualTo("Public Page");
    }

    @Test
    @DisplayName("페이지 조회 실패 - 권한이 없는 비공개 페이지")
    void getPage_failure_noPermissionPrivatePage() {
        // given
        when(jwtTokenProvider.getAuthentication(anyString())).thenReturn(memberAuth);
        when(reactiveWorkspaceRepository.findById(1L)).thenReturn(Mono.just(workspace));
        when(reactiveWorkspaceMemberRepository.existsByWorkspaceIdAndUserId(1L, 2L)).thenReturn(Mono.just(true));
        when(reactivePageRepository.findByIdAndWorkspaceId(1L, 1L)).thenReturn(Mono.just(rootPage));
        when(reactivePagePermissionRepository.findByPageIdAndUserId(1L, 2L)).thenReturn(Mono.empty());

        // when & then
        webTestClient.get()
                .uri("/api/workspaces/{workspaceId}/pages/{pageId}", 1L, 1L)
                .header("Authorization", VALID_TOKEN)
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.message").isEqualTo(ErrorCode.PAGE_PERMISSION_DENIED.getMessage());
    }

    // ========== 페이지 수정 테스트 ==========

    @Test
    @DisplayName("페이지 수정 성공 - 워크스페이스 소유자")
    void updatePage_success_asOwner() {
        // given
        PageUpdateRequestDto request = PageUpdateRequestDto.builder()
                .title("Updated Page")
                .icon("✅")
                .cover("updated-cover.jpg")
                .build();

        Page updatedPage = Page.builder()
                .id(1L)
                .workspaceId(1L)
                .title("Updated Page")
                .icon("✅")
                .cover("updated-cover.jpg")
                .updatedAt(LocalDateTime.now())
                .lastEditedBy(1L)
                .build();

        when(jwtTokenProvider.getAuthentication(anyString())).thenReturn(ownerAuth);
        when(reactiveWorkspaceRepository.findById(1L)).thenReturn(Mono.just(workspace));
        when(reactivePageRepository.findByIdAndWorkspaceId(1L, 1L)).thenReturn(Mono.just(rootPage));
        when(reactivePageRepository.save(any(Page.class))).thenReturn(Mono.just(updatedPage));

        // when & then
        webTestClient.put()
                .uri("/api/workspaces/{workspaceId}/pages/{pageId}", 1L, 1L)
                .header("Authorization", VALID_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo(1L)
                .jsonPath("$.title").isEqualTo("Updated Page")
                .jsonPath("$.icon").isEqualTo("✅");
    }

    @Test
    @DisplayName("페이지 수정 실패 - 편집 권한이 없는 멤버")
    void updatePage_failure_noEditPermission() {
        // given
        PageUpdateRequestDto request = PageUpdateRequestDto.builder()
                .title("Unauthorized Update")
                .build();

        PagePermission readOnlyPermission = PagePermission.builder()
                .pageId(1L)
                .userId(2L)
                .permission(PagePermissionType.READ.name())
                .build();

        when(jwtTokenProvider.getAuthentication(anyString())).thenReturn(memberAuth);
        when(reactiveWorkspaceRepository.findById(1L)).thenReturn(Mono.just(workspace));
        when(reactivePageRepository.findByIdAndWorkspaceId(1L, 1L)).thenReturn(Mono.just(rootPage));
        when(reactiveWorkspaceMemberRepository.existsByWorkspaceIdAndUserId(1L, 2L)).thenReturn(Mono.just(true));
        when(reactivePagePermissionRepository.findByPageIdAndUserId(1L, 2L)).thenReturn(Mono.just(readOnlyPermission));

        // when & then
        webTestClient.put()
                .uri("/api/workspaces/{workspaceId}/pages/{pageId}", 1L, 1L)
                .header("Authorization", VALID_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.message").isEqualTo(ErrorCode.PAGE_PERMISSION_DENIED.getMessage());
    }

    // ========== 페이지 멤버 초대 테스트 ==========

    @Test
    @DisplayName("페이지 멤버 초대 성공 - 워크스페이스 소유자가 멤버 초대")
    void inviteMemberToPage_success_asOwner() {
        // given
        PageInviteRequestDto request = PageInviteRequestDto.builder()
                .userId(2L)
                .permissionType(PagePermissionType.EDIT.name())
                .build();

        PagePermission savedPermission = PagePermission.builder()
                .id(2L)
                .pageId(1L)
                .userId(2L)
                .permission(PagePermissionType.EDIT.name())
                .build();

        when(jwtTokenProvider.getAuthentication(anyString())).thenReturn(ownerAuth);
        when(reactiveWorkspaceRepository.findById(1L)).thenReturn(Mono.just(workspace));
        when(reactivePageRepository.findByIdAndWorkspaceId(1L, 1L)).thenReturn(Mono.just(rootPage));
        when(reactiveWorkspaceMemberRepository.existsByWorkspaceIdAndUserId(1L, 2L)).thenReturn(Mono.just(true));
        when(reactivePagePermissionRepository.findByPageIdAndUserId(1L, 2L)).thenReturn(Mono.empty());
        when(reactivePagePermissionRepository.save(any(PagePermission.class))).thenReturn(Mono.just(savedPermission));

        // when & then
        webTestClient.post()
                .uri("/api/workspaces/{workspaceId}/pages/{pageId}/members", 1L, 1L)
                .header("Authorization", VALID_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.pageId").isEqualTo(1L)
                .jsonPath("$.userId").isEqualTo(2L)
                .jsonPath("$.permission").isEqualTo(PagePermissionType.EDIT.name());
    }

    @Test
    @DisplayName("페이지 멤버 초대 실패 - 초대할 사용자가 워크스페이스 멤버가 아님")
    void inviteMemberToPage_failure_invitedUserNotWorkspaceMember() {
        // given
        PageInviteRequestDto request = PageInviteRequestDto.builder()
                .userId(99L) // 워크스페이스 멤버가 아닌 사용자
                .permissionType(PagePermissionType.EDIT.name())
                .build();

        when(jwtTokenProvider.getAuthentication(anyString())).thenReturn(ownerAuth);
        when(reactiveWorkspaceRepository.findById(1L)).thenReturn(Mono.just(workspace));
        when(reactivePageRepository.findByIdAndWorkspaceId(1L, 1L)).thenReturn(Mono.just(rootPage));
        when(reactiveWorkspaceMemberRepository.existsByWorkspaceIdAndUserId(1L, 99L)).thenReturn(Mono.just(false));

        // when & then
        webTestClient.post()
                .uri("/api/workspaces/{workspaceId}/pages/{pageId}/members", 1L, 1L)
                .header("Authorization", VALID_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.message").isEqualTo(ErrorCode.INVITED_USER_NOT_WORKSPACE_MEMBER.getMessage());
    }

    // ========== 페이지 공개 상태 변경 테스트 ==========

    @Test
    @DisplayName("페이지 공개 상태 변경 성공 - 워크스페이스 소유자가 공개로 변경")
    void updatePagePublicStatus_success_makePublic() {
        // given
        PagePublicStatusUpdateRequestDto request = PagePublicStatusUpdateRequestDto.builder()
                .isPublic(true)
                .build();

        Page updatedPage = Page.builder()
                .id(1L)
                .workspaceId(1L)
                .title("Root Page")
                .isPublic(true)
                .updatedAt(LocalDateTime.now())
                .lastEditedBy(1L)
                .build();

        when(jwtTokenProvider.getAuthentication(anyString())).thenReturn(ownerAuth);
        when(reactiveWorkspaceRepository.findById(1L)).thenReturn(Mono.just(workspace));
        when(reactivePageRepository.findByIdAndWorkspaceId(1L, 1L)).thenReturn(Mono.just(rootPage));
        when(reactivePageRepository.save(any(Page.class))).thenReturn(Mono.just(updatedPage));

        // when & then
        webTestClient.put()
                .uri("/api/workspaces/{workspaceId}/pages/{pageId}/public", 1L, 1L)
                .header("Authorization", VALID_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.pageId").isEqualTo(1L)
                .jsonPath("$.isPublic").isEqualTo(true);
    }

    // ========== 페이지 보관 테스트 ==========

    @Test
    @DisplayName("페이지 보관 성공 - 페이지 소유자")
    void archivePage_success_asPageOwner() {
        // given
        when(jwtTokenProvider.getAuthentication(anyString())).thenReturn(ownerAuth);
        when(reactiveWorkspaceRepository.findById(1L)).thenReturn(Mono.just(workspace));
        when(reactivePageRepository.findByIdAndWorkspaceId(1L, 1L)).thenReturn(Mono.just(rootPage));
        when(reactivePageRepository.updateArchiveStatusForTree(1L, true, 1L)).thenReturn(Mono.empty());
        when(reactiveBlockRepository.updateArchiveStatusForPageTree(1L, true)).thenReturn(Mono.empty());

        // when & then
        webTestClient.put()
                .uri("/api/workspaces/{workspaceId}/pages/{pageId}/archive", 1L, 1L)
                .header("Authorization", VALID_TOKEN)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.pageId").isEqualTo(1L)
                .jsonPath("$.isArchived").isEqualTo(true);
    }

    // ========== 페이지 복원 테스트 ==========

    @Test
    @DisplayName("페이지 복원 성공 - 페이지 소유자")
    void restorePage_success_asPageOwner() {
        // given
        when(jwtTokenProvider.getAuthentication(anyString())).thenReturn(ownerAuth);
        when(reactiveWorkspaceRepository.findById(1L)).thenReturn(Mono.just(workspace));
        when(reactivePageRepository.findByIdAndWorkspaceId(1L, 1L)).thenReturn(Mono.just(rootPage));
        when(reactivePageRepository.updateArchiveStatusForTree(1L, false, 1L)).thenReturn(Mono.empty());
        when(reactiveBlockRepository.updateArchiveStatusForPageTree(1L, false)).thenReturn(Mono.empty());

        // when & then
        webTestClient.put()
                .uri("/api/workspaces/{workspaceId}/pages/{pageId}/restore", 1L, 1L)
                .header("Authorization", VALID_TOKEN)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.pageId").isEqualTo(1L)
                .jsonPath("$.isArchived").isEqualTo(false);
    }

    // ========== 페이지 삭제 테스트 ==========

    @Test
    @DisplayName("페이지 삭제 성공 - 페이지 소유자")
    void deletePage_success_asPageOwner() {
        // given
        when(jwtTokenProvider.getAuthentication(anyString())).thenReturn(ownerAuth);
        when(reactiveWorkspaceRepository.findById(1L)).thenReturn(Mono.just(workspace));
        when(reactivePageRepository.findByIdAndWorkspaceId(1L, 1L)).thenReturn(Mono.just(rootPage));
        when(reactiveBlockRepository.deleteAllByPageTree(1L)).thenReturn(Mono.empty());
        when(reactivePageRepository.deletePageAndDescendants(1L)).thenReturn(Mono.empty());

        // when & then
        webTestClient.delete()
                .uri("/api/workspaces/{workspaceId}/pages/{pageId}", 1L, 1L)
                .header("Authorization", VALID_TOKEN)
                .exchange()
                .expectStatus().isNoContent();
    }

    @Test
    @DisplayName("페이지 삭제 실패 - 권한 없는 사용자")
    void deletePage_failure_noPermission() {
        // given
        when(jwtTokenProvider.getAuthentication(anyString())).thenReturn(memberAuth);
        when(reactiveWorkspaceRepository.findById(1L)).thenReturn(Mono.just(workspace));
        when(reactivePageRepository.findByIdAndWorkspaceId(1L, 1L)).thenReturn(Mono.just(rootPage));
        when(reactiveWorkspaceMemberRepository.existsByWorkspaceIdAndUserId(1L, 2L)).thenReturn(Mono.just(true));
        when(reactivePagePermissionRepository.findByPageIdAndUserId(1L, 2L)).thenReturn(Mono.just(pagePermission));

        // when & then
        webTestClient.delete()
                .uri("/api/workspaces/{workspaceId}/pages/{pageId}", 1L, 1L)
                .header("Authorization", VALID_TOKEN)
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.message").isEqualTo(ErrorCode.PAGE_PERMISSION_DENIED.getMessage());
    }

    // ========== 페이지 멤버 권한 수정 테스트 ==========

    @Test
    @DisplayName("페이지 멤버 권한 수정 성공 - 워크스페이스 소유자가 멤버 권한 수정")
    void updateMemberPagePermission_success_asOwner() {
        // given
        PageUpdatePermissionRequestDto request = PageUpdatePermissionRequestDto.builder()
                .permissionType(PagePermissionType.FULL_ACCESS.name())
                .build();

        PagePermission existingPermission = PagePermission.builder()
                .id(1L)
                .pageId(1L)
                .userId(2L)
                .permission(PagePermissionType.EDIT.name())
                .build();

        PagePermission updatedPermission = PagePermission.builder()
                .id(1L)
                .pageId(1L)
                .userId(2L)
                .permission(PagePermissionType.FULL_ACCESS.name())
                .build();

        when(jwtTokenProvider.getAuthentication(anyString())).thenReturn(ownerAuth);
        when(reactiveWorkspaceRepository.findById(1L)).thenReturn(Mono.just(workspace));
        when(reactivePageRepository.findByIdAndWorkspaceId(1L, 1L)).thenReturn(Mono.just(rootPage));
        when(reactiveWorkspaceMemberRepository.existsByWorkspaceIdAndUserId(1L, 2L)).thenReturn(Mono.just(true));
        when(reactivePagePermissionRepository.findByPageIdAndUserId(1L, 2L)).thenReturn(Mono.just(existingPermission));
        when(reactivePagePermissionRepository.save(any(PagePermission.class))).thenReturn(Mono.just(updatedPermission));

        // when & then
        webTestClient.put()
                .uri("/api/workspaces/{workspaceId}/pages/{pageId}/members/{userId}", 1L, 1L, 2L)
                .header("Authorization", VALID_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.pageId").isEqualTo(1L)
                .jsonPath("$.userId").isEqualTo(2L)
                .jsonPath("$.permission").isEqualTo(PagePermissionType.FULL_ACCESS.name());
    }

    @Test
    @DisplayName("페이지 멤버 권한 수정 실패 - 페이지 소유자의 권한은 수정할 수 없음")
    void updateMemberPagePermission_failure_cannotChangeOwnerPermission() {
        // given
        PageUpdatePermissionRequestDto request = PageUpdatePermissionRequestDto.builder()
                .permissionType(PagePermissionType.READ.name())
                .build();

        when(jwtTokenProvider.getAuthentication(anyString())).thenReturn(ownerAuth);
        when(reactiveWorkspaceRepository.findById(1L)).thenReturn(Mono.just(workspace));
        when(reactivePageRepository.findByIdAndWorkspaceId(1L, 1L)).thenReturn(Mono.just(rootPage));
        when(reactiveWorkspaceMemberRepository.existsByWorkspaceIdAndUserId(1L, 1L)).thenReturn(Mono.just(true));

        // when & then
        webTestClient.put()
                .uri("/api/workspaces/{workspaceId}/pages/{pageId}/members/{userId}", 1L, 1L, 1L)
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
    @DisplayName("인증 실패 - 유효하지 않은 토큰")
    void createPage_failure_invalidToken() {
        // given
        PageCreateRequestDto request = PageCreateRequestDto.builder()
                .title("Test Page")
                .build();

        when(jwtTokenProvider.validateToken(anyString())).thenReturn(false);

        // when & then
        webTestClient.post()
                .uri("/api/workspaces/{workspaceId}/pages", 1L)
                .header("Authorization", "Bearer invalidToken")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    @DisplayName("워크스페이스 존재하지 않음")
    void createPage_failure_workspaceNotFound() {
        // given
        PageCreateRequestDto request = PageCreateRequestDto.builder()
                .title("Test Page")
                .build();

        when(jwtTokenProvider.getAuthentication(anyString())).thenReturn(ownerAuth);
        when(reactiveWorkspaceRepository.findById(999L)).thenReturn(Mono.empty());

        // when & then
        webTestClient.post()
                .uri("/api/workspaces/{workspaceId}/pages", 999L)
                .header("Authorization", VALID_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.message").isEqualTo(ErrorCode.WORKSPACE_NOT_FOUND.getMessage());
    }

    @Test
    @DisplayName("페이지 존재하지 않음")
    void getPage_failure_pageNotFound() {
        // given
        when(jwtTokenProvider.getAuthentication(anyString())).thenReturn(ownerAuth);
        when(reactiveWorkspaceRepository.findById(1L)).thenReturn(Mono.just(workspace));
        when(reactiveWorkspaceMemberRepository.existsByWorkspaceIdAndUserId(1L, 1L)).thenReturn(Mono.just(true));
        when(reactivePageRepository.findByIdAndWorkspaceId(999L, 1L)).thenReturn(Mono.empty());

        // when & then
        webTestClient.get()
                .uri("/api/workspaces/{workspaceId}/pages/{pageId}", 1L, 999L)
                .header("Authorization", VALID_TOKEN)
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.message").isEqualTo(ErrorCode.PAGE_NOT_FOUND.getMessage());
    }

    // ========== 엣지 케이스 테스트 ==========

    @Test
    @DisplayName("제목이 null인 페이지 생성 - Untitled로 설정됨")
    void createPage_success_nullTitle_setsUntitled() {
        // given
        PageCreateRequestDto request = PageCreateRequestDto.builder()
                .title(null)
                .build();

        Page savedPage = Page.builder()
                .id(5L)
                .workspaceId(1L)
                .title("Untitled")
                .createdAt(LocalDateTime.now())
                .createdBy(1L)
                .build();

        when(jwtTokenProvider.getAuthentication(anyString())).thenReturn(ownerAuth);
        when(reactiveWorkspaceRepository.findById(1L)).thenReturn(Mono.just(workspace));
        when(reactivePageRepository.save(any(Page.class))).thenReturn(Mono.just(savedPage));

        // when & then
        webTestClient.post()
                .uri("/api/workspaces/{workspaceId}/pages", 1L)
                .header("Authorization", VALID_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.pageId").isEqualTo(5L);
    }

    @Test
    @DisplayName("이미 권한이 있는 사용자를 페이지에 다시 초대 - 권한 업데이트")
    void inviteMemberToPage_success_updateExistingPermission() {
        // given
        PageInviteRequestDto request = PageInviteRequestDto.builder()
                .userId(2L)
                .permissionType(PagePermissionType.FULL_ACCESS.name())
                .build();

        PagePermission existingPermission = PagePermission.builder()
                .id(1L)
                .pageId(1L)
                .userId(2L)
                .permission(PagePermissionType.READ.name())
                .build();

        PagePermission updatedPermission = PagePermission.builder()
                .id(1L)
                .pageId(1L)
                .userId(2L)
                .permission(PagePermissionType.FULL_ACCESS.name())
                .build();

        when(jwtTokenProvider.getAuthentication(anyString())).thenReturn(ownerAuth);
        when(reactiveWorkspaceRepository.findById(1L)).thenReturn(Mono.just(workspace));
        when(reactivePageRepository.findByIdAndWorkspaceId(1L, 1L)).thenReturn(Mono.just(rootPage));
        when(reactiveWorkspaceMemberRepository.existsByWorkspaceIdAndUserId(1L, 2L)).thenReturn(Mono.just(true));
        when(reactivePagePermissionRepository.findByPageIdAndUserId(1L, 2L)).thenReturn(Mono.just(existingPermission));
        when(reactivePagePermissionRepository.save(any(PagePermission.class))).thenReturn(Mono.just(updatedPermission));

        // when & then
        webTestClient.post()
                .uri("/api/workspaces/{workspaceId}/pages/{pageId}/members", 1L, 1L)
                .header("Authorization", VALID_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.pageId").isEqualTo(1L)
                .jsonPath("$.userId").isEqualTo(2L)
                .jsonPath("$.permission").isEqualTo(PagePermissionType.FULL_ACCESS.name());
    }
}