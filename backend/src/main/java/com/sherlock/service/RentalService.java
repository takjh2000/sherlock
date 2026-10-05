package com.sherlock.service;

import com.sherlock.domain.Game;
import com.sherlock.domain.GameNote;
import com.sherlock.domain.Member;
import com.sherlock.domain.Rental;
import com.sherlock.domain.RentalStatus;
import com.sherlock.dto.RentalResponse;
import com.sherlock.repository.GameNoteRepository;
import com.sherlock.repository.GameRepository;
import com.sherlock.repository.MemberRepository;
import com.sherlock.repository.RentalRepository;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class RentalService {

	private final RentalRepository rentalRepository;
	private final GameRepository gameRepository;
	private final GameNoteRepository gameNoteRepository;
	private final MemberRepository memberRepository;

	public RentalService(RentalRepository rentalRepository, GameRepository gameRepository,
			GameNoteRepository gameNoteRepository, MemberRepository memberRepository) {
		this.rentalRepository = rentalRepository;
		this.gameRepository = gameRepository;
		this.gameNoteRepository = gameNoteRepository;
		this.memberRepository = memberRepository;
	}

	/** 대여한다. 게임 행을 락으로 잡아 마지막 1개를 동시에 대여해도 한 명만 성공한다. */
	public RentalResponse rent(String studentId, Long gameId) {
		Member member = getMember(studentId);
		Game game = gameRepository.findActiveByIdForUpdate(gameId)
				.orElseThrow(() -> new GameNotFoundException(gameId));
		long active = rentalRepository.countByGameAndStatus(game, RentalStatus.RENTED);
		if (game.availableQuantity(active) <= 0) {
			throw new RentalException(HttpStatus.CONFLICT, "OUT_OF_STOCK", "대여 가능한 수량이 없습니다.");
		}
		LocalDate today = LocalDate.now();
		Rental rental = rentalRepository.save(new Rental(game, member, today));
		return RentalResponse.of(rental, today);
	}

	@Transactional(readOnly = true)
	public List<RentalResponse> myRentals(String studentId) {
		Member member = getMember(studentId);
		LocalDate today = LocalDate.now();
		return rentalRepository.findByMemberWithGame(member).stream()
				.map(r -> RentalResponse.of(r, today)).toList();
	}

	/** 본인 대여만 반납한다. report가 비어 있지 않으면 해당 게임의 특이사항 이력에 추가한다. */
	public RentalResponse returnRental(String studentId, Long rentalId, String report) {
		Member member = getMember(studentId);
		Rental rental = rentalRepository.findByIdForUpdate(rentalId)
				.orElseThrow(() -> new RentalException(HttpStatus.NOT_FOUND, "RENTAL_NOT_FOUND",
						"대여 내역을 찾을 수 없습니다."));
		if (!rental.getMember().getId().equals(member.getId())) {
			throw new RentalException(HttpStatus.FORBIDDEN, "NOT_RENTAL_OWNER",
					"본인의 대여만 반납할 수 있습니다.");
		}
		if (rental.getStatus() == RentalStatus.RETURNED) {
			throw new RentalException(HttpStatus.CONFLICT, "ALREADY_RETURNED", "이미 반납된 대여입니다.");
		}
		LocalDate today = LocalDate.now();
		rental.markReturned(today);
		if (report != null && !report.isBlank()) {
			gameNoteRepository.save(new GameNote(rental.getGame(), report.trim(), member));
		}
		return RentalResponse.of(rental, today);
	}

	private Member getMember(String studentId) {
		return memberRepository.findByStudentId(studentId).filter(Member::isActive)
				.orElseThrow(() -> new RentalException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED",
						"로그인이 필요합니다."));
	}
}
