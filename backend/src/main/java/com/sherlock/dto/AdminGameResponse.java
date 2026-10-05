package com.sherlock.dto;

import com.sherlock.domain.Game;
import com.sherlock.domain.GameCategory;
import com.sherlock.domain.GameNote;

/** 관리자 목록 항목. deleted가 true면 soft delete된 게임이다. */
public record AdminGameResponse(Long id, String name, GameCategory category, String owner,
		int totalQuantity, int availableQuantity, GameNoteResponse latestNote, boolean deleted) {

	public static AdminGameResponse of(Game game, long activeRentalCount, GameNote latestNote) {
		return new AdminGameResponse(game.getId(), game.getName(), game.getCategory(), game.getOwner(),
				game.getTotalQuantity(), game.availableQuantity(activeRentalCount),
				latestNote == null ? null : GameNoteResponse.from(latestNote), game.isDeleted());
	}
}
