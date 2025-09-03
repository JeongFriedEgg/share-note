package com.example.share_note.domain;

import com.example.share_note.enums.WorkspaceRole;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "workspace_members")
public class WorkspaceMember {

    @Id
    private Long id;

    @Column("workspace_id")
    private Long workspaceId;

    @Column("user_id")
    private Long userId;

    @Column("role")
    private WorkspaceRole role;

    @Column("joined_at")
    private LocalDateTime joinedAt;
}