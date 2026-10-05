package com.sherlock.dto;

import com.sherlock.domain.GameNote;
import java.time.LocalDateTime;

public record GameNoteResponse(String content, String authorName, LocalDateTime createdAt) {

	public static GameNoteResponse from(GameNote note) {
		return new GameNoteResponse(note.getContent(), note.getAuthor().getName(), note.getCreatedAt());
	}
}
