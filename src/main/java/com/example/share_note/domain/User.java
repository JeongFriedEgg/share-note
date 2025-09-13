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
@Table("users")
public class User {
    @Id
    private UUID id;

    @Column("username")
    private String username;

    @Column("password")
    private String password;

    @Column("authorities")
    private String authorities;

    @Column("email")
    private String email;

    @Column("created_at")
    private LocalDateTime createdAt;
}
