package com.example.share_note.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table("users")
public class UserEntity {
    @Id
    private Long id;
    private String username;
    private String password;
    private String authorities;
    private String email;
    private LocalDateTime createdAt;
}
