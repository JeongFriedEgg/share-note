package com.example.share_note.controller;

import com.example.share_note.dto.page.*;
import com.example.share_note.dto.page.PageListResponseDto;
import com.example.share_note.dto.page.PageUpdatePermissionResponseDto;
import com.example.share_note.dto.page.PageResponseDto;
import com.example.share_note.service.PageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/workspaces/{workspaceId}/pages")
@RequiredArgsConstructor
public class PageController {

    private final PageService pageService;

    @PostMapping
    public Mono<ResponseEntity<PageCreateResponseDto>> createPage(
            @PathVariable String workspaceId,
            @RequestBody PageCreateRequestDto request) {
        return pageService.createPage(workspaceId, request)
                .map(response ->
                        ResponseEntity.status(HttpStatus.CREATED).body(response));
    }

    @GetMapping
    public Mono<ResponseEntity<PageListResponseDto>> getPages(
            @PathVariable String workspaceId) {
        return pageService.getPages(workspaceId)
                .map(ResponseEntity::ok);
    }

    @GetMapping("/{pageId}")
    public Mono<ResponseEntity<PageResponseDto>> getPage(
            @PathVariable String workspaceId,
            @PathVariable String pageId) {
        return pageService.getPage(workspaceId, pageId)
                .map(ResponseEntity::ok);
    }

    @PutMapping("/{pageId}")
    public Mono<ResponseEntity<PageResponseDto>> updatePage(
            @PathVariable String workspaceId,
            @PathVariable String pageId,
            @RequestBody PageUpdateRequestDto request) {
        return pageService.updatePage(workspaceId, pageId, request)
                .map(ResponseEntity::ok);
    }

    @PostMapping("/{pageId}/members")
    public Mono<ResponseEntity<PageInviteResponseDto>> inviteMemberToPage(
            @PathVariable String workspaceId,
            @PathVariable String pageId,
            @RequestBody PageInviteRequestDto request) {
        return pageService.inviteMemberToPage(workspaceId, pageId, request)
                .map(response ->
                        ResponseEntity.status(HttpStatus.CREATED).body(response));
    }

    @PutMapping("/{pageId}/members/{userId}")
    public Mono<ResponseEntity<PageUpdatePermissionResponseDto>> updateMemberPagePermission(
            @PathVariable String workspaceId,
            @PathVariable String pageId,
            @PathVariable String userId,
            @RequestBody PageUpdatePermissionRequestDto request) {
        return pageService.updateMemberPagePermission(workspaceId, pageId, userId, request)
                .map(ResponseEntity::ok);
    }

    @PutMapping("/{pageId}/public")
    public Mono<ResponseEntity<PagePublicStatusUpdateResponseDto>> updatePagePublicStatus(
            @PathVariable String workspaceId,
            @PathVariable String pageId,
            @RequestBody PagePublicStatusUpdateRequestDto request) {
        return pageService.updatePagePublicStatus(workspaceId, pageId, request)
                .map(ResponseEntity::ok);
    }

    @PutMapping("/{pageId}/archive")
    public Mono<ResponseEntity<PageStatusResponseDto>> archivePage(
            @PathVariable String workspaceId,
            @PathVariable String pageId) {
        return pageService.archivePage(workspaceId, pageId)
                .map(ResponseEntity::ok);
    }

    @PutMapping("/{pageId}/restore")
    public Mono<ResponseEntity<PageStatusResponseDto>> restorePage(
            @PathVariable String workspaceId,
            @PathVariable String pageId) {
        return pageService.restorePage(workspaceId, pageId)
                .map(ResponseEntity::ok);
    }

    @DeleteMapping("/{pageId}")
    public Mono<ResponseEntity<Void>> deletePage(
            @PathVariable String workspaceId,
            @PathVariable String pageId) {
        return pageService.deletePage(workspaceId, pageId)
                .then(Mono.just(ResponseEntity.noContent().build()));
    }
}