package com.example.share_note.domain;

import com.example.share_note.enums.WorkspaceRole;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "workspace_members")
public class WorkspaceMember {

    @Id
    private UUID id;

    @Column("workspace_id")
    private UUID workspaceId;

    @Column("user_id")
    private UUID userId;

    @Column("role")
    private WorkspaceRole role;

    @Column("joined_at")
    private LocalDateTime joinedAt;
}