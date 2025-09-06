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
@Table(name = "blocks")
public class Block {
    @Id
    private Long id;

    @Column("page_id")
    private Long pageId;

    @Column("parent_block_id")
    private Long parentBlockId;

    @Column("type")
    private String type;

    @Column("content")
    private String content;

    @Column("position")
    private Integer position;

    @Column("is_archived")
    private boolean isArchived;

    @Column("created_at")
    private LocalDateTime createdAt;

    @Column("updated_at")
    private LocalDateTime updatedAt;

    @Column("created_by")
    private Long createdBy;

    @Column("last_edited_by")
    private Long lastEditedBy;
}
