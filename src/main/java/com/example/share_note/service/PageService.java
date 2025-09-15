package com.example.share_note.service;

import com.example.share_note.dto.page.*;
import reactor.core.publisher.Mono;

public interface PageService {
    Mono<PageCreateResponseDto> createPage(String workspaceIdStr, PageCreateRequestDto request);

    Mono<PageListResponseDto> getPages(String workspaceIdStr);

    Mono<PageResponseDto> getPage(String workspaceIdStr, String pageIdStr);

    Mono<PageResponseDto> updatePage(String workspaceIdStr, String pageIdStr, PageUpdateRequestDto request);

    Mono<PageInviteResponseDto> inviteMemberToPage(
            String workspaceIdStr, String pageIdStr, PageInviteRequestDto request);

    Mono<PageUpdatePermissionResponseDto> updateMemberPagePermission(
            String workspaceIdStr, String pageIdStr, String targetUserIdStr, PageUpdatePermissionRequestDto request);

    Mono<PagePublicStatusUpdateResponseDto> updatePagePublicStatus(
            String workspaceIdStr, String pageIdStr, PagePublicStatusUpdateRequestDto request);

    Mono<PageStatusResponseDto> archivePage(String workspaceIdStr, String pageIdStr);

    Mono<PageStatusResponseDto> restorePage(String workspaceIdStr, String pageIdStr);

    Mono<Void> deletePage(String workspaceIdStr, String pageIdStr);
}
