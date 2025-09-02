package com.example.share_note.dto.user;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class RegisterRequestDto {
    private String username;
    private String password;
    private String roles;
    private String email;
}
