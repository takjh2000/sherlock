package com.sherlock.service;

import com.sherlock.domain.Game;
import com.sherlock.domain.GameNote;
import com.sherlock.domain.Member;
import com.sherlock.domain.RentalStatus;
import com.sherlock.dto.AdminGameDetailResponse;
import com.sherlock.dto.GameNoteResponse;
import com.sherlock.dto.GameRequest;
import com.sherlock.repository.GameNoteRepository;
import com.sherlock.repository.GameRepository;
import com.sherlock.repository.MemberRepository;
import com.sherlock.repository.RentalRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 관리자의 게임/특이사항 관리. 게임 삭제는 soft delete다. */
@Service
@Transactional
public class AdminGameService {

	private final GameRepository gameRepository;
	private final RentalRepository rentalRepository;
	private final GameNoteRepository gameNoteRepository;
	private final MemberRepository memberRepository;
	private final GameQueryService gameQueryService;

	public AdminGameService(GameRepository gameRepository, RentalRepository rentalRepository,
			GameNoteRepository gameNoteRepository, MemberRepository memberRepository,
			GameQueryService gameQueryService) {
		this.gameRepository = gameRepository;
		this.rentalRepository = rentalRepository;
		this.gameNoteRepository = gameNoteRepository;
		this.memberRepository = memberRepository;
		this.gameQueryService = gameQueryService;
	}

	public AdminGameDetailResponse create(GameRequest request) {
		Game game = gameRepository.save(new Game(request.name().trim(), request.category(),
				normalizeOwner(request.owner()), request.totalQuantity()));
		return gameQueryService.adminDetail(game.getId());
	}

	/** 전체 수량은 현재 대여 중인 수량보다 작게 줄일 수 없다. */
	public AdminGameDetailResponse update(Long id, GameRequest request) {
		Game game = lockActive(id);
		long active = rentalRepository.countByGameAndStatus(game, RentalStatus.RENTED);
		if (request.totalQuantity() < active) {
			throw new AdminException(HttpStatus.CONFLICT, "QUANTITY_BELOW_RENTED",
					"대여 중인 수량(" + active + "개)보다 적게 줄일 수 없습니다.");
		}
		game.update(request.name().trim(), request.category(), normalizeOwner(request.owner()),
				request.totalQuantity());
		return gameQueryService.adminDetail(id);
	}

	/** soft delete. 대여 중인 건이 있으면 삭제할 수 없다. 대여와 같은 행 락을 잡아 경쟁을 막는다. */
	public void delete(Long id) {
		Game game = lockActive(id);
		if (rentalRepository.countByGameAndStatus(game, RentalStatus.RENTED) > 0) {
			throw new AdminException(HttpStatus.CONFLICT, "GAME_RENTED", "대여 중인 게임은 삭제할 수 없습니다.");
		}
		game.delete();
	}

	public GameNoteResponse addNote(String adminStudentId, Long gameId, String content) {
		Game game = gameRepository.findById(gameId).orElseThrow(() -> new GameNotFoundException(gameId));
		Member author = memberRepository.findByStudentId(adminStudentId)
				.orElseThrow(() -> new AdminException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED",
						"로그인이 필요합니다."));
		return GameNoteResponse.from(gameNoteRepository.save(new GameNote(game, content.trim(), author)));
	}

	public GameNoteResponse updateNote(Long noteId, String content) {
		GameNote note = findNote(noteId);
		note.updateContent(content.trim());
		return GameNoteResponse.from(note);
	}

	public void deleteNote(Long noteId) {
		gameNoteRepository.delete(findNote(noteId));
	}

	private Game lockActive(Long id) {
		return gameRepository.findActiveByIdForUpdate(id).orElseThrow(() -> new GameNotFoundException(id));
	}

	private GameNote findNote(Long noteId) {
		return gameNoteRepository.findById(noteId)
				.orElseThrow(() -> new AdminException(HttpStatus.NOT_FOUND, "NOTE_NOT_FOUND",
						"특이사항을 찾을 수 없습니다."));
	}

	private static String normalizeOwner(String owner) {
		return owner == null || owner.isBlank() ? null : owner.trim();
	}
}
