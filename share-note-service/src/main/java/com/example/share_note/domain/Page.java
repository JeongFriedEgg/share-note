package com.example.share_note.domain;

import com.example.share_note.enums.PagePermissionType;
import lombok.*;
import org.springframework.cglib.core.Block;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "pages")
public class Page {

    @Id
    private UUID id;

    @Column("workspace_id")
    private UUID workspaceId;

    @Column("parent_page_id")
    private UUID parentPageId;

    @Column("title")
    private String title;

    @Column("icon")
    private String icon;

    @Column("cover")
    private String cover;

    @Column("properties")
    private String properties;

    @Column("is_public")
    private boolean isPublic;

    @Column("is_archived")
    private boolean isArchived;

    @Column("is_template")
    private boolean isTemplate;

    @Column("created_at")
    private LocalDateTime createdAt;

    @Column("updated_at")
    private LocalDateTime updatedAt;

    @Column("created_by")
    private UUID createdBy;

    @Column("last_edited_by")
    private UUID lastEditedBy;

    // R2DBC는 관계를 직접 매핑하지 않으므로 @Transient로 무시
    @Transient
    private Set<PagePermissionType> permissions;

    @Transient
    private Set<Block> blocks;
}
