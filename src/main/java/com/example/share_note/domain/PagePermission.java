package com.example.share_note.domain;

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
@Table(name = "page_permissions")
public class PagePermission {
    @Id
    private Long id;

    @Column("page_id")
    private Long pageId;

    @Column("user_id")
    private Long userId;

    @Column("permission")
    private String permission;

    @Column("granted_at")
    private LocalDateTime grantedAt;

    @Column("granted_by")
    private Long grantedBy;
}
