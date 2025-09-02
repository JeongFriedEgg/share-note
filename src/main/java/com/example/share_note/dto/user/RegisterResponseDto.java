package com.example.share_note.dto.user;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RegisterResponseDto {
    private String message;
    private String username;
    private String email;
}
