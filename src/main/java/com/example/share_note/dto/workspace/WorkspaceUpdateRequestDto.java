package com.example.share_note.dto.workspace;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class WorkspaceUpdateRequestDto {
    private String workspaceId;
    private String name;
    private String description;
}
