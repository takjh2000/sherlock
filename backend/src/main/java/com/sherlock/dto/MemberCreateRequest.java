package com.sherlock.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 회원 명단 단건 등록 요청(학번 + 이름). */
public record MemberCreateRequest(
		@NotBlank @Size(max = 20) @Pattern(regexp = "\\s*\\S+\\s*") String studentId,
		@NotBlank @Size(max = 50) String name) {
}
