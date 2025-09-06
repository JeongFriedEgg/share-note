package com.example.share_note.dto.workspace;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkspaceUpdateResponseDto {
    private String name;
    private String description;
    private LocalDateTime updatedAt;
}
