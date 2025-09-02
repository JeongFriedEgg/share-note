package com.example.share_note.exception;

import lombok.*;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ErrorResponseDto {
    private HttpStatus status;
    private String code;
    private String message;
    private LocalDateTime timestamp;
}
