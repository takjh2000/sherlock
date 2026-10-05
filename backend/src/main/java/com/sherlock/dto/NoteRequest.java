package com.sherlock.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 특이사항 작성/수정 요청. */
public record NoteRequest(@NotBlank @Size(max = 1000) String content) {
}
