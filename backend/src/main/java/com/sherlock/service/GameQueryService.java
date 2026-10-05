package com.sherlock.service;

import com.sherlock.domain.Game;
import com.sherlock.domain.GameCategory;
import com.sherlock.domain.GameNote;
import com.sherlock.domain.Rental;
import com.sherlock.domain.RentalStatus;
import com.sherlock.dto.AdminGameDetailResponse;
import com.sherlock.dto.AdminGameResponse;
import com.sherlock.dto.GameDetailResponse;
import com.sherlock.dto.GameNoteResponse;
import com.sherlock.dto.GameSummaryResponse;
import com.sherlock.dto.PageResponse;
import com.sherlock.repository.GameNoteRepository;
import com.sherlock.repository.GameRepository;
import com.sherlock.repository.RentalRepository;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class GameQueryService {

	static final int MAX_PAGE_SIZE = 100;
	private static final char LIKE_ESCAPE = '!';

	private final GameRepository gameRepository;
	private final RentalRepository rentalRepository;
	private final GameNoteRepository gameNoteRepository;

	public GameQueryService(GameRepository gameRepository, RentalRepository rentalRepository,
			GameNoteRepository gameNoteRepository) {
		this.gameRepository = gameRepository;
		this.rentalRepository = rentalRepository;
		this.gameNoteRepository = gameNoteRepository;
	}

	/** 회원용 목록/검색. 삭제된 게임은 제외한다. */
	public PageResponse<GameSummaryResponse> search(String keyword, GameCategory category,
			boolean availableOnly, int page, int size) {
		return searchGames(keyword, category, availableOnly, false, page, size, GameSummaryResponse::of);
	}

	/** 관리자용 목록/검색. includeDeleted가 true면 삭제된 게임도 포함한다. */
	public PageResponse<AdminGameResponse> searchForAdmin(String keyword, GameCategory category,
			boolean includeDeleted, int page, int size) {
		return searchGames(keyword, category, false, includeDeleted, page, size, AdminGameResponse::of);
	}

	@FunctionalInterface
	private interface GameMapper<T> {
		T map(Game game, long activeRentalCount, GameNote latestNote);
	}

	private <T> PageResponse<T> searchGames(String keyword, GameCategory category, boolean availableOnly,
			boolean includeDeleted, int page, int size, GameMapper<T> mapper) {
		// 1,000개 이상이므로 항상 페이지 단위로만 조회하고, 크기는 상한을 둔다.
		PageRequest pageable = PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), MAX_PAGE_SIZE),
				Sort.by("name").and(Sort.by("id")));
		Page<Game> games = gameRepository.findAll(spec(keyword, category, availableOnly, includeDeleted),
				pageable);

		// 목록의 게임들에 대해 대여 수/최근 특이사항을 한 번씩만 조회한다(N+1 방지).
		List<Long> ids = games.getContent().stream().map(Game::getId).toList();
		Map<Long, Long> activeCounts = activeRentalCounts(ids);
		Map<Long, GameNote> latestNotes = ids.isEmpty() ? Map.of()
				: gameNoteRepository.findLatestByGameIds(ids).stream()
						.collect(Collectors.toMap(n -> n.getGame().getId(), Function.identity()));

		return PageResponse.from(games.map(g -> mapper.map(g,
				activeCounts.getOrDefault(g.getId(), 0L), latestNotes.get(g.getId()))));
	}

	/** 회원용 상세. 삭제된 게임은 404. */
	public GameDetailResponse detail(Long id) {
		Game game = gameRepository.findById(id).filter(g -> !g.isDeleted())
				.orElseThrow(() -> new GameNotFoundException(id));
		return toDetail(game);
	}

	/** 관리자용 상세. 삭제된 게임도 조회한다. */
	public AdminGameDetailResponse adminDetail(Long id) {
		Game game = gameRepository.findById(id).orElseThrow(() -> new GameNotFoundException(id));
		return new AdminGameDetailResponse(toDetail(game), game.isDeleted());
	}

	private GameDetailResponse toDetail(Game game) {
		long active = rentalRepository.countByGameAndStatus(game, RentalStatus.RENTED);
		List<GameNoteResponse> notes = gameNoteRepository.findHistoryByGameId(game.getId()).stream()
				.map(GameNoteResponse::from).toList();
		return GameDetailResponse.of(game, active, notes);
	}

	private Map<Long, Long> activeRentalCounts(Collection<Long> ids) {
		Map<Long, Long> counts = new HashMap<>();
		if (ids.isEmpty()) {
			return counts;
		}
		for (Object[] row : rentalRepository.countByGameIdsAndStatus(ids, RentalStatus.RENTED)) {
			counts.put(((Number) row[0]).longValue(), ((Number) row[1]).longValue());
		}
		return counts;
	}

	private static Specification<Game> spec(String keyword, GameCategory category, boolean availableOnly,
			boolean includeDeleted) {
		return (root, query, cb) -> {
			List<Predicate> predicates = new ArrayList<>();
			if (!includeDeleted) {
				predicates.add(cb.isFalse(root.get("deleted")));
			}
			if (category != null) {
				predicates.add(cb.equal(root.get("category"), category));
			}
			if (keyword != null && !keyword.isBlank()) {
				String escaped = keyword.trim().toLowerCase()
						.replace("!", "!!").replace("%", "!%").replace("_", "!_");
				predicates.add(cb.like(cb.lower(root.get("name")), "%" + escaped + "%", LIKE_ESCAPE));
			}
			if (availableOnly) {
				// 전체 수량 > 미반납 대여 수
				Subquery<Long> active = query.subquery(Long.class);
				Root<Rental> rental = active.from(Rental.class);
				active.select(cb.count(rental)).where(
						cb.equal(rental.get("game"), root),
						cb.equal(rental.get("status"), RentalStatus.RENTED));
				predicates.add(cb.greaterThan(cb.toLong(root.get("totalQuantity")), active));
			}
			return cb.and(predicates.toArray(Predicate[]::new));
		};
	}
}
