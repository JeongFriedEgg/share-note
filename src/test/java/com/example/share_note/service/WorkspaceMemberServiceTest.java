package com.example.share_note.service;


import com.example.share_note.domain.Workspace;
import com.example.share_note.domain.WorkspaceMember;
import com.example.share_note.dto.CustomUserDetails;
import com.example.share_note.dto.workspacemember.WorkspaceMemberInviteRequestDto;
import com.example.share_note.dto.workspacemember.WorkspaceMemberRoleUpdateRequestDto;
import com.example.share_note.enums.WorkspaceRole;
import com.example.share_note.exception.WorkspaceException;
import com.example.share_note.exception.WorkspaceMemberException;
import com.example.share_note.repository.ReactiveWorkspaceMemberRepository;
import com.example.share_note.repository.ReactiveWorkspaceRepository;
import com.example.share_note.service.impl.WorkspaceMemberServiceImpl;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class WorkspaceMemberServiceTest {

    @Mock
    private ReactiveWorkspaceMemberRepository reactiveWorkspaceMemberRepository;

    @Mock
    private ReactiveWorkspaceRepository reactiveWorkspaceRepository;

    @Mock
    private UuidUtils uuidUtils;

    @InjectMocks
    private WorkspaceMemberServiceImpl workspaceMemberService;

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
    private WorkspaceMember normalMember;

    private SecurityContext securityContext;
    private Authentication authentication;

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

        ownerDetails = new CustomUserDetails(
                ownerId, "owner", "password", "ROLE_USER", "owner@example.com"
        );
        adminDetails = new CustomUserDetails(
                adminId, "admin", "password", "ROLE_USER", "admin@example.com"
        );
        memberDetails = new CustomUserDetails(
                memberId, "member", "password", "ROLE_USER", "member@example.com"
        );

        securityContext = mock(SecurityContext.class);
        authentication = mock(Authentication.class);
        when(securityContext.getAuthentication()).thenReturn(authentication);

        workspace = Workspace.builder()
                .id(workspaceId)
                .name("Test Workspace")
                .createdBy(ownerId) // owner가 워크스페이스 소유자
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

        normalMember = WorkspaceMember.builder()
                .id(UUID.randomUUID())
                .workspaceId(workspaceId)
                .userId(memberId)
                .role(WorkspaceRole.MEMBER)
                .joinedAt(LocalDateTime.now())
                .build();
    }

    @Test
    @Order(1)
    @DisplayName("멤버 초대 성공 - 워크스페이스 소유자")
    void inviteMember_Success_WorkspaceOwner() {
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

        when(authentication.getPrincipal()).thenReturn(ownerDetails);
        when(uuidUtils.fromString(workspaceIdStr)).thenReturn(workspaceId);
        when(uuidUtils.fromString(inviteUserIdStr)).thenReturn(inviteUserId);
        when(reactiveWorkspaceRepository.findById(workspaceId)).thenReturn(Mono.just(workspace));
        when(reactiveWorkspaceMemberRepository.existsByWorkspaceIdAndUserId(workspaceId, inviteUserId))
                .thenReturn(Mono.just(false));
        when(reactiveWorkspaceMemberRepository.save(any(WorkspaceMember.class))).thenReturn(Mono.just(newMember));

        try (MockedStatic<ReactiveSecurityContextHolder> mockedSecurityContext =
                     mockStatic(ReactiveSecurityContextHolder.class)) {

            mockedSecurityContext.when(ReactiveSecurityContextHolder::getContext)
                    .thenReturn(Mono.just(securityContext));

            // when & then
            StepVerifier.create(workspaceMemberService.inviteMember(workspaceIdStr, request))
                    .expectNextMatches(response -> {
                        assertThat(response.getUserId()).isEqualTo(inviteUserIdStr);
                        assertThat(response.getRole()).isEqualTo(WorkspaceRole.MEMBER);
                        return true;
                    })
                    .verifyComplete();
        }

        verify(reactiveWorkspaceMemberRepository).save(any(WorkspaceMember.class));
    }

    @Test
    @Order(2)
    @DisplayName("멤버 초대 성공 - 워크스페이스 어드민")
    void inviteMember_Success_WorkspaceAdmin() {
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

        when(authentication.getPrincipal()).thenReturn(adminDetails);
        when(uuidUtils.fromString(workspaceIdStr)).thenReturn(workspaceId);
        when(uuidUtils.fromString(inviteUserIdStr)).thenReturn(inviteUserId);
        when(reactiveWorkspaceRepository.findById(workspaceId)).thenReturn(Mono.just(workspace));
        when(reactiveWorkspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, adminId))
                .thenReturn(Mono.just(adminMember));
        when(reactiveWorkspaceMemberRepository.existsByWorkspaceIdAndUserId(workspaceId, inviteUserId))
                .thenReturn(Mono.just(false));
        when(reactiveWorkspaceMemberRepository.save(any(WorkspaceMember.class))).thenReturn(Mono.just(newMember));

        try (MockedStatic<ReactiveSecurityContextHolder> mockedSecurityContext =
                     mockStatic(ReactiveSecurityContextHolder.class)) {

            mockedSecurityContext.when(ReactiveSecurityContextHolder::getContext)
                    .thenReturn(Mono.just(securityContext));

            // when & then
            StepVerifier.create(workspaceMemberService.inviteMember(workspaceIdStr, request))
                    .expectNextCount(1)
                    .verifyComplete();
        }
    }

    @Test
    @Order(3)
    @DisplayName("멤버 초대 실패 - 워크스페이스 없음")
    void inviteMember_Fail_WorkspaceNotFound() {
        // given
        WorkspaceMemberInviteRequestDto request = WorkspaceMemberInviteRequestDto.builder()
                .userId(inviteUserIdStr)
                .role(WorkspaceRole.MEMBER)
                .build();

        when(authentication.getPrincipal()).thenReturn(ownerDetails);
        when(uuidUtils.fromString(workspaceIdStr)).thenReturn(workspaceId);
        when(uuidUtils.fromString(inviteUserIdStr)).thenReturn(inviteUserId);
        when(reactiveWorkspaceRepository.findById(workspaceId)).thenReturn(Mono.empty());

        try (MockedStatic<ReactiveSecurityContextHolder> mockedSecurityContext =
                     mockStatic(ReactiveSecurityContextHolder.class)) {

            mockedSecurityContext.when(ReactiveSecurityContextHolder::getContext)
                    .thenReturn(Mono.just(securityContext));

            // when & then
            StepVerifier.create(workspaceMemberService.inviteMember(workspaceIdStr, request))
                    .expectError(WorkspaceException.class)
                    .verify();
        }
    }

    @Test
    @Order(4)
    @DisplayName("멤버 초대 실패 - 권한 없음 (일반 멤버)")
    void inviteMember_Fail_PermissionDenied() {
        // given
        WorkspaceMemberInviteRequestDto request = WorkspaceMemberInviteRequestDto.builder()
                .userId(inviteUserIdStr)
                .role(WorkspaceRole.MEMBER)
                .build();

        when(authentication.getPrincipal()).thenReturn(memberDetails);
        when(uuidUtils.fromString(workspaceIdStr)).thenReturn(workspaceId);
        when(uuidUtils.fromString(inviteUserIdStr)).thenReturn(inviteUserId);
        when(reactiveWorkspaceRepository.findById(workspaceId)).thenReturn(Mono.just(workspace));
        when(reactiveWorkspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, memberId))
                .thenReturn(Mono.just(normalMember));

        try (MockedStatic<ReactiveSecurityContextHolder> mockedSecurityContext =
                     mockStatic(ReactiveSecurityContextHolder.class)) {

            mockedSecurityContext.when(ReactiveSecurityContextHolder::getContext)
                    .thenReturn(Mono.just(securityContext));

            // when & then
            StepVerifier.create(workspaceMemberService.inviteMember(workspaceIdStr, request))
                    .expectError(WorkspaceException.class)
                    .verify();
        }
    }

    @Test
    @Order(5)
    @DisplayName("멤버 초대 실패 - 이미 존재하는 멤버")
    void inviteMember_Fail_MemberAlreadyExists() {
        // given
        WorkspaceMemberInviteRequestDto request = WorkspaceMemberInviteRequestDto.builder()
                .userId(inviteUserIdStr)
                .role(WorkspaceRole.MEMBER)
                .build();

        when(authentication.getPrincipal()).thenReturn(ownerDetails);
        when(uuidUtils.fromString(workspaceIdStr)).thenReturn(workspaceId);
        when(uuidUtils.fromString(inviteUserIdStr)).thenReturn(inviteUserId);
        when(reactiveWorkspaceRepository.findById(workspaceId)).thenReturn(Mono.just(workspace));
        when(reactiveWorkspaceMemberRepository.existsByWorkspaceIdAndUserId(workspaceId, inviteUserId))
                .thenReturn(Mono.just(true)); // 이미 존재

        try (MockedStatic<ReactiveSecurityContextHolder> mockedSecurityContext =
                     mockStatic(ReactiveSecurityContextHolder.class)) {

            mockedSecurityContext.when(ReactiveSecurityContextHolder::getContext)
                    .thenReturn(Mono.just(securityContext));

            // when & then
            StepVerifier.create(workspaceMemberService.inviteMember(workspaceIdStr, request))
                    .expectError(WorkspaceMemberException.class)
                    .verify();
        }
    }

    @Test
    @Order(6)
    @DisplayName("멤버 목록 조회 성공 - 워크스페이스 소유자")
    void getWorkspaceMembers_Success_WorkspaceOwner() {
        // given
        when(authentication.getPrincipal()).thenReturn(ownerDetails);
        when(uuidUtils.fromString(workspaceIdStr)).thenReturn(workspaceId);
        when(reactiveWorkspaceRepository.findById(workspaceId)).thenReturn(Mono.just(workspace));
        when(reactiveWorkspaceMemberRepository.findByWorkspaceId(workspaceId))
                .thenReturn(Flux.just(ownerMember, adminMember, normalMember));

        try (MockedStatic<ReactiveSecurityContextHolder> mockedSecurityContext =
                     mockStatic(ReactiveSecurityContextHolder.class)) {

            mockedSecurityContext.when(ReactiveSecurityContextHolder::getContext)
                    .thenReturn(Mono.just(securityContext));

            // when & then
            StepVerifier.create(workspaceMemberService.getWorkspaceMembers(workspaceIdStr))
                    .expectNextMatches(response -> {
                        assertThat(response.getMembers()).hasSize(3);
                        assertThat(response.getTotalCount()).isEqualTo(3);
                        return true;
                    })
                    .verifyComplete();
        }
    }

    @Test
    @Order(7)
    @DisplayName("멤버 목록 조회 성공 - 워크스페이스 멤버")
    void getWorkspaceMembers_Success_WorkspaceMember() {
        // given
        when(authentication.getPrincipal()).thenReturn(memberDetails);
        when(uuidUtils.fromString(workspaceIdStr)).thenReturn(workspaceId);
        when(reactiveWorkspaceRepository.findById(workspaceId)).thenReturn(Mono.just(workspace));
        when(reactiveWorkspaceMemberRepository.existsByWorkspaceIdAndUserId(workspaceId, memberId))
                .thenReturn(Mono.just(true));
        when(reactiveWorkspaceMemberRepository.findByWorkspaceId(workspaceId))
                .thenReturn(Flux.just(ownerMember, adminMember, normalMember));

        try (MockedStatic<ReactiveSecurityContextHolder> mockedSecurityContext =
                     mockStatic(ReactiveSecurityContextHolder.class)) {

            mockedSecurityContext.when(ReactiveSecurityContextHolder::getContext)
                    .thenReturn(Mono.just(securityContext));

            // when & then
            StepVerifier.create(workspaceMemberService.getWorkspaceMembers(workspaceIdStr))
                    .expectNextMatches(response -> {
                        assertThat(response.getTotalCount()).isEqualTo(3);
                        return true;
                    })
                    .verifyComplete();
        }
    }

    @Test
    @Order(8)
    @DisplayName("멤버 목록 조회 실패 - 권한 없음 (비멤버)")
    void getWorkspaceMembers_Fail_PermissionDenied() {
        // given
        UUID nonMemberId = UUID.randomUUID();
        CustomUserDetails nonMemberDetails = new CustomUserDetails(
                nonMemberId, "nonmember", "password", "ROLE_USER", "nonmember@example.com"
        );

        when(authentication.getPrincipal()).thenReturn(nonMemberDetails);
        when(uuidUtils.fromString(workspaceIdStr)).thenReturn(workspaceId);
        when(reactiveWorkspaceRepository.findById(workspaceId)).thenReturn(Mono.just(workspace));
        when(reactiveWorkspaceMemberRepository.existsByWorkspaceIdAndUserId(workspaceId, nonMemberId))
                .thenReturn(Mono.just(false));

        try (MockedStatic<ReactiveSecurityContextHolder> mockedSecurityContext =
                     mockStatic(ReactiveSecurityContextHolder.class)) {

            mockedSecurityContext.when(ReactiveSecurityContextHolder::getContext)
                    .thenReturn(Mono.just(securityContext));

            // when & then
            StepVerifier.create(workspaceMemberService.getWorkspaceMembers(workspaceIdStr))
                    .expectError(WorkspaceException.class)
                    .verify();
        }
    }

    @Test
    @Order(9)
    @DisplayName("멤버 역할 변경 성공 - 워크스페이스 소유자")
    void updateMemberRole_Success_WorkspaceOwner() {
        // given
        WorkspaceMemberRoleUpdateRequestDto request = WorkspaceMemberRoleUpdateRequestDto.builder()
                .userId(memberIdStr)
                .role(WorkspaceRole.ADMIN)
                .build();

        WorkspaceMember updatedMember = WorkspaceMember.builder()
                .id(normalMember.getId())
                .workspaceId(workspaceId)
                .userId(memberId)
                .role(WorkspaceRole.ADMIN)
                .joinedAt(normalMember.getJoinedAt())
                .build();

        when(authentication.getPrincipal()).thenReturn(ownerDetails);
        when(uuidUtils.fromString(workspaceIdStr)).thenReturn(workspaceId);
        when(uuidUtils.fromString(memberIdStr)).thenReturn(memberId);
        when(reactiveWorkspaceRepository.findById(workspaceId)).thenReturn(Mono.just(workspace));
        when(reactiveWorkspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, memberId))
                .thenReturn(Mono.just(normalMember));
        when(reactiveWorkspaceMemberRepository.save(any(WorkspaceMember.class))).thenReturn(Mono.just(updatedMember));

        try (MockedStatic<ReactiveSecurityContextHolder> mockedSecurityContext =
                     mockStatic(ReactiveSecurityContextHolder.class)) {

            mockedSecurityContext.when(ReactiveSecurityContextHolder::getContext)
                    .thenReturn(Mono.just(securityContext));

            // when & then
            StepVerifier.create(workspaceMemberService.updateMemberRole(workspaceIdStr, request))
                    .expectNextMatches(response -> {
                        assertThat(response.getRole()).isEqualTo(WorkspaceRole.ADMIN);
                        return true;
                    })
                    .verifyComplete();
        }

        verify(reactiveWorkspaceMemberRepository).save(any(WorkspaceMember.class));
    }

    @Test
    @Order(10)
    @DisplayName("멤버 역할 변경 실패 - 권한 없음 (일반 멤버)")
    void updateMemberRole_Fail_PermissionDenied() {
        // given
        WorkspaceMemberRoleUpdateRequestDto request = WorkspaceMemberRoleUpdateRequestDto.builder()
                .userId(adminIdStr)
                .role(WorkspaceRole.MEMBER)
                .build();

        when(authentication.getPrincipal()).thenReturn(memberDetails);
        when(uuidUtils.fromString(workspaceIdStr)).thenReturn(workspaceId);
        when(reactiveWorkspaceRepository.findById(workspaceId)).thenReturn(Mono.just(workspace));
        when(reactiveWorkspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, memberId))
                .thenReturn(Mono.just(normalMember));

        try (MockedStatic<ReactiveSecurityContextHolder> mockedSecurityContext =
                     mockStatic(ReactiveSecurityContextHolder.class)) {

            mockedSecurityContext.when(ReactiveSecurityContextHolder::getContext)
                    .thenReturn(Mono.just(securityContext));

            // when & then
            StepVerifier.create(workspaceMemberService.updateMemberRole(workspaceIdStr, request))
                    .expectError(WorkspaceException.class)
                    .verify();
        }
    }

    @Test
    @Order(11)
    @DisplayName("멤버 역할 변경 실패 - 소유자 역할 변경 시도")
    void updateMemberRole_Fail_CannotChangeOwnerRole() {
        // given
        WorkspaceMemberRoleUpdateRequestDto request = WorkspaceMemberRoleUpdateRequestDto.builder()
                .userId(ownerIdStr)
                .role(WorkspaceRole.ADMIN)
                .build();

        when(authentication.getPrincipal()).thenReturn(ownerDetails);
        when(uuidUtils.fromString(workspaceIdStr)).thenReturn(workspaceId);
        when(uuidUtils.fromString(ownerIdStr)).thenReturn(ownerId);
        when(reactiveWorkspaceRepository.findById(workspaceId)).thenReturn(Mono.just(workspace));
        when(reactiveWorkspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, ownerId))
                .thenReturn(Mono.just(ownerMember));

        try (MockedStatic<ReactiveSecurityContextHolder> mockedSecurityContext =
                     mockStatic(ReactiveSecurityContextHolder.class)) {

            mockedSecurityContext.when(ReactiveSecurityContextHolder::getContext)
                    .thenReturn(Mono.just(securityContext));

            // when & then
            StepVerifier.create(workspaceMemberService.updateMemberRole(workspaceIdStr, request))
                    .expectError(WorkspaceMemberException.class)
                    .verify();
        }
    }

    @Test
    @Order(12)
    @DisplayName("멤버 역할 변경 실패 - 멤버 없음")
    void updateMemberRole_Fail_MemberNotFound() {
        // given
        WorkspaceMemberRoleUpdateRequestDto request = WorkspaceMemberRoleUpdateRequestDto.builder()
                .userId(inviteUserIdStr)
                .role(WorkspaceRole.ADMIN)
                .build();

        when(authentication.getPrincipal()).thenReturn(ownerDetails);
        when(uuidUtils.fromString(workspaceIdStr)).thenReturn(workspaceId);
        when(uuidUtils.fromString(inviteUserIdStr)).thenReturn(inviteUserId);
        when(reactiveWorkspaceRepository.findById(workspaceId)).thenReturn(Mono.just(workspace));
        when(reactiveWorkspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, inviteUserId))
                .thenReturn(Mono.empty());

        try (MockedStatic<ReactiveSecurityContextHolder> mockedSecurityContext =
                     mockStatic(ReactiveSecurityContextHolder.class)) {

            mockedSecurityContext.when(ReactiveSecurityContextHolder::getContext)
                    .thenReturn(Mono.just(securityContext));

            // when & then
            StepVerifier.create(workspaceMemberService.updateMemberRole(workspaceIdStr, request))
                    .expectError(WorkspaceMemberException.class)
                    .verify();
        }
    }

    @Test
    @Order(13)
    @DisplayName("멤버 제거 성공 - 워크스페이스 소유자")
    void removeMember_Success_WorkspaceOwner() {
        // given
        when(authentication.getPrincipal()).thenReturn(ownerDetails);
        when(uuidUtils.fromString(workspaceIdStr)).thenReturn(workspaceId);
        when(uuidUtils.fromString(memberIdStr)).thenReturn(memberId);
        when(reactiveWorkspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, ownerId))
                .thenReturn(Mono.just(ownerMember));
        when(reactiveWorkspaceMemberRepository.existsByWorkspaceIdAndUserId(workspaceId, memberId))
                .thenReturn(Mono.just(true));
        when(reactiveWorkspaceMemberRepository.deleteByWorkspaceIdAndUserId(workspaceId, memberId))
                .thenReturn(Mono.empty());

        try (MockedStatic<ReactiveSecurityContextHolder> mockedSecurityContext =
                     mockStatic(ReactiveSecurityContextHolder.class)) {

            mockedSecurityContext.when(ReactiveSecurityContextHolder::getContext)
                    .thenReturn(Mono.just(securityContext));

            // when & then
            StepVerifier.create(workspaceMemberService.removeMember(workspaceIdStr, memberIdStr))
                    .verifyComplete();
        }
    }

    @Test
    @Order(14)
    @DisplayName("멤버 제거 실패 - 권한 없음 (어드민이 아님)")
    void removeMember_Fail_PermissionDenied() {
        // given
        when(authentication.getPrincipal()).thenReturn(memberDetails);
        when(uuidUtils.fromString(workspaceIdStr)).thenReturn(workspaceId);
        when(uuidUtils.fromString(adminIdStr)).thenReturn(adminId);
        when(reactiveWorkspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, memberId))
                .thenReturn(Mono.just(normalMember));

        try (MockedStatic<ReactiveSecurityContextHolder> mockedSecurityContext =
                     mockStatic(ReactiveSecurityContextHolder.class)) {

            mockedSecurityContext.when(ReactiveSecurityContextHolder::getContext)
                    .thenReturn(Mono.just(securityContext));

            // when & then
            StepVerifier.create(workspaceMemberService.removeMember(workspaceIdStr, adminIdStr))
                    .expectError(WorkspaceException.class)
                    .verify();
        }
    }

    @Test
    @Order(15)
    @DisplayName("멤버 제거 실패 - 소유자 자신을 제거하려고 시도")
    void removeMember_Fail_CannotRemoveOwner() {
        // given
        when(authentication.getPrincipal()).thenReturn(ownerDetails);
        when(uuidUtils.fromString(workspaceIdStr)).thenReturn(workspaceId);
        when(uuidUtils.fromString(ownerIdStr)).thenReturn(ownerId);
        when(reactiveWorkspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, ownerId))
                .thenReturn(Mono.just(ownerMember));

        try (MockedStatic<ReactiveSecurityContextHolder> mockedSecurityContext =
                     mockStatic(ReactiveSecurityContextHolder.class)) {

            mockedSecurityContext.when(ReactiveSecurityContextHolder::getContext)
                    .thenReturn(Mono.just(securityContext));

            // when & then
            StepVerifier.create(workspaceMemberService.removeMember(workspaceIdStr, ownerIdStr))
                    .expectError(WorkspaceMemberException.class)
                    .verify();
        }
    }

    @Test
    @Order(16)
    @DisplayName("멤버 제거 실패 - 제거할 멤버가 존재하지 않음")
    void removeMember_Fail_MemberNotFound() {
        // given
        when(authentication.getPrincipal()).thenReturn(ownerDetails);
        when(uuidUtils.fromString(workspaceIdStr)).thenReturn(workspaceId);
        when(uuidUtils.fromString(inviteUserIdStr)).thenReturn(inviteUserId);
        when(reactiveWorkspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, ownerId))
                .thenReturn(Mono.just(ownerMember));
        when(reactiveWorkspaceMemberRepository.existsByWorkspaceIdAndUserId(workspaceId, inviteUserId))
                .thenReturn(Mono.just(false));

        try (MockedStatic<ReactiveSecurityContextHolder> mockedSecurityContext =
                     mockStatic(ReactiveSecurityContextHolder.class)) {

            mockedSecurityContext.when(ReactiveSecurityContextHolder::getContext)
                    .thenReturn(Mono.just(securityContext));

            // when & then
            StepVerifier.create(workspaceMemberService.removeMember(workspaceIdStr, inviteUserIdStr))
                    .expectError(WorkspaceMemberException.class)
                    .verify();
        }
    }

    @Test
    @Order(17)
    @DisplayName("멤버 제거 실패 - 요청자가 워크스페이스 멤버가 아님")
    void removeMember_Fail_RequesterNotMember() {
        // given
        UUID nonMemberId = UUID.randomUUID();
        CustomUserDetails nonMemberDetails = new CustomUserDetails(
                nonMemberId, "nonmember", "password", "ROLE_USER", "nonmember@example.com"
        );

        when(authentication.getPrincipal()).thenReturn(nonMemberDetails);
        when(uuidUtils.fromString(workspaceIdStr)).thenReturn(workspaceId);
        when(uuidUtils.fromString(memberIdStr)).thenReturn(memberId);
        when(reactiveWorkspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, nonMemberId))
                .thenReturn(Mono.empty());

        try (MockedStatic<ReactiveSecurityContextHolder> mockedSecurityContext =
                     mockStatic(ReactiveSecurityContextHolder.class)) {

            mockedSecurityContext.when(ReactiveSecurityContextHolder::getContext)
                    .thenReturn(Mono.just(securityContext));

            // when & then
            StepVerifier.create(workspaceMemberService.removeMember(workspaceIdStr, memberIdStr))
                    .expectError(WorkspaceMemberException.class)
                    .verify();
        }
    }

    @Test
    @Order(18)
    @DisplayName("어드민 권한 확인 - 워크스페이스 소유자가 아닌 어드민 멤버")
    void checkAdminPermission_AdminMember() {
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

        // 워크스페이스 소유자가 아닌 다른 사용자가 만든 워크스페이스
        Workspace otherUserWorkspace = Workspace.builder()
                .id(workspaceId)
                .name("Test Workspace")
                .createdBy(UUID.randomUUID()) // 다른 사용자가 소유자
                .build();

        when(authentication.getPrincipal()).thenReturn(adminDetails);
        when(uuidUtils.fromString(workspaceIdStr)).thenReturn(workspaceId);
        when(uuidUtils.fromString(inviteUserIdStr)).thenReturn(inviteUserId);
        when(reactiveWorkspaceRepository.findById(workspaceId)).thenReturn(Mono.just(otherUserWorkspace));
        when(reactiveWorkspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, adminId))
                .thenReturn(Mono.just(adminMember));
        when(reactiveWorkspaceMemberRepository.existsByWorkspaceIdAndUserId(workspaceId, inviteUserId))
                .thenReturn(Mono.just(false));
        when(reactiveWorkspaceMemberRepository.save(any(WorkspaceMember.class))).thenReturn(Mono.just(newMember));

        try (MockedStatic<ReactiveSecurityContextHolder> mockedSecurityContext =
                     mockStatic(ReactiveSecurityContextHolder.class)) {

            mockedSecurityContext.when(ReactiveSecurityContextHolder::getContext)
                    .thenReturn(Mono.just(securityContext));

            // when & then
            StepVerifier.create(workspaceMemberService.inviteMember(workspaceIdStr, request))
                    .expectNextCount(1)
                    .verifyComplete();
        }
    }

    @Test
    @Order(19)
    @DisplayName("어드민 권한 확인 - 비멤버는 권한 없음")
    void checkAdminPermission_NonMember() {
        // given
        UUID nonMemberId = UUID.randomUUID();
        CustomUserDetails nonMemberDetails = new CustomUserDetails(
                nonMemberId, "nonmember", "password", "ROLE_USER", "nonmember@example.com"
        );

        WorkspaceMemberInviteRequestDto request = WorkspaceMemberInviteRequestDto.builder()
                .userId(inviteUserIdStr)
                .role(WorkspaceRole.MEMBER)
                .build();

        // 워크스페이스 소유자가 아닌 다른 사용자가 만든 워크스페이스
        Workspace otherUserWorkspace = Workspace.builder()
                .id(workspaceId)
                .name("Test Workspace")
                .createdBy(UUID.randomUUID())
                .build();

        when(authentication.getPrincipal()).thenReturn(nonMemberDetails);
        when(uuidUtils.fromString(workspaceIdStr)).thenReturn(workspaceId);
        when(uuidUtils.fromString(inviteUserIdStr)).thenReturn(inviteUserId);
        when(reactiveWorkspaceRepository.findById(workspaceId)).thenReturn(Mono.just(otherUserWorkspace));
        when(reactiveWorkspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, nonMemberId))
                .thenReturn(Mono.empty()); // 비멤버

        try (MockedStatic<ReactiveSecurityContextHolder> mockedSecurityContext =
                     mockStatic(ReactiveSecurityContextHolder.class)) {

            mockedSecurityContext.when(ReactiveSecurityContextHolder::getContext)
                    .thenReturn(Mono.just(securityContext));

            // when & then
            StepVerifier.create(workspaceMemberService.inviteMember(workspaceIdStr, request))
                    .expectError(WorkspaceException.class)
                    .verify();
        }
    }

    @Test
    @Order(20)
    @DisplayName("멤버 권한 확인 - 비소유자이지만 멤버인 경우")
    void checkMemberPermission_NonOwnerButMember() {
        // given
        // 워크스페이스 소유자가 아닌 다른 사용자가 만든 워크스페이스
        Workspace otherUserWorkspace = Workspace.builder()
                .id(workspaceId)
                .name("Test Workspace")
                .createdBy(UUID.randomUUID())
                .build();

        when(authentication.getPrincipal()).thenReturn(memberDetails);
        when(uuidUtils.fromString(workspaceIdStr)).thenReturn(workspaceId);
        when(reactiveWorkspaceRepository.findById(workspaceId)).thenReturn(Mono.just(otherUserWorkspace));
        when(reactiveWorkspaceMemberRepository.existsByWorkspaceIdAndUserId(workspaceId, memberId))
                .thenReturn(Mono.just(true));
        when(reactiveWorkspaceMemberRepository.findByWorkspaceId(workspaceId))
                .thenReturn(Flux.just(ownerMember, adminMember, normalMember));

        try (MockedStatic<ReactiveSecurityContextHolder> mockedSecurityContext =
                     mockStatic(ReactiveSecurityContextHolder.class)) {

            mockedSecurityContext.when(ReactiveSecurityContextHolder::getContext)
                    .thenReturn(Mono.just(securityContext));

            // when & then
            StepVerifier.create(workspaceMemberService.getWorkspaceMembers(workspaceIdStr))
                    .expectNextCount(1)
                    .verifyComplete();
        }
    }
}