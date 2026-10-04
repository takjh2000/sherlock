package com.sherlock.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class GameTest {

	@Test
	void 대여_가능_수량은_전체_수량에서_미반납_수를_뺀다() {
		Game game = new Game("카탄", GameCategory.BOARD_GAME, "홍길동", 3);
		assertThat(game.availableQuantity(0)).isEqualTo(3);
		assertThat(game.availableQuantity(2)).isEqualTo(1);
		assertThat(game.availableQuantity(3)).isZero();
	}

	@Test
	void 전체_수량은_1_이상이어야_한다() {
		assertThatThrownBy(() -> new Game("카탄", GameCategory.BOARD_GAME, null, 0))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void 삭제는_soft_delete로_처리한다() {
		Game game = new Game("카탄", GameCategory.BOARD_GAME, null, 1);
		game.delete();
		assertThat(game.isDeleted()).isTrue();
	}
}
