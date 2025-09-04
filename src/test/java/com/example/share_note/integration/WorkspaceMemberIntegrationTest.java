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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
public class WorkspaceMemberIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private ReactiveWorkspaceRepository reactiveWorkspaceRepository;

    @MockBean
    private ReactiveWorkspaceMemberRepository reactiveWorkspaceMemberRepository;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    private final String VALID_TOKEN = "Bearer validAccessToken";

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
        ownerDetails = new CustomUserDetails(1L, "owner", "password", "ROLE_USER", "owner@example.com");
        adminDetails = new CustomUserDetails(2L, "admin", "password", "ROLE_USER", "admin@example.com");
        memberDetails = new CustomUserDetails(3L, "member", "password", "ROLE_USER", "member@example.com");

        ownerAuth = new UsernamePasswordAuthenticationToken(ownerDetails, null, Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")));
        adminAuth = new UsernamePasswordAuthenticationToken(adminDetails, null, Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")));
        memberAuth = new UsernamePasswordAuthenticationToken(memberDetails, null, Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")));

        workspace = Workspace.builder()
                .id(1L)
                .name("Test Workspace")
                .description("Test Description")
                .createdBy(1L)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        ownerMember = WorkspaceMember.builder()
                .id(1L)
                .workspaceId(1L)
                .userId(1L)
                .role(WorkspaceRole.OWNER)
                .joinedAt(LocalDateTime.now())
                .build();

        adminMember = WorkspaceMember.builder()
                .id(2L)
                .workspaceId(1L)
                .userId(2L)
                .role(WorkspaceRole.ADMIN)
                .joinedAt(LocalDateTime.now())
                .build();

        regularMember = WorkspaceMember.builder()
                .id(3L)
                .workspaceId(1L)
                .userId(3L)
                .role(WorkspaceRole.MEMBER)
                .joinedAt(LocalDateTime.now())
                .build();

        when(jwtTokenProvider.validateToken(anyString())).thenReturn(true);
    }


    @Test
    @DisplayName("멤버 초대 성공 - ADMIN 권한으로 멤버 초대")
    void inviteMember_success_asAdmin() {
        // given
        WorkspaceMemberInviteRequestDto request = WorkspaceMemberInviteRequestDto.builder()
                .userId(10L)
                .role(WorkspaceRole.MEMBER)
                .build();

        WorkspaceMember newMember = WorkspaceMember.builder()
                .workspaceId(1L)
                .userId(10L)
                .role(WorkspaceRole.MEMBER)
                .joinedAt(LocalDateTime.now())
                .build();

        when(jwtTokenProvider.getAuthentication(anyString())).thenReturn(adminAuth);
        when(reactiveWorkspaceRepository.findById(1L)).thenReturn(Mono.just(workspace));
        when(reactiveWorkspaceMemberRepository.findByWorkspaceIdAndUserId(1L, 2L)).thenReturn(Mono.just(adminMember));
        when(reactiveWorkspaceMemberRepository.existsByWorkspaceIdAndUserId(1L, 10L)).thenReturn(Mono.just(false));
        when(reactiveWorkspaceMemberRepository.save(any(WorkspaceMember.class))).thenReturn(Mono.just(newMember));

        // when
        // then
        webTestClient.post()
                .uri("/api/workspaces/{workspaceId}/members/invite", 1L)
                .header("Authorization", VALID_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.userId").isEqualTo(10L)
                .jsonPath("$.role").isEqualTo(WorkspaceRole.MEMBER.name());
    }

    @Test
    @DisplayName("멤버 초대 실패 - 이미 존재하는 멤버")
    void inviteMember_failure_alreadyExists() {
        // given
        WorkspaceMemberInviteRequestDto request = WorkspaceMemberInviteRequestDto.builder()
                .userId(3L) // 이미 존재하는 멤버
                .role(WorkspaceRole.MEMBER)
                .build();

        when(jwtTokenProvider.getAuthentication(anyString())).thenReturn(adminAuth);
        when(reactiveWorkspaceRepository.findById(1L)).thenReturn(Mono.just(workspace));
        when(reactiveWorkspaceMemberRepository.findByWorkspaceIdAndUserId(1L, 2L)).thenReturn(Mono.just(adminMember));
        when(reactiveWorkspaceMemberRepository.existsByWorkspaceIdAndUserId(1L, 3L)).thenReturn(Mono.just(true));

        // when
        // then
        webTestClient.post()
                .uri("/api/workspaces/{workspaceId}/members/invite", 1L)
                .header("Authorization", VALID_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.CONFLICT)
                .expectBody()
                .jsonPath("$.message").isEqualTo(ErrorCode.MEMBER_ALREADY_EXISTS.getMessage());
    }


    @Test
    @DisplayName("멤버 목록 조회 성공 - MEMBER 권한으로 조회")
    void getWorkspaceMembers_success() {
        // given
        List<WorkspaceMember> members = List.of(ownerMember, adminMember, regularMember);

        when(jwtTokenProvider.getAuthentication(anyString())).thenReturn(memberAuth);
        when(reactiveWorkspaceRepository.findById(1L)).thenReturn(Mono.just(workspace));
        when(reactiveWorkspaceMemberRepository.existsByWorkspaceIdAndUserId(1L, 3L)).thenReturn(Mono.just(true));
        when(reactiveWorkspaceMemberRepository.findByWorkspaceId(1L)).thenReturn(reactor.core.publisher.Flux.fromIterable(members));

        // when
        // then
        webTestClient.get()
                .uri("/api/workspaces/{workspaceId}/members", 1L)
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
    @DisplayName("멤버 목록 조회 실패 - 권한 없음")
    void getWorkspaceMembers_failure_permissionDenied() {
        // given
        CustomUserDetails uninvitedUser = new CustomUserDetails(99L, "uninvited", "pass", "ROLE_USER", "uninvited@example.com");
        Authentication uninvitedAuth = new UsernamePasswordAuthenticationToken(uninvitedUser, null, Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")));

        when(jwtTokenProvider.getAuthentication(anyString())).thenReturn(uninvitedAuth);
        when(reactiveWorkspaceRepository.findById(1L)).thenReturn(Mono.just(workspace));
        when(reactiveWorkspaceMemberRepository.existsByWorkspaceIdAndUserId(1L, 99L)).thenReturn(Mono.just(false));

        // when
        // then
        webTestClient.get()
                .uri("/api/workspaces/{workspaceId}/members", 1L)
                .header("Authorization", VALID_TOKEN)
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.message").isEqualTo(ErrorCode.PERMISSION_DENIED.getMessage());
    }


    @Test
    @DisplayName("멤버 역할 수정 성공 - ADMIN이 MEMBER의 역할을 ADMIN으로 변경")
    void updateMemberRole_success() {
        // given
        WorkspaceMemberRoleUpdateRequestDto request = WorkspaceMemberRoleUpdateRequestDto.builder()
                .userId(3L) // regularMember
                .role(WorkspaceRole.ADMIN)
                .build();

        WorkspaceMember updatedMember = WorkspaceMember.builder()
                .id(3L)
                .workspaceId(1L)
                .userId(3L)
                .role(WorkspaceRole.ADMIN)
                .joinedAt(regularMember.getJoinedAt())
                .build();

        when(jwtTokenProvider.getAuthentication(anyString())).thenReturn(adminAuth);
        when(reactiveWorkspaceRepository.findById(1L)).thenReturn(Mono.just(workspace));
        when(reactiveWorkspaceMemberRepository.findByWorkspaceIdAndUserId(1L, 2L)).thenReturn(Mono.just(adminMember));
        when(reactiveWorkspaceMemberRepository.findByWorkspaceIdAndUserId(1L, 3L)).thenReturn(Mono.just(regularMember));
        when(reactiveWorkspaceMemberRepository.save(any(WorkspaceMember.class))).thenReturn(Mono.just(updatedMember));

        // when
        // then
        webTestClient.put()
                .uri("/api/workspaces/{workspaceId}/members/role", 1L)
                .header("Authorization", VALID_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.userId").isEqualTo(3L)
                .jsonPath("$.role").isEqualTo(WorkspaceRole.ADMIN.name());
    }

    @Test
    @DisplayName("멤버 역할 수정 실패 - 소유자의 역할은 변경할 수 없음")
    void updateMemberRole_failure_cannotChangeOwnerRole() {
        // given
        WorkspaceMemberRoleUpdateRequestDto request = WorkspaceMemberRoleUpdateRequestDto.builder()
                .userId(1L) // ownerMember
                .role(WorkspaceRole.MEMBER)
                .build();

        when(jwtTokenProvider.getAuthentication(anyString())).thenReturn(adminAuth);
        when(reactiveWorkspaceRepository.findById(1L)).thenReturn(Mono.just(workspace));
        when(reactiveWorkspaceMemberRepository.findByWorkspaceIdAndUserId(1L, 2L)).thenReturn(Mono.just(adminMember));
        when(reactiveWorkspaceMemberRepository.findByWorkspaceIdAndUserId(1L, 1L)).thenReturn(Mono.just(ownerMember));

        // when
        // then
        webTestClient.put()
                .uri("/api/workspaces/{workspaceId}/members/role", 1L)
                .header("Authorization", VALID_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.message").isEqualTo(ErrorCode.CANNOT_CHANGE_OWNER_ROLE.getMessage());
    }


    @Test
    @DisplayName("멤버 삭제 성공 - OWNER가 다른 멤버 삭제")
    void removeMember_success_asOwner() {
        // given
        when(jwtTokenProvider.getAuthentication(anyString())).thenReturn(ownerAuth);
        when(reactiveWorkspaceMemberRepository.findByWorkspaceIdAndUserId(1L, 1L)).thenReturn(Mono.just(ownerMember));
        when(reactiveWorkspaceMemberRepository.existsByWorkspaceIdAndUserId(1L, 3L)).thenReturn(Mono.just(true));
        when(reactiveWorkspaceMemberRepository.deleteByWorkspaceIdAndUserId(1L, 3L)).thenReturn(Mono.empty());

        // when
        // then
        webTestClient.delete()
                .uri("/api/workspaces/{workspaceId}/members/{userId}", 1L, 3L)
                .header("Authorization", VALID_TOKEN)
                .exchange()
                .expectStatus().isNoContent();
    }

    @Test
    @DisplayName("멤버 삭제 실패 - OWNER가 자기 자신을 삭제 시도")
    void removeMember_failure_ownerRemovesSelf() {
        // given
        when(jwtTokenProvider.getAuthentication(anyString())).thenReturn(ownerAuth);
        when(reactiveWorkspaceMemberRepository.findByWorkspaceIdAndUserId(1L, 1L)).thenReturn(Mono.just(ownerMember));

        // when
        // then
        webTestClient.delete()
                .uri("/api/workspaces/{workspaceId}/members/{userId}", 1L, 1L)
                .header("Authorization", VALID_TOKEN)
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.message").isEqualTo(ErrorCode.CANNOT_REMOVE_OWNER.getMessage());
    }

    @Test
    @DisplayName("멤버 삭제 실패 - 권한 없음 (ADMIN이 삭제 시도)")
    void removeMember_failure_permissionDenied() {
        // given
        when(jwtTokenProvider.getAuthentication(anyString())).thenReturn(adminAuth);
        when(reactiveWorkspaceMemberRepository.findByWorkspaceIdAndUserId(1L, 2L)).thenReturn(Mono.just(adminMember));

        // when
        // then
        webTestClient.delete()
                .uri("/api/workspaces/{workspaceId}/members/{userId}", 1L, 3L)
                .header("Authorization", VALID_TOKEN)
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.message").isEqualTo(ErrorCode.PERMISSION_DENIED.getMessage());
    }
}
