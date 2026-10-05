package com.sherlock.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// BCrypt는 72바이트까지만 반영하므로 최대 길이를 제한한다.
public record SetupPasswordRequest(
		@NotBlank String studentId,
		@NotBlank String name,
		@NotBlank @Size(min = 8, max = 72) String password) {
}
