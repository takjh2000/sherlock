package com.sherlock.service;

import com.sherlock.domain.Member;
import com.sherlock.domain.Rental;
import com.sherlock.domain.RentalStatus;
import com.sherlock.dto.AdminRentalResponse;
import com.sherlock.dto.MemberOverdueStatResponse;
import com.sherlock.repository.RentalRepository;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 관리자의 대여 현황 조회와 강제 반납. */
@Service
@Transactional
public class AdminRentalService {

	private final RentalRepository rentalRepository;

	public AdminRentalService(RentalRepository rentalRepository) {
		this.rentalRepository = rentalRepository;
	}

	/** 전체 대여 중(미반납) 목록. 반납 예정일이 빠른 순. */
	@Transactional(readOnly = true)
	public List<AdminRentalResponse> activeRentals() {
		LocalDate today = LocalDate.now();
		return rentalRepository.findAllWithGameAndMemberByStatus(RentalStatus.RENTED).stream()
				.map(r -> AdminRentalResponse.of(r, today)).toList();
	}

	/** 지금 연체 중인 미반납 목록. 연체 일수가 긴 순. */
	@Transactional(readOnly = true)
	public List<AdminRentalResponse> overdueRentals() {
		LocalDate today = LocalDate.now();
		return rentalRepository.findAllWithGameAndMemberByStatus(RentalStatus.RENTED).stream()
				.filter(r -> r.isOverdue(today))
				.sorted(Comparator.comparingLong((Rental r) -> r.overdueDays(today)).reversed()
						.thenComparing(Rental::getId))
				.map(r -> AdminRentalResponse.of(r, today)).toList();
	}

	/** 회원별 연체 누적 건수/일수. 반납 완료된 연체도 포함하며 연체 일수가 많은 순. */
	@Transactional(readOnly = true)
	public List<MemberOverdueStatResponse> memberOverdueStats() {
		LocalDate today = LocalDate.now();
		Map<Long, List<Rental>> byMember = new LinkedHashMap<>();
		for (Rental r : rentalRepository.findOverdueCandidates(today)) {
			byMember.computeIfAbsent(r.getMember().getId(), k -> new java.util.ArrayList<>()).add(r);
		}
		return byMember.values().stream().map(rentals -> {
			Member member = rentals.get(0).getMember();
			return new MemberOverdueStatResponse(member.getStudentId(), member.getName(), rentals.size(),
					rentals.stream().mapToLong(r -> r.overdueDays(today)).sum(),
					rentals.stream().filter(r -> r.getStatus() == RentalStatus.RENTED).count());
		}).sorted(Comparator.comparingLong(MemberOverdueStatResponse::overdueDays).reversed()
				.thenComparing(MemberOverdueStatResponse::studentId)).toList();
	}

	/** 회원이 반납 처리를 못 한 경우 관리자가 오늘 날짜로 반납 처리한다. */
	public AdminRentalResponse forceReturn(Long rentalId) {
		Rental rental = rentalRepository.findByIdForUpdate(rentalId)
				.orElseThrow(() -> new AdminException(HttpStatus.NOT_FOUND, "RENTAL_NOT_FOUND",
						"대여 내역을 찾을 수 없습니다."));
		if (rental.getStatus() == RentalStatus.RETURNED) {
			throw new AdminException(HttpStatus.CONFLICT, "ALREADY_RETURNED", "이미 반납된 대여입니다.");
		}
		LocalDate today = LocalDate.now();
		rental.markReturned(today);
		return AdminRentalResponse.of(rental, today);
	}
}
