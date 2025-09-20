package com.sharenote.redistribution.entity;

import com.sharenote.redistribution.enums.MigrationStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
@Entity
@Table(name = "pages")
public class Page {
    @Id
    @Column(columnDefinition = "UUID")
    private UUID id;

    @Column(name = "workspace_id", columnDefinition = "UUID")
    private UUID workspaceId;

    @Column(name = "parent_page_id", columnDefinition = "UUID")
    private UUID parentPageId;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "JSONB")
    private String icon;

    @Column(columnDefinition = "JSONB")
    private String cover;

    @Column(columnDefinition = "JSONB")
    private String properties;

    @Column(name = "is_public")
    @Builder.Default
    private Boolean isPublic = false;

    @Column(name = "is_archived")
    @Builder.Default
    private Boolean isArchived = false;

    @Column(name = "is_template")
    @Builder.Default
    private Boolean isTemplate = false;

    @Column(name = "created_at")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    @Column(name = "created_by", columnDefinition = "UUID")
    private UUID createdBy;

    @Column(name = "last_edited_by", columnDefinition = "UUID")
    private UUID lastEditedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "migration_status")
    @Builder.Default
    private MigrationStatus migrationStatus = MigrationStatus.READY;

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
