package com.example.share_note.integration;

import com.example.share_note.domain.Workspace;
import com.example.share_note.domain.WorkspaceMember;
import com.example.share_note.dto.CustomUserDetails;
import com.example.share_note.dto.workspacemember.WorkspaceMemberInviteRequestDto;
import com.example.share_note.dto.workspacemember.WorkspaceMemberRoleUpdateRequestDto;
import com.example.share_note.enums.WorkspaceRole;
import com.example.share_note.exception.ErrorCode;
import com.example.share_note.repository.ReactiveWorkspaceMemberRepository;
import com.example.share_note.repository.ReactiveWorkspaceRepository;
import com.example.share_note.security.JwtTokenProvider;
import com.example.share_note.util.UuidUtils;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.*;


@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class WorkspaceMemberIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

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
    private UUID ownerId;
    private UUID adminId;
    private UUID memberId;
    private UUID inviteUserId;

    private String workspaceIdStr;
    private String ownerIdStr;
    private String adminIdStr;
    private String memberIdStr;
    private String inviteUserIdStr;

    private CustomUserDetails ownerDetails;
    private CustomUserDetails adminDetails;
    private CustomUserDetails memberDetails;

    private Workspace workspace;
    private WorkspaceMember ownerMember;
    private WorkspaceMember adminMember;
    private WorkspaceMember regularMember;

    private Authentication ownerAuth;
    private Authentication adminAuth;
    private Authentication memberAuth;

    @BeforeEach
    void setUp() {
        workspaceId = UUID.randomUUID();
        ownerId = UUID.randomUUID();
        adminId = UUID.randomUUID();
        memberId = UUID.randomUUID();
        inviteUserId = UUID.randomUUID();

        workspaceIdStr = workspaceId.toString();
        ownerIdStr = ownerId.toString();
        adminIdStr = adminId.toString();
        memberIdStr = memberId.toString();
        inviteUserIdStr = inviteUserId.toString();

        ownerDetails = new CustomUserDetails(ownerId, "owner", "password", "ROLE_USER", "owner@example.com");
        adminDetails = new CustomUserDetails(adminId, "admin", "password", "ROLE_USER", "admin@example.com");
        memberDetails = new CustomUserDetails(memberId, "member", "password", "ROLE_USER", "member@example.com");

        ownerAuth = new UsernamePasswordAuthenticationToken(ownerDetails, null,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")));
        adminAuth = new UsernamePasswordAuthenticationToken(adminDetails, null,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")));
        memberAuth = new UsernamePasswordAuthenticationToken(memberDetails, null,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")));

        workspace = Workspace.builder()
                .id(workspaceId)
                .name("Test Workspace")
                .description("Test Description")
                .createdBy(ownerId)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        ownerMember = WorkspaceMember.builder()
                .id(UUID.randomUUID())
                .workspaceId(workspaceId)
                .userId(ownerId)
                .role(WorkspaceRole.OWNER)
                .joinedAt(LocalDateTime.now())
                .build();

        adminMember = WorkspaceMember.builder()
                .id(UUID.randomUUID())
                .workspaceId(workspaceId)
                .userId(adminId)
                .role(WorkspaceRole.ADMIN)
                .joinedAt(LocalDateTime.now())
                .build();

        regularMember = WorkspaceMember.builder()
                .id(UUID.randomUUID())
                .workspaceId(workspaceId)
                .userId(memberId)
                .role(WorkspaceRole.MEMBER)
                .joinedAt(LocalDateTime.now())
                .build();

        when(jwtTokenProvider.validateToken(anyString())).thenReturn(true);

        when(uuidUtils.fromString(workspaceIdStr)).thenReturn(workspaceId);
        when(uuidUtils.fromString(ownerIdStr)).thenReturn(ownerId);
        when(uuidUtils.fromString(adminIdStr)).thenReturn(adminId);
        when(uuidUtils.fromString(memberIdStr)).thenReturn(memberId);
        when(uuidUtils.fromString(inviteUserIdStr)).thenReturn(inviteUserId);
    }

    @Test
    @Order(1)
    @DisplayName("멤버 초대 성공 - ADMIN 권한으로 멤버 초대")
    void inviteMember_success_asAdmin() {
        // given
        WorkspaceMemberInviteRequestDto request = WorkspaceMemberInviteRequestDto.builder()
                .userId(inviteUserIdStr)
                .role(WorkspaceRole.MEMBER)
                .build();

        WorkspaceMember newMember = WorkspaceMember.builder()
                .id(UUID.randomUUID())
                .workspaceId(workspaceId)
                .userId(inviteUserId)
                .role(WorkspaceRole.MEMBER)
                .joinedAt(LocalDateTime.now())
                .build();

        when(jwtTokenProvider.getAuthentication(anyString())).thenReturn(adminAuth);
        when(reactiveWorkspaceRepository.findById(workspaceId)).thenReturn(Mono.just(workspace));
        when(reactiveWorkspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, adminId))
                .thenReturn(Mono.just(adminMember));
        when(reactiveWorkspaceMemberRepository.existsByWorkspaceIdAndUserId(workspaceId, inviteUserId))
                .thenReturn(Mono.just(false));
        when(reactiveWorkspaceMemberRepository.save(any(WorkspaceMember.class))).thenReturn(Mono.just(newMember));

        // when & then
        webTestClient.post()
                .uri("/api/workspaces/{workspaceId}/members/invite", workspaceIdStr)
                .header("Authorization", VALID_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.userId").isEqualTo(inviteUserIdStr)
                .jsonPath("$.role").isEqualTo(WorkspaceRole.MEMBER.name());
    }

    @Test
    @Order(2)
    @DisplayName("멤버 초대 성공 - OWNER 권한으로 멤버 초대")
    void inviteMember_success_asOwner() {
        // given
        WorkspaceMemberInviteRequestDto request = WorkspaceMemberInviteRequestDto.builder()
                .userId(inviteUserIdStr)
                .role(WorkspaceRole.ADMIN)
                .build();

        WorkspaceMember newMember = WorkspaceMember.builder()
                .id(UUID.randomUUID())
                .workspaceId(workspaceId)
                .userId(inviteUserId)
                .role(WorkspaceRole.ADMIN)
                .joinedAt(LocalDateTime.now())
                .build();

        when(jwtTokenProvider.getAuthentication(anyString())).thenReturn(ownerAuth);
        when(reactiveWorkspaceRepository.findById(workspaceId)).thenReturn(Mono.just(workspace));
        when(reactiveWorkspaceMemberRepository.existsByWorkspaceIdAndUserId(workspaceId, inviteUserId))
                .thenReturn(Mono.just(false));
        when(reactiveWorkspaceMemberRepository.save(any(WorkspaceMember.class))).thenReturn(Mono.just(newMember));

        // when & then
        webTestClient.post()
                .uri("/api/workspaces/{workspaceId}/members/invite", workspaceIdStr)
                .header("Authorization", VALID_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.userId").isEqualTo(inviteUserIdStr)
                .jsonPath("$.role").isEqualTo(WorkspaceRole.ADMIN.name());
    }

    @Test
    @Order(3)
    @DisplayName("멤버 초대 실패 - 이미 존재하는 멤버")
    void inviteMember_failure_alreadyExists() {
        // given
        WorkspaceMemberInviteRequestDto request = WorkspaceMemberInviteRequestDto.builder()
                .userId(memberIdStr) // 이미 존재하는 멤버
                .role(WorkspaceRole.MEMBER)
                .build();

        when(jwtTokenProvider.getAuthentication(anyString())).thenReturn(adminAuth);
        when(reactiveWorkspaceRepository.findById(workspaceId)).thenReturn(Mono.just(workspace));
        when(reactiveWorkspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, adminId))
                .thenReturn(Mono.just(adminMember));
        when(reactiveWorkspaceMemberRepository.existsByWorkspaceIdAndUserId(workspaceId, memberId))
                .thenReturn(Mono.just(true));

        // when & then
        webTestClient.post()
                .uri("/api/workspaces/{workspaceId}/members/invite", workspaceIdStr)
                .header("Authorization", VALID_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.CONFLICT)
                .expectBody()
                .jsonPath("$.message").isEqualTo(ErrorCode.MEMBER_ALREADY_EXISTS.getMessage());
    }

    @Test
    @Order(4)
    @DisplayName("멤버 초대 실패 - 권한 없음 (MEMBER 권한)")
    void inviteMember_failure_permissionDenied() {
        // given
        WorkspaceMemberInviteRequestDto request = WorkspaceMemberInviteRequestDto.builder()
                .userId(inviteUserIdStr)
                .role(WorkspaceRole.MEMBER)
                .build();

        when(jwtTokenProvider.getAuthentication(anyString())).thenReturn(memberAuth);
        when(reactiveWorkspaceRepository.findById(workspaceId)).thenReturn(Mono.just(workspace));
        when(reactiveWorkspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, memberId))
                .thenReturn(Mono.just(regularMember));

        // when & then
        webTestClient.post()
                .uri("/api/workspaces/{workspaceId}/members/invite", workspaceIdStr)
                .header("Authorization", VALID_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.message").isEqualTo(ErrorCode.WORKSPACE_PERMISSION_DENIED.getMessage());
    }

    @Test
    @Order(5)
    @DisplayName("멤버 목록 조회 성공 - MEMBER 권한으로 조회")
    void getWorkspaceMembers_success() {
        // given
        List<WorkspaceMember> members = List.of(ownerMember, adminMember, regularMember);

        when(jwtTokenProvider.getAuthentication(anyString())).thenReturn(memberAuth);
        when(reactiveWorkspaceRepository.findById(workspaceId)).thenReturn(Mono.just(workspace));
        when(reactiveWorkspaceMemberRepository.existsByWorkspaceIdAndUserId(workspaceId, memberId))
                .thenReturn(Mono.just(true));
        when(reactiveWorkspaceMemberRepository.findByWorkspaceId(workspaceId))
                .thenReturn(Flux.fromIterable(members));

        // when & then
        webTestClient.get()
                .uri("/api/workspaces/{workspaceId}/members", workspaceIdStr)
                .header("Authorization", VALID_TOKEN)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.totalCount").isEqualTo(3)
                .jsonPath("$.members[0].role").isEqualTo(WorkspaceRole.OWNER.name())
                .jsonPath("$.members[1].role").isEqualTo(WorkspaceRole.ADMIN.name())
                .jsonPath("$.members[2].role").isEqualTo(WorkspaceRole.MEMBER.name());
    }

    @Test
    @Order(6)
    @DisplayName("멤버 목록 조회 실패 - 권한 없음")
    void getWorkspaceMembers_failure_permissionDenied() {
        // given
        UUID uninvitedUserId = UUID.randomUUID();
        CustomUserDetails uninvitedUser = new CustomUserDetails(uninvitedUserId, "uninvited", "pass", "ROLE_USER", "uninvited@example.com");
        Authentication uninvitedAuth = new UsernamePasswordAuthenticationToken(uninvitedUser, null,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")));

        when(jwtTokenProvider.getAuthentication(anyString())).thenReturn(uninvitedAuth);
        when(reactiveWorkspaceRepository.findById(workspaceId)).thenReturn(Mono.just(workspace));
        when(reactiveWorkspaceMemberRepository.existsByWorkspaceIdAndUserId(workspaceId, uninvitedUserId))
                .thenReturn(Mono.just(false));

        // when & then
        webTestClient.get()
                .uri("/api/workspaces/{workspaceId}/members", workspaceIdStr)
                .header("Authorization", VALID_TOKEN)
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.message").isEqualTo(ErrorCode.WORKSPACE_PERMISSION_DENIED.getMessage());
    }

    @Test
    @Order(7)
    @DisplayName("멤버 역할 수정 성공 - ADMIN이 MEMBER의 역할을 ADMIN으로 변경")
    void updateMemberRole_success() {
        // given
        WorkspaceMemberRoleUpdateRequestDto request = WorkspaceMemberRoleUpdateRequestDto.builder()
                .userId(memberIdStr) // regularMember
                .role(WorkspaceRole.ADMIN)
                .build();

        WorkspaceMember updatedMember = WorkspaceMember.builder()
                .id(regularMember.getId())
                .workspaceId(workspaceId)
                .userId(memberId)
                .role(WorkspaceRole.ADMIN)
                .joinedAt(regularMember.getJoinedAt())
                .build();

        when(jwtTokenProvider.getAuthentication(anyString())).thenReturn(adminAuth);
        when(reactiveWorkspaceRepository.findById(workspaceId)).thenReturn(Mono.just(workspace));
        when(reactiveWorkspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, adminId))
                .thenReturn(Mono.just(adminMember));
        when(reactiveWorkspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, memberId))
                .thenReturn(Mono.just(regularMember));
        when(reactiveWorkspaceMemberRepository.save(any(WorkspaceMember.class))).thenReturn(Mono.just(updatedMember));

        // when & then
        webTestClient.put()
                .uri("/api/workspaces/{workspaceId}/members/role", workspaceIdStr)
                .header("Authorization", VALID_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.userId").isEqualTo(memberIdStr)
                .jsonPath("$.role").isEqualTo(WorkspaceRole.ADMIN.name());
    }

    @Test
    @Order(8)
    @DisplayName("멤버 역할 수정 실패 - 소유자의 역할은 변경할 수 없음")
    void updateMemberRole_failure_cannotChangeOwnerRole() {
        // given
        WorkspaceMemberRoleUpdateRequestDto request = WorkspaceMemberRoleUpdateRequestDto.builder()
                .userId(ownerIdStr) // ownerMember
                .role(WorkspaceRole.MEMBER)
                .build();

        when(jwtTokenProvider.getAuthentication(anyString())).thenReturn(adminAuth);
        when(reactiveWorkspaceRepository.findById(workspaceId)).thenReturn(Mono.just(workspace));
        when(reactiveWorkspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, adminId))
                .thenReturn(Mono.just(adminMember));
        when(reactiveWorkspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, ownerId))
                .thenReturn(Mono.just(ownerMember));

        // when & then
        webTestClient.put()
                .uri("/api/workspaces/{workspaceId}/members/role", workspaceIdStr)
                .header("Authorization", VALID_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.message").isEqualTo(ErrorCode.CANNOT_CHANGE_OWNER_ROLE.getMessage());
    }

    @Test
    @Order(9)
    @DisplayName("멤버 역할 수정 실패 - 권한 없음 (MEMBER 권한)")
    void updateMemberRole_failure_permissionDenied() {
        // given
        WorkspaceMemberRoleUpdateRequestDto request = WorkspaceMemberRoleUpdateRequestDto.builder()
                .userId(adminIdStr)
                .role(WorkspaceRole.MEMBER)
                .build();

        when(jwtTokenProvider.getAuthentication(anyString())).thenReturn(memberAuth);
        when(reactiveWorkspaceRepository.findById(workspaceId)).thenReturn(Mono.just(workspace));
        when(reactiveWorkspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, memberId))
                .thenReturn(Mono.just(regularMember));

        // when & then
        webTestClient.put()
                .uri("/api/workspaces/{workspaceId}/members/role", workspaceIdStr)
                .header("Authorization", VALID_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.message").isEqualTo(ErrorCode.WORKSPACE_PERMISSION_DENIED.getMessage());
    }

    @Test
    @Order(10)
    @DisplayName("멤버 삭제 성공 - OWNER가 다른 멤버 삭제")
    void removeMember_success_asOwner() {
        // given
        when(jwtTokenProvider.getAuthentication(anyString())).thenReturn(ownerAuth);
        when(reactiveWorkspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, ownerId))
                .thenReturn(Mono.just(ownerMember));
        when(reactiveWorkspaceMemberRepository.existsByWorkspaceIdAndUserId(workspaceId, memberId))
                .thenReturn(Mono.just(true));
        when(reactiveWorkspaceMemberRepository.deleteByWorkspaceIdAndUserId(workspaceId, memberId))
                .thenReturn(Mono.empty());

        // when & then
        webTestClient.delete()
                .uri("/api/workspaces/{workspaceId}/members/{userId}", workspaceIdStr, memberIdStr)
                .header("Authorization", VALID_TOKEN)
                .exchange()
                .expectStatus().isNoContent();
    }

    @Test
    @Order(11)
    @DisplayName("멤버 삭제 실패 - OWNER가 자기 자신을 삭제 시도")
    void removeMember_failure_ownerRemovesSelf() {
        // given
        when(jwtTokenProvider.getAuthentication(anyString())).thenReturn(ownerAuth);
        when(reactiveWorkspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, ownerId))
                .thenReturn(Mono.just(ownerMember));

        // when & then
        webTestClient.delete()
                .uri("/api/workspaces/{workspaceId}/members/{userId}", workspaceIdStr, ownerIdStr)
                .header("Authorization", VALID_TOKEN)
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.message").isEqualTo(ErrorCode.CANNOT_REMOVE_OWNER.getMessage());
    }

    @Test
    @Order(12)
    @DisplayName("멤버 삭제 실패 - 권한 없음 (ADMIN이 삭제 시도)")
    void removeMember_failure_permissionDenied() {
        // given
        when(jwtTokenProvider.getAuthentication(anyString())).thenReturn(adminAuth);
        when(reactiveWorkspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, adminId))
                .thenReturn(Mono.just(adminMember));

        // when & then
        webTestClient.delete()
                .uri("/api/workspaces/{workspaceId}/members/{userId}", workspaceIdStr, memberIdStr)
                .header("Authorization", VALID_TOKEN)
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.message").isEqualTo(ErrorCode.WORKSPACE_PERMISSION_DENIED.getMessage());
    }

    @Test
    @Order(13)
    @DisplayName("멤버 삭제 실패 - 존재하지 않는 멤버")
    void removeMember_failure_memberNotFound() {
        // given
        when(jwtTokenProvider.getAuthentication(anyString())).thenReturn(ownerAuth);
        when(reactiveWorkspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, ownerId))
                .thenReturn(Mono.just(ownerMember));
        when(reactiveWorkspaceMemberRepository.existsByWorkspaceIdAndUserId(workspaceId, inviteUserId))
                .thenReturn(Mono.just(false)); // 존재하지 않음

        // when & then
        webTestClient.delete()
                .uri("/api/workspaces/{workspaceId}/members/{userId}", workspaceIdStr, inviteUserIdStr)
                .header("Authorization", VALID_TOKEN)
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.message").isEqualTo(ErrorCode.MEMBER_NOT_FOUND.getMessage());
    }

    @Test
    @Order(14)
    @DisplayName("멤버 초대 실패 - 인증 토큰 없음")
    void inviteMember_failure_noAuthToken() {
        // given
        WorkspaceMemberInviteRequestDto request = WorkspaceMemberInviteRequestDto.builder()
                .userId(inviteUserIdStr)
                .role(WorkspaceRole.MEMBER)
                .build();

        // when & then (Authorization 헤더 없음)
        webTestClient.post()
                .uri("/api/workspaces/{workspaceId}/members/invite", workspaceIdStr)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isUnauthorized();
    }
}
