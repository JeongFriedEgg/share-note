package com.example.share_note.integration;

import com.example.share_note.domain.Workspace;
import com.example.share_note.dto.CustomUserDetails;
import com.example.share_note.dto.workspace.WorkspaceCreateRequestDto;
import com.example.share_note.dto.workspace.WorkspaceUpdateRequestDto;
import com.example.share_note.exception.ErrorCode;
import com.example.share_note.exception.JwtAuthenticationException;
import com.example.share_note.repository.ReactiveWorkspaceRepository;
import com.example.share_note.security.JwtTokenProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.client.AutoConfigureWebClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebClient
public class WorkspaceIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private ReactiveWorkspaceRepository reactiveWorkspaceRepository;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    private final String VALID_TOKEN = "Bearer validAccessToken";
    private final String INVALID_TOKEN = "Bearer invalidAccessToken";

    private CustomUserDetails createMockUserDetails() {
        return new CustomUserDetails(
                1L,
                "testuser",
                "password",
                "ROLE_USER",
                "test@example.com"
        );
    }

    private Authentication createMockAuthentication() {
        CustomUserDetails userDetails = createMockUserDetails();
        return new UsernamePasswordAuthenticationToken(
                userDetails,
                null,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"))
        );
    }

    @Test
    @DisplayName("워크스페이스 생성 성공 - 201 CREATED 응답")
    void createWorkspace_success() {
        // given
        WorkspaceCreateRequestDto request = WorkspaceCreateRequestDto.builder()
                .name("Test Workspace")
                .description("Test Description")
                .build();

        Workspace savedWorkspace = Workspace.builder()
                .id(1L)
                .name(request.getName())
                .description(request.getDescription())
                .createdBy(1L)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(jwtTokenProvider.validateToken(anyString())).thenReturn(true);
        when(jwtTokenProvider.getAuthentication(anyString())).thenReturn(createMockAuthentication());
        when(reactiveWorkspaceRepository.save(any(Workspace.class)))
                .thenReturn(Mono.just(savedWorkspace));

        // when
        // then
        webTestClient.post()
                .uri("/api/workspaces")
                .header("Authorization", VALID_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isCreated()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.name").isEqualTo("Test Workspace")
                .jsonPath("$.description").isEqualTo("Test Description")
                .jsonPath("$.createdAt").isNotEmpty();
    }

    @Test
    @DisplayName("워크스페이스 생성 실패 - 인증 토큰 없음 - 401 UNAUTHORIZED 응답")
    void createWorkspace_failure_noToken() {
        // given
        WorkspaceCreateRequestDto request = WorkspaceCreateRequestDto.builder()
                .name("Test Workspace")
                .description("Test Description")
                .build();

        // when
        // then
        webTestClient.post()
                .uri("/api/workspaces")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    @DisplayName("워크스페이스 생성 실패 - 유효하지 않은 토큰 - 401 UNAUTHORIZED 응답")
    void createWorkspace_failure_invalidToken() {
        // given
        WorkspaceCreateRequestDto request = WorkspaceCreateRequestDto.builder()
                .name("Test Workspace")
                .description("Test Description")
                .build();

        when(jwtTokenProvider.validateToken(anyString()))
                .thenThrow(new JwtAuthenticationException(ErrorCode.INVALID_TOKEN));

        // when
        // then
        webTestClient.post()
                .uri("/api/workspaces")
                .header("Authorization", INVALID_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    @DisplayName("워크스페이스 수정 성공 - 200 OK 응답")
    void updateWorkspace_success() {
        // given
        WorkspaceUpdateRequestDto request = WorkspaceUpdateRequestDto.builder()
                .workspaceId(1L)
                .name("Updated Workspace")
                .description("Updated Description")
                .build();

        Workspace existingWorkspace = Workspace.builder()
                .id(1L)
                .name("Original Workspace")
                .description("Original Description")
                .createdBy(1L)
                .createdAt(LocalDateTime.now().minusDays(1))
                .updatedAt(LocalDateTime.now().minusDays(1))
                .build();

        Workspace updatedWorkspace = Workspace.builder()
                .id(1L)
                .name(request.getName())
                .description(request.getDescription())
                .createdBy(1L)
                .createdAt(existingWorkspace.getCreatedAt())
                .updatedAt(LocalDateTime.now())
                .build();

        when(jwtTokenProvider.validateToken(anyString())).thenReturn(true);
        when(jwtTokenProvider.getAuthentication(anyString())).thenReturn(createMockAuthentication());
        when(reactiveWorkspaceRepository.findById(1L))
                .thenReturn(Mono.just(existingWorkspace));
        when(reactiveWorkspaceRepository.save(any(Workspace.class)))
                .thenReturn(Mono.just(updatedWorkspace));

        // when
        // then
        webTestClient.put()
                .uri("/api/workspaces")
                .header("Authorization", VALID_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.name").isEqualTo("Updated Workspace")
                .jsonPath("$.description").isEqualTo("Updated Description")
                .jsonPath("$.updatedAt").isNotEmpty();
    }

    @Test
    @DisplayName("워크스페이스 수정 실패 - 워크스페이스 없음 - 404 NOT FOUND 응답")
    void updateWorkspace_failure_workspaceNotFound() {
        // given
        WorkspaceUpdateRequestDto request = WorkspaceUpdateRequestDto.builder()
                .workspaceId(999L)
                .name("Updated Workspace")
                .description("Updated Description")
                .build();

        when(jwtTokenProvider.validateToken(anyString())).thenReturn(true);
        when(jwtTokenProvider.getAuthentication(anyString())).thenReturn(createMockAuthentication());
        when(reactiveWorkspaceRepository.findById(999L))
                .thenReturn(Mono.empty());

        // when
        // then
        webTestClient.put()
                .uri("/api/workspaces")
                .header("Authorization", VALID_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.message").isEqualTo(ErrorCode.WORKSPACE_NOT_FOUND.getMessage());
    }

    @Test
    @DisplayName("워크스페이스 수정 실패 - 권한 없음 - 403 FORBIDDEN 응답")
    void updateWorkspace_failure_permissionDenied() {
        // given
        WorkspaceUpdateRequestDto request = WorkspaceUpdateRequestDto.builder()
                .workspaceId(1L)
                .name("Updated Workspace")
                .description("Updated Description")
                .build();

        // 다른 사용자가 생성한 워크스페이스
        Workspace existingWorkspace = Workspace.builder()
                .id(1L)
                .name("Original Workspace")
                .description("Original Description")
                .createdBy(2L) // 다른 사용자 ID
                .createdAt(LocalDateTime.now().minusDays(1))
                .updatedAt(LocalDateTime.now().minusDays(1))
                .build();

        when(jwtTokenProvider.validateToken(anyString())).thenReturn(true);
        when(jwtTokenProvider.getAuthentication(anyString())).thenReturn(createMockAuthentication());
        when(reactiveWorkspaceRepository.findById(1L))
                .thenReturn(Mono.just(existingWorkspace));

        // when
        // then
        webTestClient.put()
                .uri("/api/workspaces")
                .header("Authorization", VALID_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.message").isEqualTo(ErrorCode.WORKSPACE_PERMISSION_DENIED.getMessage());
    }

    @Test
    @DisplayName("워크스페이스 수정 실패 - 유효하지 않은 워크스페이스 이름 - 400 BAD REQUEST 응답")
    void updateWorkspace_failure_invalidWorkspaceName() {
        // given
        WorkspaceUpdateRequestDto request = WorkspaceUpdateRequestDto.builder()
                .workspaceId(1L)
                .name("") // 빈 이름
                .description("Updated Description")
                .build();

        Workspace existingWorkspace = Workspace.builder()
                .id(1L)
                .name("Original Workspace")
                .description("Original Description")
                .createdBy(1L)
                .createdAt(LocalDateTime.now().minusDays(1))
                .updatedAt(LocalDateTime.now().minusDays(1))
                .build();

        when(jwtTokenProvider.validateToken(anyString())).thenReturn(true);
        when(jwtTokenProvider.getAuthentication(anyString())).thenReturn(createMockAuthentication());
        when(reactiveWorkspaceRepository.findById(1L))
                .thenReturn(Mono.just(existingWorkspace));

        // when
        // then
        webTestClient.put()
                .uri("/api/workspaces")
                .header("Authorization", VALID_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.message").isEqualTo(ErrorCode.INVALID_WORKSPACE_NAME.getMessage());
    }

    @Test
    @DisplayName("워크스페이스 삭제 성공 - 204 NO CONTENT 응답")
    void deleteWorkspace_success() {
        // given
        Long workspaceId = 1L;

        Workspace existingWorkspace = Workspace.builder()
                .id(workspaceId)
                .name("Test Workspace")
                .description("Test Description")
                .createdBy(1L)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(jwtTokenProvider.validateToken(anyString())).thenReturn(true);
        when(jwtTokenProvider.getAuthentication(anyString())).thenReturn(createMockAuthentication());
        when(reactiveWorkspaceRepository.findById(workspaceId))
                .thenReturn(Mono.just(existingWorkspace));
        when(reactiveWorkspaceRepository.delete(any(Workspace.class)))
                .thenReturn(Mono.empty());

        // when & then
        webTestClient.delete()
                .uri("/api/workspaces/{workspaceId}", workspaceId)
                .header("Authorization", VALID_TOKEN)
                .exchange()
                .expectStatus().isNoContent()
                .expectBody().isEmpty();
    }

    @Test
    @DisplayName("워크스페이스 삭제 실패 - 워크스페이스 없음 - 404 NOT FOUND 응답")
    void deleteWorkspace_failure_workspaceNotFound() {
        // given
        Long workspaceId = 999L;

        when(jwtTokenProvider.validateToken(anyString())).thenReturn(true);
        when(jwtTokenProvider.getAuthentication(anyString())).thenReturn(createMockAuthentication());
        when(reactiveWorkspaceRepository.findById(workspaceId))
                .thenReturn(Mono.empty());

        // when & then
        webTestClient.delete()
                .uri("/api/workspaces/{workspaceId}", workspaceId)
                .header("Authorization", VALID_TOKEN)
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.message").isEqualTo(ErrorCode.WORKSPACE_NOT_FOUND.getMessage());
    }

    @Test
    @DisplayName("워크스페이스 삭제 실패 - 권한 없음 - 403 FORBIDDEN 응답")
    void deleteWorkspace_failure_permissionDenied() {
        // given
        Long workspaceId = 1L;

        // 다른 사용자가 생성한 워크스페이스
        Workspace existingWorkspace = Workspace.builder()
                .id(workspaceId)
                .name("Test Workspace")
                .description("Test Description")
                .createdBy(2L) // 다른 사용자 ID
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(jwtTokenProvider.validateToken(anyString())).thenReturn(true);
        when(jwtTokenProvider.getAuthentication(anyString())).thenReturn(createMockAuthentication());
        when(reactiveWorkspaceRepository.findById(workspaceId))
                .thenReturn(Mono.just(existingWorkspace));

        // when & then
        webTestClient.delete()
                .uri("/api/workspaces/{workspaceId}", workspaceId)
                .header("Authorization", VALID_TOKEN)
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.message").isEqualTo(ErrorCode.WORKSPACE_PERMISSION_DENIED.getMessage());
    }

    @Test
    @DisplayName("워크스페이스 삭제 실패 - 인증 토큰 없음 - 401 UNAUTHORIZED 응답")
    void deleteWorkspace_failure_noToken() {
        // given
        Long workspaceId = 1L;

        // when & then
        webTestClient.delete()
                .uri("/api/workspaces/{workspaceId}", workspaceId)
                .exchange()
                .expectStatus().isUnauthorized();
    }
}
