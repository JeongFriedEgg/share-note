package com.example.share_note.integration;

import com.example.share_note.domain.Workspace;
import com.example.share_note.dto.CustomUserDetails;
import com.example.share_note.dto.workspace.WorkspaceCreateRequestDto;
import com.example.share_note.dto.workspace.WorkspaceUpdateRequestDto;
import com.example.share_note.exception.ErrorCode;
import com.example.share_note.exception.JwtAuthenticationException;
import com.example.share_note.repository.ReactiveWorkspaceRepository;
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
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class WorkspaceIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private ReactiveWorkspaceRepository reactiveWorkspaceRepository;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private UuidUtils uuidUtils;

    private final String VALID_TOKEN = "Bearer validAccessToken";
    private final String INVALID_TOKEN = "Bearer invalidAccessToken";

    private UUID workspaceId;
    private UUID userId;
    private UUID otherUserId;
    private String workspaceIdStr;
    private String userIdStr;
    private String otherUserIdStr;

    private CustomUserDetails userDetails;
    private CustomUserDetails otherUserDetails;
    private Authentication userAuth;
    private Authentication otherUserAuth;
    private Workspace workspace;
    private Workspace otherUserWorkspace;

    @BeforeEach
    void setUp() {
        workspaceId = UUID.randomUUID();
        userId = UUID.randomUUID();
        otherUserId = UUID.randomUUID();

        workspaceIdStr = workspaceId.toString();
        userIdStr = userId.toString();
        otherUserIdStr = otherUserId.toString();

        userDetails = new CustomUserDetails(userId, "testuser", "password", "ROLE_USER", "test@example.com");
        otherUserDetails = new CustomUserDetails(otherUserId, "otheruser", "password", "ROLE_USER", "other@example.com");

        userAuth = new UsernamePasswordAuthenticationToken(userDetails, null,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")));
        otherUserAuth = new UsernamePasswordAuthenticationToken(otherUserDetails, null,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")));

        workspace = Workspace.builder()
                .id(workspaceId)
                .name("Test Workspace")
                .description("Test Description")
                .createdBy(userId)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        otherUserWorkspace = Workspace.builder()
                .id(workspaceId)
                .name("Other User Workspace")
                .description("Other Description")
                .createdBy(otherUserId) // 다른 사용자가 소유
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(jwtTokenProvider.validateToken(anyString())).thenReturn(true);

        when(uuidUtils.fromString(workspaceIdStr)).thenReturn(workspaceId);
        when(uuidUtils.fromString(userIdStr)).thenReturn(userId);
        when(uuidUtils.fromString(otherUserIdStr)).thenReturn(otherUserId);
    }

    @Test
    @Order(1)
    @DisplayName("워크스페이스 생성 성공 - 201 CREATED 응답")
    void createWorkspace_success() {
        // given
        WorkspaceCreateRequestDto request = WorkspaceCreateRequestDto.builder()
                .name("Test Workspace")
                .description("Test Description")
                .build();

        when(jwtTokenProvider.getAuthentication(anyString())).thenReturn(userAuth);
        when(reactiveWorkspaceRepository.save(any(Workspace.class))).thenReturn(Mono.just(workspace));

        // when & then
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
    @Order(2)
    @DisplayName("워크스페이스 생성 성공 - 설명 없음")
    void createWorkspace_success_withoutDescription() {
        // given
        WorkspaceCreateRequestDto request = WorkspaceCreateRequestDto.builder()
                .name("Test Workspace Without Description")
                .build();

        Workspace workspaceWithoutDesc = Workspace.builder()
                .id(workspaceId)
                .name("Test Workspace Without Description")
                .createdBy(userId)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(jwtTokenProvider.getAuthentication(anyString())).thenReturn(userAuth);
        when(reactiveWorkspaceRepository.save(any(Workspace.class))).thenReturn(Mono.just(workspaceWithoutDesc));

        // when & then
        webTestClient.post()
                .uri("/api/workspaces")
                .header("Authorization", VALID_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isCreated()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.name").isEqualTo("Test Workspace Without Description")
                .jsonPath("$.description").isEmpty();
    }

    @Test
    @Order(3)
    @DisplayName("워크스페이스 생성 실패 - 인증 토큰 없음 - 401 UNAUTHORIZED 응답")
    void createWorkspace_failure_noToken() {
        // given
        WorkspaceCreateRequestDto request = WorkspaceCreateRequestDto.builder()
                .name("Test Workspace")
                .description("Test Description")
                .build();

        // when & then (Authorization 헤더 없음)
        webTestClient.post()
                .uri("/api/workspaces")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    @Order(4)
    @DisplayName("워크스페이스 생성 실패 - 유효하지 않은 토큰 - 401 UNAUTHORIZED 응답")
    void createWorkspace_failure_invalidToken() {
        // given
        WorkspaceCreateRequestDto request = WorkspaceCreateRequestDto.builder()
                .name("Test Workspace")
                .description("Test Description")
                .build();

        when(jwtTokenProvider.validateToken(anyString()))
                .thenThrow(new JwtAuthenticationException(ErrorCode.INVALID_TOKEN));

        // when & then
        webTestClient.post()
                .uri("/api/workspaces")
                .header("Authorization", INVALID_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    @Order(5)
    @DisplayName("워크스페이스 수정 성공 - 200 OK 응답")
    void updateWorkspace_success() {
        // given
        WorkspaceUpdateRequestDto request = WorkspaceUpdateRequestDto.builder()
                .workspaceId(workspaceIdStr)
                .name("Updated Workspace")
                .description("Updated Description")
                .build();

        Workspace updatedWorkspace = Workspace.builder()
                .id(workspaceId)
                .name("Updated Workspace")
                .description("Updated Description")
                .createdBy(userId)
                .createdAt(workspace.getCreatedAt())
                .updatedAt(LocalDateTime.now())
                .build();

        when(jwtTokenProvider.getAuthentication(anyString())).thenReturn(userAuth);
        when(reactiveWorkspaceRepository.findById(workspaceId)).thenReturn(Mono.just(workspace));
        when(reactiveWorkspaceRepository.save(any(Workspace.class))).thenReturn(Mono.just(updatedWorkspace));

        // when & then
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
    @Order(6)
    @DisplayName("워크스페이스 수정 성공 - 설명만 변경 (null로 유지)")
    void updateWorkspace_success_keepDescription() {
        // given
        WorkspaceUpdateRequestDto request = WorkspaceUpdateRequestDto.builder()
                .workspaceId(workspaceIdStr)
                .name("Updated Name Only")
                .description(null) // 기존 설명 유지
                .build();

        Workspace updatedWorkspace = Workspace.builder()
                .id(workspaceId)
                .name("Updated Name Only")
                .description("Test Description") // 기존 설명 유지
                .createdBy(userId)
                .createdAt(workspace.getCreatedAt())
                .updatedAt(LocalDateTime.now())
                .build();

        when(jwtTokenProvider.getAuthentication(anyString())).thenReturn(userAuth);
        when(reactiveWorkspaceRepository.findById(workspaceId)).thenReturn(Mono.just(workspace));
        when(reactiveWorkspaceRepository.save(any(Workspace.class))).thenReturn(Mono.just(updatedWorkspace));

        // when & then
        webTestClient.put()
                .uri("/api/workspaces")
                .header("Authorization", VALID_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.name").isEqualTo("Updated Name Only")
                .jsonPath("$.description").isEqualTo("Test Description");
    }

    @Test
    @Order(7)
    @DisplayName("워크스페이스 수정 실패 - 워크스페이스 없음 - 404 NOT FOUND 응답")
    void updateWorkspace_failure_workspaceNotFound() {
        // given
        WorkspaceUpdateRequestDto request = WorkspaceUpdateRequestDto.builder()
                .workspaceId(workspaceIdStr)
                .name("Updated Workspace")
                .description("Updated Description")
                .build();

        when(jwtTokenProvider.getAuthentication(anyString())).thenReturn(userAuth);
        when(reactiveWorkspaceRepository.findById(workspaceId)).thenReturn(Mono.empty());

        // when & then
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
    @Order(8)
    @DisplayName("워크스페이스 수정 실패 - 권한 없음 - 403 FORBIDDEN 응답")
    void updateWorkspace_failure_permissionDenied() {
        // given
        WorkspaceUpdateRequestDto request = WorkspaceUpdateRequestDto.builder()
                .workspaceId(workspaceIdStr)
                .name("Updated Workspace")
                .description("Updated Description")
                .build();

        when(jwtTokenProvider.getAuthentication(anyString())).thenReturn(userAuth);
        when(reactiveWorkspaceRepository.findById(workspaceId)).thenReturn(Mono.just(otherUserWorkspace));

        // when & then
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
    @Order(9)
    @DisplayName("워크스페이스 수정 실패 - 유효하지 않은 워크스페이스 이름 (빈 문자열) - 400 BAD REQUEST 응답")
    void updateWorkspace_failure_invalidWorkspaceName_empty() {
        // given
        WorkspaceUpdateRequestDto request = WorkspaceUpdateRequestDto.builder()
                .workspaceId(workspaceIdStr)
                .name("") // 빈 이름
                .description("Updated Description")
                .build();

        when(jwtTokenProvider.getAuthentication(anyString())).thenReturn(userAuth);
        when(reactiveWorkspaceRepository.findById(workspaceId)).thenReturn(Mono.just(workspace));

        // when & then
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
    @Order(10)
    @DisplayName("워크스페이스 수정 실패 - 유효하지 않은 워크스페이스 이름 (null) - 400 BAD REQUEST 응답")
    void updateWorkspace_failure_invalidWorkspaceName_null() {
        // given
        WorkspaceUpdateRequestDto request = WorkspaceUpdateRequestDto.builder()
                .workspaceId(workspaceIdStr)
                .name(null) // null 이름
                .description("Updated Description")
                .build();

        when(jwtTokenProvider.getAuthentication(anyString())).thenReturn(userAuth);
        when(reactiveWorkspaceRepository.findById(workspaceId)).thenReturn(Mono.just(workspace));

        // when & then
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
    @Order(11)
    @DisplayName("워크스페이스 삭제 성공 - 204 NO CONTENT 응답")
    void deleteWorkspace_success() {
        // given
        when(jwtTokenProvider.getAuthentication(anyString())).thenReturn(userAuth);
        when(reactiveWorkspaceRepository.findById(workspaceId)).thenReturn(Mono.just(workspace));
        when(reactiveWorkspaceRepository.delete(any(Workspace.class))).thenReturn(Mono.empty());

        // when & then
        webTestClient.delete()
                .uri("/api/workspaces/{workspaceId}", workspaceIdStr)
                .header("Authorization", VALID_TOKEN)
                .exchange()
                .expectStatus().isNoContent()
                .expectBody().isEmpty();
    }

    @Test
    @Order(12)
    @DisplayName("워크스페이스 삭제 실패 - 워크스페이스 없음 - 404 NOT FOUND 응답")
    void deleteWorkspace_failure_workspaceNotFound() {
        // given
        when(jwtTokenProvider.getAuthentication(anyString())).thenReturn(userAuth);
        when(reactiveWorkspaceRepository.findById(workspaceId)).thenReturn(Mono.empty());

        // when & then
        webTestClient.delete()
                .uri("/api/workspaces/{workspaceId}", workspaceIdStr)
                .header("Authorization", VALID_TOKEN)
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.message").isEqualTo(ErrorCode.WORKSPACE_NOT_FOUND.getMessage());
    }

    @Test
    @Order(13)
    @DisplayName("워크스페이스 삭제 실패 - 권한 없음 - 403 FORBIDDEN 응답")
    void deleteWorkspace_failure_permissionDenied() {
        // given
        when(jwtTokenProvider.getAuthentication(anyString())).thenReturn(userAuth);
        when(reactiveWorkspaceRepository.findById(workspaceId)).thenReturn(Mono.just(otherUserWorkspace));

        // when & then
        webTestClient.delete()
                .uri("/api/workspaces/{workspaceId}", workspaceIdStr)
                .header("Authorization", VALID_TOKEN)
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.message").isEqualTo(ErrorCode.WORKSPACE_PERMISSION_DENIED.getMessage());
    }

    @Test
    @Order(14)
    @DisplayName("워크스페이스 삭제 실패 - 인증 토큰 없음 - 401 UNAUTHORIZED 응답")
    void deleteWorkspace_failure_noToken() {
        // when & then (Authorization 헤더 없음)
        webTestClient.delete()
                .uri("/api/workspaces/{workspaceId}", workspaceIdStr)
                .exchange()
                .expectStatus().isUnauthorized();
    }
}