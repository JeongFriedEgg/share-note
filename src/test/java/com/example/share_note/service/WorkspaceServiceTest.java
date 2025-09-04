package com.example.share_note.service;

import com.example.share_note.domain.Workspace;
import com.example.share_note.dto.CustomUserDetails;
import com.example.share_note.dto.workspace.WorkspaceCreateRequestDto;
import com.example.share_note.dto.workspace.WorkspaceCreateResponseDto;
import com.example.share_note.dto.workspace.WorkspaceUpdateRequestDto;
import com.example.share_note.dto.workspace.WorkspaceUpdateResponseDto;
import com.example.share_note.exception.ErrorCode;
import com.example.share_note.exception.WorkspaceException;
import com.example.share_note.repository.ReactiveWorkspaceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class WorkspaceServiceTest {

    @Mock
    private ReactiveWorkspaceRepository reactiveWorkspaceRepository;

    @InjectMocks
    private WorkspaceService workspaceService;

    private CustomUserDetails mockUserDetails;
    private SecurityContext mockSecurityContext;
    private Authentication mockAuthentication;
    private Workspace workspace;

    @BeforeEach
    void setUp() {
        mockUserDetails = new CustomUserDetails(
                1L,
                "testuser",
                "password",
                "ROLE_USER",
                "test@example.com"
        );

        mockAuthentication = new UsernamePasswordAuthenticationToken(
                mockUserDetails,
                null,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"))
        );

        mockSecurityContext = mock(SecurityContext.class);
        when(mockSecurityContext.getAuthentication()).thenReturn(mockAuthentication);

        workspace = Workspace.builder()
                .id(1L)
                .name("Test Workspace")
                .description("Test Description")
                .createdBy(1L)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    @Test
    @DisplayName("워크스페이스 생성 성공 - 워크스페이스 저장 후 응답 DTO 반환")
    void createWorkspace_success() {
        // given
        WorkspaceCreateRequestDto requestDto = WorkspaceCreateRequestDto.builder()
                .name("New Workspace")
                .description("New Description")
                .build();

        when(reactiveWorkspaceRepository.save(any(Workspace.class)))
                .thenReturn(Mono.just(workspace));

        // when
        Mono<WorkspaceCreateResponseDto> responseDtoMono = workspaceService.createWorkspace(requestDto)
                .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(mockSecurityContext)));

        // then
        StepVerifier.create(responseDtoMono)
                .expectNextMatches(response ->
                        "Test Workspace".equals(response.getName()) &&
                                "Test Description".equals(response.getDescription()) &&
                                response.getCreatedAt() != null)
                .verifyComplete();

        verify(reactiveWorkspaceRepository).save(argThat(savedWorkspace ->
                "New Workspace".equals(savedWorkspace.getName()) &&
                        "New Description".equals(savedWorkspace.getDescription()) &&
                        savedWorkspace.getCreatedBy().equals(1L) &&
                        savedWorkspace.getCreatedAt() != null &&
                        savedWorkspace.getUpdatedAt() != null
        ));
    }

    @Test
    @DisplayName("워크스페이스 수정 성공 - 워크스페이스 수정 후 응답 DTO 반환")
    void updateWorkspace_success() {
        // given
        WorkspaceUpdateRequestDto requestDto = WorkspaceUpdateRequestDto.builder()
                .workspaceId(1L)
                .name("Updated Workspace")
                .description("Updated Description")
                .build();

        Workspace updatedWorkspace = Workspace.builder()
                .id(1L)
                .name("Updated Workspace")
                .description("Updated Description")
                .createdBy(1L)
                .createdAt(workspace.getCreatedAt())
                .updatedAt(LocalDateTime.now())
                .build();

        when(reactiveWorkspaceRepository.findById(1L))
                .thenReturn(Mono.just(workspace));
        when(reactiveWorkspaceRepository.save(any(Workspace.class)))
                .thenReturn(Mono.just(updatedWorkspace));

        // when
        Mono<WorkspaceUpdateResponseDto> responseDtoMono = workspaceService.updateWorkspace(requestDto)
                .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(mockSecurityContext)));

        // then
        StepVerifier.create(responseDtoMono)
                .expectNextMatches(response ->
                        "Updated Workspace".equals(response.getName()) &&
                                "Updated Description".equals(response.getDescription()) &&
                                response.getUpdatedAt() != null)
                .verifyComplete();

        verify(reactiveWorkspaceRepository).save(argThat(savedWorkspace ->
                "Updated Workspace".equals(savedWorkspace.getName()) &&
                        "Updated Description".equals(savedWorkspace.getDescription()) &&
                        savedWorkspace.getCreatedBy().equals(1L)
        ));
    }

    @Test
    @DisplayName("워크스페이스 수정 성공 - description null인 경우 기존 description 유지")
    void updateWorkspace_success_nullDescription() {
        // given
        WorkspaceUpdateRequestDto requestDto = WorkspaceUpdateRequestDto.builder()
                .workspaceId(1L)
                .name("Updated Workspace")
                .description(null)
                .build();

        Workspace updatedWorkspace = Workspace.builder()
                .id(1L)
                .name("Updated Workspace")
                .description("Test Description") // 기존 description 유지
                .createdBy(1L)
                .createdAt(workspace.getCreatedAt())
                .updatedAt(LocalDateTime.now())
                .build();

        when(reactiveWorkspaceRepository.findById(1L))
                .thenReturn(Mono.just(workspace));
        when(reactiveWorkspaceRepository.save(any(Workspace.class)))
                .thenReturn(Mono.just(updatedWorkspace));

        // when
        Mono<WorkspaceUpdateResponseDto> responseDtoMono = workspaceService.updateWorkspace(requestDto)
                .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(mockSecurityContext)));

        // then
        StepVerifier.create(responseDtoMono)
                .expectNextMatches(response ->
                        "Updated Workspace".equals(response.getName()) &&
                                "Test Description".equals(response.getDescription()) &&
                                response.getUpdatedAt() != null)
                .verifyComplete();
    }

    @Test
    @DisplayName("워크스페이스 수정 실패 - 워크스페이스가 존재하지 않음")
    void updateWorkspace_failure_workspaceNotFound() {
        // given
        WorkspaceUpdateRequestDto requestDto = WorkspaceUpdateRequestDto.builder()
                .workspaceId(999L)
                .name("Updated Workspace")
                .description("Updated Description")
                .build();

        when(reactiveWorkspaceRepository.findById(999L))
                .thenReturn(Mono.empty());

        // when
        Mono<WorkspaceUpdateResponseDto> responseDtoMono = workspaceService.updateWorkspace(requestDto)
                .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(mockSecurityContext)));

        // then
        StepVerifier.create(responseDtoMono)
                .expectErrorMatches(throwable ->
                        throwable instanceof WorkspaceException &&
                                ((WorkspaceException) throwable).getErrorCode() == ErrorCode.WORKSPACE_NOT_FOUND)
                .verify();
    }

    @Test
    @DisplayName("워크스페이스 수정 실패 - 권한이 없음")
    void updateWorkspace_failure_permissionDenied() {
        // given
        WorkspaceUpdateRequestDto requestDto = WorkspaceUpdateRequestDto.builder()
                .workspaceId(1L)
                .name("Updated Workspace")
                .description("Updated Description")
                .build();

        // 다른 사용자가 생성한 워크스페이스
        Workspace otherUserWorkspace = Workspace.builder()
                .id(1L)
                .name("Test Workspace")
                .description("Test Description")
                .createdBy(2L) // 다른 사용자
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(reactiveWorkspaceRepository.findById(1L))
                .thenReturn(Mono.just(otherUserWorkspace));

        // when
        Mono<WorkspaceUpdateResponseDto> responseDtoMono = workspaceService.updateWorkspace(requestDto)
                .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(mockSecurityContext)));

        // then
        StepVerifier.create(responseDtoMono)
                .expectErrorMatches(throwable ->
                        throwable instanceof WorkspaceException &&
                                ((WorkspaceException) throwable).getErrorCode() == ErrorCode.PERMISSION_DENIED)
                .verify();
    }

    @Test
    @DisplayName("워크스페이스 수정 실패 - 워크스페이스 이름이 유효하지 않음")
    void updateWorkspace_failure_invalidWorkspaceName() {
        // given
        WorkspaceUpdateRequestDto requestDto = WorkspaceUpdateRequestDto.builder()
                .workspaceId(1L)
                .name("") // 빈 이름
                .description("Updated Description")
                .build();

        when(reactiveWorkspaceRepository.findById(1L))
                .thenReturn(Mono.just(workspace));

        // when
        Mono<WorkspaceUpdateResponseDto> responseDtoMono = workspaceService.updateWorkspace(requestDto)
                .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(mockSecurityContext)));

        // then
        StepVerifier.create(responseDtoMono)
                .expectErrorMatches(throwable ->
                        throwable instanceof WorkspaceException &&
                                ((WorkspaceException) throwable).getErrorCode() == ErrorCode.INVALID_WORKSPACE_NAME)
                .verify();
    }

    @Test
    @DisplayName("워크스페이스 수정 실패 - 워크스페이스 이름이 공백")
    void updateWorkspace_failure_blankWorkspaceName() {
        // given
        WorkspaceUpdateRequestDto requestDto = WorkspaceUpdateRequestDto.builder()
                .workspaceId(1L)
                .name("   ") // 공백만 있는 이름
                .description("Updated Description")
                .build();

        when(reactiveWorkspaceRepository.findById(1L))
                .thenReturn(Mono.just(workspace));

        // when
        Mono<WorkspaceUpdateResponseDto> responseDtoMono = workspaceService.updateWorkspace(requestDto)
                .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(mockSecurityContext)));

        // then
        StepVerifier.create(responseDtoMono)
                .expectErrorMatches(throwable ->
                        throwable instanceof WorkspaceException &&
                                ((WorkspaceException) throwable).getErrorCode() == ErrorCode.INVALID_WORKSPACE_NAME)
                .verify();
    }

    @Test
    @DisplayName("워크스페이스 삭제 성공 - 워크스페이스 삭제")
    void deleteWorkspace_success() {
        // given
        Long workspaceId = 1L;

        when(reactiveWorkspaceRepository.findById(workspaceId))
                .thenReturn(Mono.just(workspace));
        when(reactiveWorkspaceRepository.delete(workspace))
                .thenReturn(Mono.empty());

        // when
        Mono<Void> resultMono = workspaceService.deleteWorkspace(workspaceId)
                .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(mockSecurityContext)));

        // then
        StepVerifier.create(resultMono)
                .verifyComplete();

        verify(reactiveWorkspaceRepository).delete(workspace);
    }

    @Test
    @DisplayName("워크스페이스 삭제 실패 - 워크스페이스가 존재하지 않음")
    void deleteWorkspace_failure_workspaceNotFound() {
        // given
        Long workspaceId = 999L;

        when(reactiveWorkspaceRepository.findById(workspaceId))
                .thenReturn(Mono.empty());

        // when
        Mono<Void> resultMono = workspaceService.deleteWorkspace(workspaceId)
                .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(mockSecurityContext)));

        // then
        StepVerifier.create(resultMono)
                .expectErrorMatches(throwable ->
                        throwable instanceof WorkspaceException &&
                                ((WorkspaceException) throwable).getErrorCode() == ErrorCode.WORKSPACE_NOT_FOUND)
                .verify();
    }

    @Test
    @DisplayName("워크스페이스 삭제 실패 - 권한이 없음")
    void deleteWorkspace_failure_permissionDenied() {
        // given
        Long workspaceId = 1L;

        // 다른 사용자가 생성한 워크스페이스
        Workspace otherUserWorkspace = Workspace.builder()
                .id(1L)
                .name("Test Workspace")
                .description("Test Description")
                .createdBy(2L) // 다른 사용자
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(reactiveWorkspaceRepository.findById(workspaceId))
                .thenReturn(Mono.just(otherUserWorkspace));

        // when
        Mono<Void> resultMono = workspaceService.deleteWorkspace(workspaceId)
                .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(mockSecurityContext)));

        // then
        StepVerifier.create(resultMono)
                .expectErrorMatches(throwable ->
                        throwable instanceof WorkspaceException &&
                                ((WorkspaceException) throwable).getErrorCode() == ErrorCode.PERMISSION_DENIED)
                .verify();
    }
}