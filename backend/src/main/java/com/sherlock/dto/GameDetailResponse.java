package com.sherlock.dto;

import com.sherlock.domain.Game;
import com.sherlock.domain.GameCategory;
import java.util.List;

public record GameDetailResponse(Long id, String name, GameCategory category, String owner,
		int totalQuantity, int availableQuantity, List<GameNoteResponse> notes) {

	public static GameDetailResponse of(Game game, long activeRentalCount, List<GameNoteResponse> notes) {
		return new GameDetailResponse(game.getId(), game.getName(), game.getCategory(), game.getOwner(),
				game.getTotalQuantity(), game.availableQuantity(activeRentalCount), notes);
	}
}
