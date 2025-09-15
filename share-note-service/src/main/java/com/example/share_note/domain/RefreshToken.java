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
@Table("refresh_token")
public class RefreshToken {
    @Id
    private UUID id;

    @Column("refresh_token")
    private String refreshToken;

    @Column("username")
    private String username;

    @Column("ip_address")
    private String ipAddress;

    @Column("device_name")
    private String deviceName;

    @Column("os_name")
    private String osName;

    @Column("browser_name")
    private String browserName;

    @Column("expiration_date")
    private LocalDateTime expirationDate;

    @Column("created_at")
    private LocalDateTime createdAt;
}
