package com.example.share_note.service;

import com.example.share_note.domain.Workspace;
import com.example.share_note.dto.CustomUserDetails;
import com.example.share_note.dto.workspace.WorkspaceCreateRequestDto;
import com.example.share_note.dto.workspace.WorkspaceUpdateRequestDto;
import com.example.share_note.exception.WorkspaceException;
import com.example.share_note.repository.ReactiveWorkspaceRepository;
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
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class WorkspaceServiceTest {

    @Mock
    private ReactiveWorkspaceRepository reactiveWorkspaceRepository;

    @Mock
    private UuidUtils uuidUtils;

    @InjectMocks
    private WorkspaceService workspaceService;

    private UUID workspaceId;
    private UUID userId;
    private String workspaceIdStr;

    private Workspace workspace;

    private SecurityContext securityContext;

    @BeforeEach
    void setUp() {
        workspaceId = UUID.randomUUID();
        userId = UUID.randomUUID();
        workspaceIdStr = workspaceId.toString();

        CustomUserDetails customUserDetails = new CustomUserDetails(
                userId, "testuser", "password", "ROLE_USER", "test@example.com"
        );

        securityContext = mock(SecurityContext.class);
        Authentication authentication = mock(Authentication.class);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(customUserDetails);

        workspace = Workspace.builder()
                .id(workspaceId)
                .name("Test Workspace")
                .description("Test Description")
                .createdBy(userId)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    @Test
    @Order(1)
    @DisplayName("워크스페이스 생성 성공")
    void createWorkspace_Success() {
        // given
        WorkspaceCreateRequestDto request = WorkspaceCreateRequestDto.builder()
                .name("New Workspace")
                .description("New Description")
                .build();

        when(reactiveWorkspaceRepository.save(any(Workspace.class))).thenReturn(Mono.just(workspace));

        try (MockedStatic<ReactiveSecurityContextHolder> mockedSecurityContext =
                     mockStatic(ReactiveSecurityContextHolder.class)) {

            mockedSecurityContext.when(ReactiveSecurityContextHolder::getContext)
                    .thenReturn(Mono.just(securityContext));

            // when & then
            StepVerifier.create(workspaceService.createWorkspace(request))
                    .expectNextMatches(response -> {
                        assertThat(response.getName()).isEqualTo("Test Workspace");
                        assertThat(response.getDescription()).isEqualTo("Test Description");
                        assertThat(response.getCreatedAt()).isNotNull();
                        return true;
                    })
                    .verifyComplete();
        }

        verify(reactiveWorkspaceRepository).save(any(Workspace.class));
    }

    @Test
    @Order(2)
    @DisplayName("워크스페이스 생성 성공 - 설명 없음")
    void createWorkspace_Success_WithoutDescription() {
        // given
        WorkspaceCreateRequestDto request = WorkspaceCreateRequestDto.builder()
                .name("New Workspace")
                .build();

        Workspace workspaceWithoutDesc = Workspace.builder()
                .id(workspaceId)
                .name("New Workspace")
                .createdBy(userId)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(reactiveWorkspaceRepository.save(any(Workspace.class))).thenReturn(Mono.just(workspaceWithoutDesc));

        try (MockedStatic<ReactiveSecurityContextHolder> mockedSecurityContext =
                     mockStatic(ReactiveSecurityContextHolder.class)) {

            mockedSecurityContext.when(ReactiveSecurityContextHolder::getContext)
                    .thenReturn(Mono.just(securityContext));

            // when & then
            StepVerifier.create(workspaceService.createWorkspace(request))
                    .expectNextMatches(response -> {
                        assertThat(response.getName()).isEqualTo("New Workspace");
                        assertThat(response.getDescription()).isNull();
                        return true;
                    })
                    .verifyComplete();
        }
    }

    @Test
    @Order(3)
    @DisplayName("워크스페이스 수정 성공 - 소유자")
    void updateWorkspace_Success_Owner() {
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

        when(uuidUtils.fromString(workspaceIdStr)).thenReturn(workspaceId);
        when(reactiveWorkspaceRepository.findById(workspaceId)).thenReturn(Mono.just(workspace));
        when(reactiveWorkspaceRepository.save(any(Workspace.class))).thenReturn(Mono.just(updatedWorkspace));

        try (MockedStatic<ReactiveSecurityContextHolder> mockedSecurityContext =
                     mockStatic(ReactiveSecurityContextHolder.class)) {

            mockedSecurityContext.when(ReactiveSecurityContextHolder::getContext)
                    .thenReturn(Mono.just(securityContext));

            // when & then
            StepVerifier.create(workspaceService.updateWorkspace(request))
                    .expectNextMatches(response -> {
                        assertThat(response.getName()).isEqualTo("Updated Workspace");
                        assertThat(response.getDescription()).isEqualTo("Updated Description");
                        assertThat(response.getUpdatedAt()).isNotNull();
                        return true;
                    })
                    .verifyComplete();
        }

        verify(reactiveWorkspaceRepository).save(any(Workspace.class));
    }

    @Test
    @Order(4)
    @DisplayName("워크스페이스 수정 성공 - 설명만 수정")
    void updateWorkspace_Success_DescriptionOnly() {
        // given
        WorkspaceUpdateRequestDto request = WorkspaceUpdateRequestDto.builder()
                .workspaceId(workspaceIdStr)
                .name("Updated Name")
                .description(null) // description은 기존 것 유지
                .build();

        Workspace updatedWorkspace = Workspace.builder()
                .id(workspaceId)
                .name("Updated Name")
                .description("Test Description") // 기존 description 유지
                .createdBy(userId)
                .createdAt(workspace.getCreatedAt())
                .updatedAt(LocalDateTime.now())
                .build();

        when(uuidUtils.fromString(workspaceIdStr)).thenReturn(workspaceId);
        when(reactiveWorkspaceRepository.findById(workspaceId)).thenReturn(Mono.just(workspace));
        when(reactiveWorkspaceRepository.save(any(Workspace.class))).thenReturn(Mono.just(updatedWorkspace));

        try (MockedStatic<ReactiveSecurityContextHolder> mockedSecurityContext =
                     mockStatic(ReactiveSecurityContextHolder.class)) {

            mockedSecurityContext.when(ReactiveSecurityContextHolder::getContext)
                    .thenReturn(Mono.just(securityContext));

            // when & then
            StepVerifier.create(workspaceService.updateWorkspace(request))
                    .expectNextMatches(response -> {
                        assertThat(response.getName()).isEqualTo("Updated Name");
                        assertThat(response.getDescription()).isEqualTo("Test Description");
                        return true;
                    })
                    .verifyComplete();
        }
    }

    @Test
    @Order(5)
    @DisplayName("워크스페이스 수정 실패 - 워크스페이스 없음")
    void updateWorkspace_Fail_WorkspaceNotFound() {
        // given
        WorkspaceUpdateRequestDto request = WorkspaceUpdateRequestDto.builder()
                .workspaceId(workspaceIdStr)
                .name("Updated Workspace")
                .build();

        when(uuidUtils.fromString(workspaceIdStr)).thenReturn(workspaceId);
        when(reactiveWorkspaceRepository.findById(workspaceId)).thenReturn(Mono.empty());

        try (MockedStatic<ReactiveSecurityContextHolder> mockedSecurityContext =
                     mockStatic(ReactiveSecurityContextHolder.class)) {

            mockedSecurityContext.when(ReactiveSecurityContextHolder::getContext)
                    .thenReturn(Mono.just(securityContext));

            // when & then
            StepVerifier.create(workspaceService.updateWorkspace(request))
                    .expectError(WorkspaceException.class)
                    .verify();
        }
    }

    @Test
    @Order(6)
    @DisplayName("워크스페이스 수정 실패 - 권한 없음 (소유자 아님)")
    void updateWorkspace_Fail_PermissionDenied() {
        // given
        UUID otherUserId = UUID.randomUUID();
        Workspace otherUserWorkspace = Workspace.builder()
                .id(workspaceId)
                .name("Other User Workspace")
                .createdBy(otherUserId) // 다른 사용자가 소유
                .build();

        WorkspaceUpdateRequestDto request = WorkspaceUpdateRequestDto.builder()
                .workspaceId(workspaceIdStr)
                .name("Updated Workspace")
                .build();

        when(uuidUtils.fromString(workspaceIdStr)).thenReturn(workspaceId);
        when(reactiveWorkspaceRepository.findById(workspaceId)).thenReturn(Mono.just(otherUserWorkspace));

        try (MockedStatic<ReactiveSecurityContextHolder> mockedSecurityContext =
                     mockStatic(ReactiveSecurityContextHolder.class)) {

            mockedSecurityContext.when(ReactiveSecurityContextHolder::getContext)
                    .thenReturn(Mono.just(securityContext));

            // when & then
            StepVerifier.create(workspaceService.updateWorkspace(request))
                    .expectError(WorkspaceException.class)
                    .verify();
        }
    }

    @Test
    @Order(7)
    @DisplayName("워크스페이스 수정 실패 - 잘못된 이름 (빈 문자열)")
    void updateWorkspace_Fail_InvalidName_Empty() {
        // given
        WorkspaceUpdateRequestDto request = WorkspaceUpdateRequestDto.builder()
                .workspaceId(workspaceIdStr)
                .name("") // 빈 문자열
                .build();

        when(uuidUtils.fromString(workspaceIdStr)).thenReturn(workspaceId);
        when(reactiveWorkspaceRepository.findById(workspaceId)).thenReturn(Mono.just(workspace));

        try (MockedStatic<ReactiveSecurityContextHolder> mockedSecurityContext =
                     mockStatic(ReactiveSecurityContextHolder.class)) {

            mockedSecurityContext.when(ReactiveSecurityContextHolder::getContext)
                    .thenReturn(Mono.just(securityContext));

            // when & then
            StepVerifier.create(workspaceService.updateWorkspace(request))
                    .expectError(WorkspaceException.class)
                    .verify();
        }
    }

    @Test
    @Order(8)
    @DisplayName("워크스페이스 수정 실패 - 잘못된 이름 (null)")
    void updateWorkspace_Fail_InvalidName_Null() {
        // given
        WorkspaceUpdateRequestDto request = WorkspaceUpdateRequestDto.builder()
                .workspaceId(workspaceIdStr)
                .name(null) // null
                .build();

        when(uuidUtils.fromString(workspaceIdStr)).thenReturn(workspaceId);
        when(reactiveWorkspaceRepository.findById(workspaceId)).thenReturn(Mono.just(workspace));

        try (MockedStatic<ReactiveSecurityContextHolder> mockedSecurityContext =
                     mockStatic(ReactiveSecurityContextHolder.class)) {

            mockedSecurityContext.when(ReactiveSecurityContextHolder::getContext)
                    .thenReturn(Mono.just(securityContext));

            // when & then
            StepVerifier.create(workspaceService.updateWorkspace(request))
                    .expectError(WorkspaceException.class)
                    .verify();
        }
    }

    @Test
    @Order(9)
    @DisplayName("워크스페이스 삭제 성공 - 소유자")
    void deleteWorkspace_Success_Owner() {
        // given
        when(uuidUtils.fromString(workspaceIdStr)).thenReturn(workspaceId);
        when(reactiveWorkspaceRepository.findById(workspaceId)).thenReturn(Mono.just(workspace));
        when(reactiveWorkspaceRepository.delete(workspace)).thenReturn(Mono.empty());

        try (MockedStatic<ReactiveSecurityContextHolder> mockedSecurityContext =
                     mockStatic(ReactiveSecurityContextHolder.class)) {

            mockedSecurityContext.when(ReactiveSecurityContextHolder::getContext)
                    .thenReturn(Mono.just(securityContext));

            // when & then
            StepVerifier.create(workspaceService.deleteWorkspace(workspaceIdStr))
                    .verifyComplete();
        }

        verify(reactiveWorkspaceRepository).delete(workspace);
    }

    @Test
    @Order(10)
    @DisplayName("워크스페이스 삭제 실패 - 워크스페이스 없음")
    void deleteWorkspace_Fail_WorkspaceNotFound() {
        // given
        when(uuidUtils.fromString(workspaceIdStr)).thenReturn(workspaceId);
        when(reactiveWorkspaceRepository.findById(workspaceId)).thenReturn(Mono.empty());

        try (MockedStatic<ReactiveSecurityContextHolder> mockedSecurityContext =
                     mockStatic(ReactiveSecurityContextHolder.class)) {

            mockedSecurityContext.when(ReactiveSecurityContextHolder::getContext)
                    .thenReturn(Mono.just(securityContext));

            // when & then
            StepVerifier.create(workspaceService.deleteWorkspace(workspaceIdStr))
                    .expectError(WorkspaceException.class)
                    .verify();
        }
    }

    @Test
    @Order(11)
    @DisplayName("워크스페이스 삭제 실패 - 권한 없음 (소유자 아님)")
    void deleteWorkspace_Fail_PermissionDenied() {
        // given
        UUID otherUserId = UUID.randomUUID();
        Workspace otherUserWorkspace = Workspace.builder()
                .id(workspaceId)
                .name("Other User Workspace")
                .createdBy(otherUserId) // 다른 사용자가 소유
                .build();

        when(uuidUtils.fromString(workspaceIdStr)).thenReturn(workspaceId);
        when(reactiveWorkspaceRepository.findById(workspaceId)).thenReturn(Mono.just(otherUserWorkspace));

        try (MockedStatic<ReactiveSecurityContextHolder> mockedSecurityContext =
                     mockStatic(ReactiveSecurityContextHolder.class)) {

            mockedSecurityContext.when(ReactiveSecurityContextHolder::getContext)
                    .thenReturn(Mono.just(securityContext));

            // when & then
            StepVerifier.create(workspaceService.deleteWorkspace(workspaceIdStr))
                    .expectError(WorkspaceException.class)
                    .verify();
        }
    }
}