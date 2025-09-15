package com.example.share_note.dto.workspace;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class WorkspaceCreateRequestDto {
    private String name;
    private String description;
}
