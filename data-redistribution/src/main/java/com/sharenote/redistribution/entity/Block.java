package com.sharenote.redistribution.entity;

import com.sharenote.redistribution.enums.MigrationStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
@Entity
@Table(name = "blocks")
public class Block {
    @Id
    @Column(columnDefinition = "UUID")
    private UUID id;

    @Column(name = "page_id", columnDefinition = "UUID", nullable = false)
    private UUID pageId;

    @Column(name = "parent_block_id", columnDefinition = "UUID")
    private UUID parentBlockId;

    @Column(nullable = false)
    private String type;

    @Column(columnDefinition = "JSONB")
    @JdbcTypeCode(SqlTypes.JSON)
    private String content;

    @Column(nullable = false)
    private Integer position;

    @Column(name = "is_archived")
    @Builder.Default
    private Boolean isArchived = false;

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
}
