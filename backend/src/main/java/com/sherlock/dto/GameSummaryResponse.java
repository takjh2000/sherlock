package com.sherlock.dto;

import com.sherlock.domain.Game;
import com.sherlock.domain.GameCategory;
import com.sherlock.domain.GameNote;

/** 목록 항목. latestNote는 특이사항이 없으면 null. */
public record GameSummaryResponse(Long id, String name, GameCategory category, String owner,
		int totalQuantity, int availableQuantity, GameNoteResponse latestNote) {

	public static GameSummaryResponse of(Game game, long activeRentalCount, GameNote latestNote) {
		return new GameSummaryResponse(game.getId(), game.getName(), game.getCategory(), game.getOwner(),
				game.getTotalQuantity(), game.availableQuantity(activeRentalCount),
				latestNote == null ? null : GameNoteResponse.from(latestNote));
	}
}
