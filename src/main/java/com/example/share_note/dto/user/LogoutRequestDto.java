package com.example.share_note.dto.user;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

@Getter
public class LogoutRequestDto {
    @JsonProperty("refresh_token")
    @NotBlank(message = "리프레시 토큰은 필수 값입니다.")
    private String refreshToken;
}
