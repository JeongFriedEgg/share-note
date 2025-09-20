package com.sharenote.redistribution.entity;

import com.sharenote.redistribution.enums.MigrationStatus;
import com.sharenote.redistribution.enums.PagePermissionType;
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
@Table(name = "page_permissions")
public class PagePermission {
    @Id
    @Column(columnDefinition = "UUID")
    private UUID id;

    @Column(name = "page_id", columnDefinition = "UUID", nullable = false)
    private UUID pageId;

    @Column(name = "user_id", columnDefinition = "UUID", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "permission_type")
    private PagePermissionType permission;

    @Column(name = "granted_at")
    @Builder.Default
    private LocalDateTime grantedAt = LocalDateTime.now();

    @Column(name = "granted_by", columnDefinition = "UUID")
    private UUID grantedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "migration_status")
    @Builder.Default
    private MigrationStatus migrationStatus = MigrationStatus.READY;
}
