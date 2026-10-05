package com.sherlock.dto;

import com.sherlock.domain.GameCategory;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 게임 등록/수정 요청. */
public record GameRequest(
		@NotBlank @Size(max = 255) String name,
		@NotNull GameCategory category,
		@Size(max = 255) String owner,
		@Min(1) int totalQuantity) {
}
