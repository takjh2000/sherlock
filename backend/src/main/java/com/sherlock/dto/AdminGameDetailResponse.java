package com.sherlock.dto;

import com.sherlock.domain.GameCategory;
import java.util.List;

/** 관리자 상세. 삭제된 게임도 deleted=true로 조회된다. */
public record AdminGameDetailResponse(Long id, String name, GameCategory category, String owner,
		int totalQuantity, int availableQuantity, List<GameNoteResponse> notes, boolean deleted) {

	public AdminGameDetailResponse(GameDetailResponse detail, boolean deleted) {
		this(detail.id(), detail.name(), detail.category(), detail.owner(), detail.totalQuantity(),
				detail.availableQuantity(), detail.notes(), deleted);
	}
}
