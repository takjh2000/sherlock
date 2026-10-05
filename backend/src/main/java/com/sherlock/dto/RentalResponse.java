package com.sherlock.dto;

import com.sherlock.domain.Rental;
import com.sherlock.domain.RentalStatus;
import java.time.LocalDate;

/** daysLeft: 반납 예정일까지 D-n(반납 완료면 null, 연체면 0), overdueDays: 연체 일수(연체 아니면 0). */
public record RentalResponse(Long id, Long gameId, String gameName, LocalDate rentedDate,
		LocalDate dueDate, LocalDate returnedDate, RentalStatus status, Long daysLeft,
		long overdueDays) {

	public static RentalResponse of(Rental rental, LocalDate today) {
		boolean returned = rental.getStatus() == RentalStatus.RETURNED;
		return new RentalResponse(rental.getId(), rental.getGame().getId(), rental.getGame().getName(),
				rental.getRentedDate(), rental.getDueDate(), rental.getReturnedDate(), rental.getStatus(),
				returned ? null : Math.max(0, rental.daysLeft(today)), rental.overdueDays(today));
	}
}
