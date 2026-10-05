package com.sherlock.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(@NotBlank String studentId, @NotBlank String password) {
}
