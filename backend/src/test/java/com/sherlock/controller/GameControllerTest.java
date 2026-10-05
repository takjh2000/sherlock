package com.sherlock.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.sherlock.domain.Game;
import com.sherlock.domain.GameCategory;
import com.sherlock.domain.GameNote;
import com.sherlock.domain.Member;
import com.sherlock.domain.Rental;
import com.sherlock.domain.Role;
import com.sherlock.repository.GameNoteRepository;
import com.sherlock.repository.GameRepository;
import com.sherlock.repository.MemberRepository;
import com.sherlock.repository.RentalRepository;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class GameControllerTest {

	@Autowired
	MockMvc mockMvc;
	@Autowired
	GameRepository gameRepository;
	@Autowired
	RentalRepository rentalRepository;
	@Autowired
	GameNoteRepository gameNoteRepository;
	@Autowired
	MemberRepository memberRepository;

	Game catan;
	Game splendor;
	Game crime;
	Member member;

	@BeforeEach
	void setUp() {
		member = memberRepository.save(new Member("20240001", "홍길동", Role.MEMBER));
		catan = gameRepository.save(new Game("카탄", GameCategory.BOARD_GAME, "김철수", 2));
		splendor = gameRepository.save(new Game("스플렌더", GameCategory.BOARD_GAME, null, 1));
		crime = gameRepository.save(new Game("카탄의 밤 사건", GameCategory.CRIME_SCENE, "이영희", 1));
		Game deleted = new Game("삭제된 게임", GameCategory.BOARD_GAME, null, 1);
		deleted.delete();
		gameRepository.save(deleted);

		// 카탄 2개 중 1개 대여 중, 스플렌더 1개 전부 대여 중, 반납된 대여는 세지 않는다.
		rentalRepository.save(new Rental(catan, member, LocalDate.now()));
		rentalRepository.save(new Rental(splendor, member, LocalDate.now()));
		Rental returned = new Rental(crime, member, LocalDate.now().minusDays(3));
		returned.markReturned(LocalDate.now());
		rentalRepository.save(returned);

		gameNoteRepository.save(new GameNote(catan, "주사위 1개 분실", member));
		gameNoteRepository.save(new GameNote(catan, "주사위 교체함", member));
	}

	@Test
	void 로그인하지_않으면_401() throws Exception {
		mockMvc.perform(get("/api/games")).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/games/" + catan.getId())).andExpect(status().isUnauthorized());
	}

	@Test
	@WithMockUser
	void 목록은_삭제된_게임을_제외하고_이름순으로_반환한다() throws Exception {
		mockMvc.perform(get("/api/games"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(3))
				.andExpect(jsonPath("$.content[0].name").value("스플렌더"))
				.andExpect(jsonPath("$.content[1].name").value("카탄"))
				.andExpect(jsonPath("$.content[2].name").value("카탄의 밤 사건"));
	}

	@Test
	@WithMockUser
	void 대여_가능_수량은_전체_수량에서_미반납_대여를_뺀_값이다() throws Exception {
		mockMvc.perform(get("/api/games").param("keyword", "카탄"))
				.andExpect(jsonPath("$.content[0].name").value("카탄"))
				.andExpect(jsonPath("$.content[0].totalQuantity").value(2))
				.andExpect(jsonPath("$.content[0].availableQuantity").value(1))
				.andExpect(jsonPath("$.content[0].owner").value("김철수"))
				// 반납 완료된 대여는 가능 수량에 영향이 없다.
				.andExpect(jsonPath("$.content[1].availableQuantity").value(1));
	}

	@Test
	@WithMockUser
	void 이름으로_검색한다() throws Exception {
		mockMvc.perform(get("/api/games").param("keyword", "카탄"))
				.andExpect(jsonPath("$.totalElements").value(2));
		mockMvc.perform(get("/api/games").param("keyword", "없는게임"))
				.andExpect(jsonPath("$.totalElements").value(0));
	}

	@Test
	@WithMockUser
	void 검색어의_와일드카드_문자는_문자_그대로_취급한다() throws Exception {
		mockMvc.perform(get("/api/games").param("keyword", "%"))
				.andExpect(jsonPath("$.totalElements").value(0));
	}

	@Test
	@WithMockUser
	void 분류로_필터링한다() throws Exception {
		mockMvc.perform(get("/api/games").param("category", "CRIME_SCENE"))
				.andExpect(jsonPath("$.totalElements").value(1))
				.andExpect(jsonPath("$.content[0].name").value("카탄의 밤 사건"));
	}

	@Test
	@WithMockUser
	void 대여_가능만_보기는_모두_대여_중인_게임을_제외한다() throws Exception {
		mockMvc.perform(get("/api/games").param("availableOnly", "true"))
				.andExpect(jsonPath("$.totalElements").value(2))
				.andExpect(jsonPath("$.content[0].name").value("카탄"))
				.andExpect(jsonPath("$.content[1].name").value("카탄의 밤 사건"));
	}

	@Test
	@WithMockUser
	void 페이지네이션이_동작한다() throws Exception {
		mockMvc.perform(get("/api/games").param("size", "2").param("page", "1"))
				.andExpect(jsonPath("$.totalElements").value(3))
				.andExpect(jsonPath("$.totalPages").value(2))
				.andExpect(jsonPath("$.content.length()").value(1));
	}

	@Test
	@WithMockUser
	void 목록에_최근_특이사항이_표시된다() throws Exception {
		mockMvc.perform(get("/api/games").param("keyword", "카탄").param("category", "BOARD_GAME"))
				.andExpect(jsonPath("$.content[0].latestNote.content").value("주사위 교체함"))
				.andExpect(jsonPath("$.content[0].latestNote.authorName").value("홍길동"));
		mockMvc.perform(get("/api/games").param("category", "CRIME_SCENE"))
				.andExpect(jsonPath("$.content[0].latestNote").doesNotExist());
	}

	@Test
	@WithMockUser
	void 상세는_특이사항_이력_전체를_최신순으로_반환한다() throws Exception {
		mockMvc.perform(get("/api/games/" + catan.getId()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("카탄"))
				.andExpect(jsonPath("$.availableQuantity").value(1))
				.andExpect(jsonPath("$.notes.length()").value(2))
				.andExpect(jsonPath("$.notes[0].content").value("주사위 교체함"))
				.andExpect(jsonPath("$.notes[1].content").value("주사위 1개 분실"));
	}

	@Test
	@WithMockUser
	void 없는_게임의_상세는_404() throws Exception {
		mockMvc.perform(get("/api/games/999999"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("GAME_NOT_FOUND"));
	}

	@Test
	@WithMockUser
	void 잘못된_분류_값은_400() throws Exception {
		mockMvc.perform(get("/api/games").param("category", "UNKNOWN"))
				.andExpect(status().isBadRequest());
	}
}
