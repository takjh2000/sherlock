package com.sherlock.dto;

import com.sherlock.domain.Rental;
import com.sherlock.domain.RentalStatus;
import java.time.LocalDate;

/** 관리자용 대여 항목. overdueDays는 연체 일수(연체 아니면 0). */
public record AdminRentalResponse(Long id, Long gameId, String gameName, String studentId, String memberName,
		LocalDate rentedDate, LocalDate dueDate, LocalDate returnedDate, RentalStatus status,
		long overdueDays) {

	public static AdminRentalResponse of(Rental rental, LocalDate today) {
		return new AdminRentalResponse(rental.getId(), rental.getGame().getId(), rental.getGame().getName(),
				rental.getMember().getStudentId(), rental.getMember().getName(), rental.getRentedDate(),
				rental.getDueDate(), rental.getReturnedDate(), rental.getStatus(), rental.overdueDays(today));
	}
}
