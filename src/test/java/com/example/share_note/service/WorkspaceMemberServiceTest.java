package com.example.share_note.service;


import com.example.share_note.domain.Workspace;
import com.example.share_note.domain.WorkspaceMember;
import com.example.share_note.dto.CustomUserDetails;
import com.example.share_note.dto.workspacemember.WorkspaceMemberInviteRequestDto;
import com.example.share_note.dto.workspacemember.WorkspaceMemberListResponseDto;
import com.example.share_note.dto.workspacemember.WorkspaceMemberResponseDto;
import com.example.share_note.dto.workspacemember.WorkspaceMemberRoleUpdateRequestDto;
import com.example.share_note.enums.WorkspaceRole;
import com.example.share_note.exception.ErrorCode;
import com.example.share_note.exception.WorkspaceException;
import com.example.share_note.exception.WorkspaceMemberException;
import com.example.share_note.repository.ReactiveWorkspaceMemberRepository;
import com.example.share_note.repository.ReactiveWorkspaceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Arrays;
import java.util.List;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class WorkspaceMemberServiceTest {

    @Mock
    private ReactiveWorkspaceRepository reactiveWorkspaceRepository;

    @Mock
    private ReactiveWorkspaceMemberRepository reactiveWorkspaceMemberRepository;

    @InjectMocks
    private WorkspaceMemberService workspaceMemberService;

    private SecurityContext ownerSecurityContext;
    private SecurityContext adminSecurityContext;
    private SecurityContext memberSecurityContext;
    private Workspace workspace;
    private WorkspaceMember ownerMember;
    private WorkspaceMember adminMember;
    private WorkspaceMember regularMember;

    @BeforeEach
    void setUp() {
        // Mock user details
        CustomUserDetails ownerDetails = new CustomUserDetails(1L, "owner", "password", "ROLE_USER", "owner@example.com");
        CustomUserDetails adminDetails = new CustomUserDetails(2L, "admin", "password", "ROLE_USER", "admin@example.com");
        CustomUserDetails memberDetails = new CustomUserDetails(3L, "member", "password", "ROLE_USER", "member@example.com");

        // Mock security contexts
        ownerSecurityContext = mock(SecurityContext.class);
        Authentication ownerAuth = new UsernamePasswordAuthenticationToken(
                ownerDetails,
                null,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"))
        );
        when(ownerSecurityContext.getAuthentication()).thenReturn(ownerAuth);

        adminSecurityContext = mock(SecurityContext.class);
        Authentication adminAuth = new UsernamePasswordAuthenticationToken(
                adminDetails,
                null,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"))
        );
        lenient().when(adminSecurityContext.getAuthentication()).thenReturn(adminAuth);

        memberSecurityContext = mock(SecurityContext.class);
        Authentication memberAuth = new UsernamePasswordAuthenticationToken(
                memberDetails,
                null,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"))
        );
        lenient().when(memberSecurityContext.getAuthentication()).thenReturn(memberAuth);

        // Mock entities
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
    }

    @Test
    @DisplayName("멤버 초대 성공 - OWNER 권한으로 멤버 초대")
    void inviteMember_success_asOwner() {
        // given
        WorkspaceMemberInviteRequestDto requestDto = WorkspaceMemberInviteRequestDto.builder()
                .userId(10L)
                .role(WorkspaceRole.MEMBER)
                .build();

        WorkspaceMember newMember = WorkspaceMember.builder()
                .workspaceId(1L)
                .userId(10L)
                .role(WorkspaceRole.MEMBER)
                .joinedAt(LocalDateTime.now())
                .build();

        when(reactiveWorkspaceRepository.findById(1L)).thenReturn(Mono.just(workspace));
        when(reactiveWorkspaceMemberRepository.existsByWorkspaceIdAndUserId(1L, 10L)).thenReturn(Mono.just(false));
        when(reactiveWorkspaceMemberRepository.save(any(WorkspaceMember.class)))
                .thenReturn(Mono.just(newMember));

        // when
        Mono<WorkspaceMemberResponseDto> resultMono = workspaceMemberService.inviteMember(1L, requestDto)
                .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(ownerSecurityContext)));

        // then
        StepVerifier.create(resultMono)
                .expectNextMatches(response ->
                        response.getUserId().equals(10L) &&
                                response.getWorkspaceId().equals(1L) &&
                                response.getRole() == WorkspaceRole.MEMBER
                )
                .verifyComplete();

        verify(reactiveWorkspaceMemberRepository).save(any(WorkspaceMember.class));
    }

    @Test
    @DisplayName("멤버 초대 실패 - 워크스페이스가 존재하지 않음")
    void inviteMember_failure_workspaceNotFound() {
        // given
        WorkspaceMemberInviteRequestDto requestDto = WorkspaceMemberInviteRequestDto.builder()
                .userId(10L)
                .role(WorkspaceRole.MEMBER)
                .build();

        when(reactiveWorkspaceRepository.findById(999L)).thenReturn(Mono.empty());

        // when
        Mono<WorkspaceMemberResponseDto> resultMono = workspaceMemberService.inviteMember(999L, requestDto)
                .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(ownerSecurityContext)));

        // then
        StepVerifier.create(resultMono)
                .expectErrorMatches(throwable ->
                        throwable instanceof WorkspaceException &&
                                ((WorkspaceException) throwable).getErrorCode() == ErrorCode.WORKSPACE_NOT_FOUND)
                .verify();
    }

    @Test
    @DisplayName("멤버 초대 실패 - 권한 없음 (일반 멤버)")
    void inviteMember_failure_permissionDenied() {
        // given
        WorkspaceMemberInviteRequestDto requestDto = WorkspaceMemberInviteRequestDto.builder()
                .userId(10L)
                .role(WorkspaceRole.MEMBER)
                .build();

        when(reactiveWorkspaceRepository.findById(1L)).thenReturn(Mono.just(workspace));
        when(reactiveWorkspaceMemberRepository.findByWorkspaceIdAndUserId(1L, 3L)).thenReturn(Mono.just(regularMember));

        // when
        Mono<WorkspaceMemberResponseDto> resultMono = workspaceMemberService.inviteMember(1L, requestDto)
                .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(memberSecurityContext)));

        // then
        StepVerifier.create(resultMono)
                .expectErrorMatches(throwable ->
                        throwable instanceof WorkspaceException &&
                                ((WorkspaceException) throwable).getErrorCode() == ErrorCode.WORKSPACE_PERMISSION_DENIED)
                .verify();
    }

    @Test
    @DisplayName("멤버 초대 실패 - 이미 존재하는 멤버")
    void inviteMember_failure_memberAlreadyExists() {
        // given
        WorkspaceMemberInviteRequestDto requestDto = WorkspaceMemberInviteRequestDto.builder()
                .userId(2L) // 이미 존재하는 관리자
                .role(WorkspaceRole.MEMBER)
                .build();

        when(reactiveWorkspaceRepository.findById(1L)).thenReturn(Mono.just(workspace));
        when(reactiveWorkspaceMemberRepository.existsByWorkspaceIdAndUserId(1L, 2L)).thenReturn(Mono.just(true));

        // when
        Mono<WorkspaceMemberResponseDto> resultMono = workspaceMemberService.inviteMember(1L, requestDto)
                .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(ownerSecurityContext)));

        // then
        StepVerifier.create(resultMono)
                .expectErrorMatches(throwable ->
                        throwable instanceof WorkspaceMemberException &&
                                ((WorkspaceMemberException) throwable).getErrorCode() == ErrorCode.MEMBER_ALREADY_EXISTS)
                .verify();
    }

    @Test
    @DisplayName("워크스페이스 멤버 목록 조회 성공 - OWNER")
    void getWorkspaceMembers_success_asOwner() {
        // given
        List<WorkspaceMember> members = Arrays.asList(ownerMember, adminMember, regularMember);

        when(reactiveWorkspaceRepository.findById(1L)).thenReturn(Mono.just(workspace));
        when(reactiveWorkspaceMemberRepository.findByWorkspaceId(1L)).thenReturn(reactor.core.publisher.Flux.fromIterable(members));

        // when
        Mono<WorkspaceMemberListResponseDto> resultMono = workspaceMemberService.getWorkspaceMembers(1L)
                .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(ownerSecurityContext)));

        // then
        StepVerifier.create(resultMono)
                .expectNextMatches(response ->
                        response.getMembers().size() == 3 &&
                                response.getTotalCount() == 3 &&
                                response.getMembers().stream().anyMatch(m -> m.getUserId().equals(1L) && m.getRole() == WorkspaceRole.OWNER) &&
                                response.getMembers().stream().anyMatch(m -> m.getUserId().equals(2L) && m.getRole() == WorkspaceRole.ADMIN) &&
                                response.getMembers().stream().anyMatch(m -> m.getUserId().equals(3L) && m.getRole() == WorkspaceRole.MEMBER)
                )
                .verifyComplete();
    }

    @Test
    @DisplayName("워크스페이스 멤버 목록 조회 성공 - 멤버 권한")
    void getWorkspaceMembers_success_asMember() {
        // given
        List<WorkspaceMember> members = Arrays.asList(ownerMember, adminMember, regularMember);

        when(reactiveWorkspaceRepository.findById(1L)).thenReturn(Mono.just(workspace));
        when(reactiveWorkspaceMemberRepository.existsByWorkspaceIdAndUserId(1L, 3L)).thenReturn(Mono.just(true));
        when(reactiveWorkspaceMemberRepository.findByWorkspaceId(1L)).thenReturn(reactor.core.publisher.Flux.fromIterable(members));

        // when
        Mono<WorkspaceMemberListResponseDto> resultMono = workspaceMemberService.getWorkspaceMembers(1L)
                .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(memberSecurityContext)));

        // then
        StepVerifier.create(resultMono)
                .expectNextCount(1)
                .verifyComplete();
    }

    @Test
    @DisplayName("워크스페이스 멤버 목록 조회 실패 - 권한 없음 (비멤버)")
    void getWorkspaceMembers_failure_permissionDenied() {
        // given
        CustomUserDetails nonMemberDetails = new CustomUserDetails(4L, "nonmember", "password", "ROLE_USER", "nonmember@example.com");
        SecurityContext nonMemberContext = mock(SecurityContext.class);
        Authentication nonMemberAuth = new UsernamePasswordAuthenticationToken(nonMemberDetails, null, Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")));
        when(nonMemberContext.getAuthentication()).thenReturn(nonMemberAuth);

        when(reactiveWorkspaceRepository.findById(1L)).thenReturn(Mono.just(workspace));
        when(reactiveWorkspaceMemberRepository.existsByWorkspaceIdAndUserId(1L, 4L)).thenReturn(Mono.just(false));

        // when
        Mono<WorkspaceMemberListResponseDto> resultMono = workspaceMemberService.getWorkspaceMembers(1L)
                .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(nonMemberContext)));

        // then
        StepVerifier.create(resultMono)
                .expectErrorMatches(throwable ->
                        throwable instanceof WorkspaceException &&
                                ((WorkspaceException) throwable).getErrorCode() == ErrorCode.WORKSPACE_PERMISSION_DENIED)
                .verify();
    }

    @Test
    @DisplayName("멤버 역할 수정 성공 - ADMIN 권한으로 MEMBER 역할 수정")
    void updateMemberRole_success_asAdmin() {
        // given
        WorkspaceMemberRoleUpdateRequestDto requestDto = WorkspaceMemberRoleUpdateRequestDto.builder()
                .userId(3L)
                .role(WorkspaceRole.ADMIN)
                .build();

        when(reactiveWorkspaceRepository.findById(1L)).thenReturn(Mono.just(workspace));
        when(reactiveWorkspaceMemberRepository.findByWorkspaceIdAndUserId(1L, 2L)).thenReturn(Mono.just(adminMember)); // admin user
        when(reactiveWorkspaceMemberRepository.findByWorkspaceIdAndUserId(1L, 3L)).thenReturn(Mono.just(regularMember));
        when(reactiveWorkspaceMemberRepository.save(any(WorkspaceMember.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        // when
        Mono<WorkspaceMemberResponseDto> resultMono = workspaceMemberService.updateMemberRole(1L, requestDto)
                .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(adminSecurityContext)));

        // then
        StepVerifier.create(resultMono)
                .expectNextMatches(response ->
                        response.getUserId().equals(3L) &&
                                response.getRole() == WorkspaceRole.ADMIN)
                .verifyComplete();
    }

    @Test
    @DisplayName("멤버 역할 수정 실패 - 권한 없음 (일반 멤버)")
    void updateMemberRole_failure_permissionDenied() {
        // given
        WorkspaceMemberRoleUpdateRequestDto requestDto = WorkspaceMemberRoleUpdateRequestDto.builder()
                .userId(2L)
                .role(WorkspaceRole.MEMBER)
                .build();

        when(reactiveWorkspaceRepository.findById(1L)).thenReturn(Mono.just(workspace));
        when(reactiveWorkspaceMemberRepository.findByWorkspaceIdAndUserId(1L, 3L)).thenReturn(Mono.just(regularMember));

        // when
        Mono<WorkspaceMemberResponseDto> resultMono = workspaceMemberService.updateMemberRole(1L, requestDto)
                .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(memberSecurityContext)));

        // then
        StepVerifier.create(resultMono)
                .expectErrorMatches(throwable ->
                        throwable instanceof WorkspaceException &&
                                ((WorkspaceException) throwable).getErrorCode() == ErrorCode.WORKSPACE_PERMISSION_DENIED)
                .verify();
    }

    @Test
    @DisplayName("멤버 역할 수정 실패 - OWNER 역할 변경 시도")
    void updateMemberRole_failure_cannotChangeOwnerRole() {
        // given
        WorkspaceMemberRoleUpdateRequestDto requestDto = WorkspaceMemberRoleUpdateRequestDto.builder()
                .userId(1L)
                .role(WorkspaceRole.MEMBER)
                .build();

        when(reactiveWorkspaceRepository.findById(1L)).thenReturn(Mono.just(workspace));
        when(reactiveWorkspaceMemberRepository.findByWorkspaceIdAndUserId(1L, 1L)).thenReturn(Mono.just(ownerMember));

        // when
        Mono<WorkspaceMemberResponseDto> resultMono = workspaceMemberService.updateMemberRole(1L, requestDto)
                .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(ownerSecurityContext)));

        // then
        StepVerifier.create(resultMono)
                .expectErrorMatches(throwable ->
                        throwable instanceof WorkspaceMemberException &&
                                ((WorkspaceMemberException) throwable).getErrorCode() == ErrorCode.CANNOT_CHANGE_OWNER_ROLE)
                .verify();
    }

    @Test
    @DisplayName("멤버 삭제 성공 - OWNER 권한으로 일반 멤버 삭제")
    void removeMember_success_asOwner() {
        // given
        when(reactiveWorkspaceMemberRepository.findByWorkspaceIdAndUserId(1L, 1L))
                .thenReturn(Mono.just(ownerMember));
        when(reactiveWorkspaceMemberRepository.existsByWorkspaceIdAndUserId(1L, 3L))
                .thenReturn(Mono.just(true));
        when(reactiveWorkspaceMemberRepository.deleteByWorkspaceIdAndUserId(1L, 3L))
                .thenReturn(Mono.empty());

        // when
        Mono<Void> resultMono = workspaceMemberService.removeMember(1L, 3L)
                .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(ownerSecurityContext)));

        // then
        StepVerifier.create(resultMono)
                .verifyComplete();

        verify(reactiveWorkspaceMemberRepository).deleteByWorkspaceIdAndUserId(1L, 3L);
    }

    @Test
    @DisplayName("멤버 삭제 실패 - OWNER가 아닌 사용자가 삭제 시도")
    void removeMember_failure_permissionDenied() {
        // given
        when(reactiveWorkspaceMemberRepository.findByWorkspaceIdAndUserId(1L, 2L))
                .thenReturn(Mono.just(adminMember));

        // when
        Mono<Void> resultMono = workspaceMemberService.removeMember(1L, 3L)
                .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(adminSecurityContext)));

        // then
        StepVerifier.create(resultMono)
                .expectErrorMatches(throwable ->
                        throwable instanceof WorkspaceException &&
                                ((WorkspaceException) throwable).getErrorCode() == ErrorCode.WORKSPACE_PERMISSION_DENIED
                )
                .verify();
    }

    @Test
    @DisplayName("멤버 삭제 실패 - OWNER가 자기 자신을 삭제 시도")
    void removeMember_failure_ownerCannotRemoveSelf() {
        // given
        when(reactiveWorkspaceMemberRepository.findByWorkspaceIdAndUserId(1L, 1L))
                .thenReturn(Mono.just(ownerMember));

        // when
        Mono<Void> resultMono = workspaceMemberService.removeMember(1L, 1L)
                .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(ownerSecurityContext)));

        // then
        StepVerifier.create(resultMono)
                .expectErrorMatches(throwable ->
                        throwable instanceof WorkspaceMemberException &&
                                ((WorkspaceMemberException) throwable).getErrorCode() == ErrorCode.CANNOT_REMOVE_OWNER
                )
                .verify();
    }

    @Test
    @DisplayName("멤버 삭제 실패 - 대상 멤버를 찾을 수 없음")
    void removeMember_failure_memberNotFound() {
        // given
        when(reactiveWorkspaceMemberRepository.findByWorkspaceIdAndUserId(1L, 1L))
                .thenReturn(Mono.just(ownerMember));
        when(reactiveWorkspaceMemberRepository.existsByWorkspaceIdAndUserId(1L, 999L))
                .thenReturn(Mono.just(false));

        // when
        Mono<Void> resultMono = workspaceMemberService.removeMember(1L, 999L)
                .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(ownerSecurityContext)));

        // then
        StepVerifier.create(resultMono)
                .expectErrorMatches(throwable ->
                        throwable instanceof WorkspaceMemberException &&
                                ((WorkspaceMemberException) throwable).getErrorCode() == ErrorCode.MEMBER_NOT_FOUND
                )
                .verify();
    }
}