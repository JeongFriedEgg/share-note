package com.example.share_note.domain;

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
@Table(name = "page_permissions")
public class PagePermission {
    @Id
    private UUID id;

    @Column("page_id")
    private UUID pageId;

    @Column("user_id")
    private UUID userId;

    @Column("permission")
    private String permission;

    @Column("granted_at")
    private LocalDateTime grantedAt;

    @Column("granted_by")
    private UUID grantedBy;
}
