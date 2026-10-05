package com.sherlock.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class RentalTest {

	private static final LocalDate RENTED = LocalDate.of(2026, 3, 1);

	private Rental newRental() {
		Game game = new Game("카탄", GameCategory.BOARD_GAME, null, 1);
		Member member = new Member("20240001", "홍길동", Role.MEMBER);
		return new Rental(game, member, RENTED);
	}

	@Test
	void 반납_예정일은_대여일_더하기_7일이다() {
		assertThat(newRental().getDueDate()).isEqualTo(LocalDate.of(2026, 3, 8));
	}

	@Test
	void 반납_예정일_당일까지는_연체가_아니다() {
		Rental rental = newRental();
		assertThat(rental.isOverdue(LocalDate.of(2026, 3, 8))).isFalse();
		assertThat(rental.overdueDays(LocalDate.of(2026, 3, 8))).isZero();
	}

	@Test
	void 미반납_상태에서_예정일이_지나면_연체_일수를_계산한다() {
		Rental rental = newRental();
		assertThat(rental.isOverdue(LocalDate.of(2026, 3, 11))).isTrue();
		assertThat(rental.overdueDays(LocalDate.of(2026, 3, 11))).isEqualTo(3);
	}

	@Test
	void 늦게_반납하면_반납일_기준으로_연체_일수가_고정된다() {
		Rental rental = newRental();
		rental.markReturned(LocalDate.of(2026, 3, 10));
		assertThat(rental.getStatus()).isEqualTo(RentalStatus.RETURNED);
		assertThat(rental.overdueDays(LocalDate.of(2026, 4, 1))).isEqualTo(2);
	}

	@Test
	void 제때_반납하면_연체가_아니다() {
		Rental rental = newRental();
		rental.markReturned(LocalDate.of(2026, 3, 8));
		assertThat(rental.isOverdue(LocalDate.of(2026, 4, 1))).isFalse();
	}

	@Test
	void 이미_반납된_대여는_다시_반납할_수_없다() {
		Rental rental = newRental();
		rental.markReturned(LocalDate.of(2026, 3, 5));
		assertThatThrownBy(() -> rental.markReturned(LocalDate.of(2026, 3, 6)))
				.isInstanceOf(IllegalStateException.class);
	}
}
