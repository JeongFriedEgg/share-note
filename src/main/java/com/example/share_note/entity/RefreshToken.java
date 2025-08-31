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
@Table("refresh_token")
public class RefreshToken {
    @Id
    private Long id;
    private String refreshToken;
    private String username;
    private String ipAddress;
    private String deviceName;
    private String osName;
    private String browserName;
    private LocalDateTime expirationDate;
    private LocalDateTime createdAt;
}
