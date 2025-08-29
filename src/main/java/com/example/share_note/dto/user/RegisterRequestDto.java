package com.example.share_note.dto.user;

import lombok.Getter;

@Getter
public class RegisterRequestDto {
    private String username;
    private String password;
    private String roles;
    private String email;
}
