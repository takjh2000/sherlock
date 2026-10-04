package com.sherlock.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sherlock.domain.Game;
import com.sherlock.domain.GameCategory;
import com.sherlock.domain.GameNote;
import com.sherlock.domain.Member;
import com.sherlock.domain.Rental;
import com.sherlock.domain.RentalStatus;
import com.sherlock.domain.Role;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

@DataJpaTest
class RentalRepositoryTest {

	@Autowired
	GameRepository gameRepository;
	@Autowired
	MemberRepository memberRepository;
	@Autowired
	RentalRepository rentalRepository;
	@Autowired
	GameNoteRepository gameNoteRepository;

	@Test
	void 미반납_대여_수로_대여_가능_수량을_계산한다() {
		Game game = gameRepository.save(new Game("카탄", GameCategory.BOARD_GAME, null, 3));
		Member a = memberRepository.save(new Member("20240001", "가", Role.MEMBER));
		Member b = memberRepository.save(new Member("20240002", "나", Role.MEMBER));
		LocalDate today = LocalDate.of(2026, 3, 1);
		Rental returned = rentalRepository.save(new Rental(game, a, today));
		returned.markReturned(today.plusDays(1));
		rentalRepository.save(new Rental(game, a, today));
		rentalRepository.save(new Rental(game, b, today));
		rentalRepository.flush();

		long active = rentalRepository.countByGameAndStatus(game, RentalStatus.RENTED);

		assertThat(active).isEqualTo(2);
		assertThat(game.availableQuantity(active)).isEqualTo(1);
	}

	@Test
	void 삭제된_게임은_목록에서_제외된다() {
		gameRepository.save(new Game("카탄", GameCategory.BOARD_GAME, null, 1));
		Game removed = gameRepository.save(new Game("옛날게임", GameCategory.CRIME_SCENE, null, 1));
		removed.delete();
		gameRepository.flush();

		assertThat(gameRepository.findByDeletedFalse()).extracting(Game::getName).containsExactly("카탄");
	}

	@Test
	void 학번은_중복될_수_없다() {
		memberRepository.save(new Member("20240001", "가", Role.MEMBER));
		assertThatThrownBy(() -> memberRepository.saveAndFlush(new Member("20240001", "나", Role.MEMBER)))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void 첫_로그인_전_회원은_비밀번호_해시가_null이다() {
		Member saved = memberRepository.save(new Member("20240001", "가", Role.ADMIN));
		Member found = memberRepository.findByStudentId("20240001").orElseThrow();

		assertThat(found.getId()).isEqualTo(saved.getId());
		assertThat(found.getPasswordHash()).isNull();
		assertThat(found.isActive()).isTrue();
	}

	@Test
	void 특이사항을_게임별로_조회한다() {
		Game game = gameRepository.save(new Game("카탄", GameCategory.BOARD_GAME, null, 1));
		Member admin = memberRepository.save(new Member("20240001", "가", Role.ADMIN));
		gameNoteRepository.save(new GameNote(game, "주사위 1개 분실", admin));
		gameNoteRepository.flush();

		assertThat(gameNoteRepository.findByGameOrderByCreatedAtDesc(game))
				.extracting(GameNote::getContent).containsExactly("주사위 1개 분실");
	}
}
