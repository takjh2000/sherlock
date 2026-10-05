package com.sherlock.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.sherlock.domain.Game;
import com.sherlock.domain.GameCategory;
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
class RentalControllerTest {

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

	Member me;
	Member other;
	Game game;

	@BeforeEach
	void setUp() {
		me = memberRepository.save(new Member("20240001", "홍길동", Role.MEMBER));
		other = memberRepository.save(new Member("20240002", "김철수", Role.MEMBER));
		game = gameRepository.save(new Game("카탄", GameCategory.BOARD_GAME, null, 1));
	}

	@Test
	void 로그인하지_않으면_401() throws Exception {
		mockMvc.perform(get("/api/rentals/me")).andExpect(status().isUnauthorized());
	}

	@Test
	@WithMockUser(username = "20240001")
	void 대여하면_반납_예정일은_대여일_7일_뒤다() throws Exception {
		mockMvc.perform(rentRequest(game.getId()))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("RENTED"))
				.andExpect(jsonPath("$.rentedDate").value(LocalDate.now().toString()))
				.andExpect(jsonPath("$.dueDate").value(LocalDate.now().plusDays(7).toString()))
				.andExpect(jsonPath("$.daysLeft").value(7));
	}

	@Test
	@WithMockUser(username = "20240001")
	void 재고가_없으면_409() throws Exception {
		rentalRepository.save(new Rental(game, other, LocalDate.now()));

		mockMvc.perform(rentRequest(game.getId()))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("OUT_OF_STOCK"));
	}

	@Test
	@WithMockUser(username = "20240001")
	void 없는_게임을_대여하면_404() throws Exception {
		mockMvc.perform(rentRequest(999999L)).andExpect(status().isNotFound());
	}

	@Test
	@WithMockUser(username = "20240001")
	void gameId가_없으면_400() throws Exception {
		mockMvc.perform(post("/api/rentals").contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	@WithMockUser(username = "20240001")
	void 내_대여_목록은_D_day와_연체_일수를_보여준다() throws Exception {
		Game late = gameRepository.save(new Game("스플렌더", GameCategory.BOARD_GAME, null, 1));
		rentalRepository.save(new Rental(game, me, LocalDate.now().minusDays(2)));
		rentalRepository.save(new Rental(late, me, LocalDate.now().minusDays(10)));
		rentalRepository.save(new Rental(late, other, LocalDate.now()));

		// 최신순: 스플렌더(연체 3일) → 카탄(D-5). 타인의 대여는 보이지 않는다.
		mockMvc.perform(get("/api/rentals/me"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[0].gameName").value("스플렌더"))
				.andExpect(jsonPath("$[0].overdueDays").value(3))
				.andExpect(jsonPath("$[0].daysLeft").value(0))
				.andExpect(jsonPath("$[1].gameName").value("카탄"))
				.andExpect(jsonPath("$[1].daysLeft").value(5))
				.andExpect(jsonPath("$[1].overdueDays").value(0));
	}

	@Test
	@WithMockUser(username = "20240001")
	void 본인_대여를_반납한다() throws Exception {
		Rental rental = rentalRepository.save(new Rental(game, me, LocalDate.now()));

		mockMvc.perform(post("/api/rentals/" + rental.getId() + "/return"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("RETURNED"))
				.andExpect(jsonPath("$.returnedDate").value(LocalDate.now().toString()));
		assertEquals(0, gameNoteRepository.count());
	}

	@Test
	@WithMockUser(username = "20240001")
	void 타인의_대여는_반납할_수_없다() throws Exception {
		Rental rental = rentalRepository.save(new Rental(game, other, LocalDate.now()));

		mockMvc.perform(post("/api/rentals/" + rental.getId() + "/return"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("NOT_RENTAL_OWNER"));
	}

	@Test
	@WithMockUser(username = "20240001")
	void 이미_반납한_대여는_다시_반납할_수_없다() throws Exception {
		Rental rental = rentalRepository.save(new Rental(game, me, LocalDate.now()));
		mockMvc.perform(post("/api/rentals/" + rental.getId() + "/return")).andExpect(status().isOk());

		mockMvc.perform(post("/api/rentals/" + rental.getId() + "/return"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("ALREADY_RETURNED"));
	}

	@Test
	@WithMockUser(username = "20240001")
	void 없는_대여를_반납하면_404() throws Exception {
		mockMvc.perform(post("/api/rentals/999999/return")).andExpect(status().isNotFound());
	}

	@Test
	@WithMockUser(username = "20240001")
	void 반납_시_신고하면_게임_특이사항에_추가된다() throws Exception {
		Rental rental = rentalRepository.save(new Rental(game, me, LocalDate.now()));

		mockMvc.perform(returnRequest(rental.getId(), "  카드 2장 분실  ")).andExpect(status().isOk());

		var notes = gameNoteRepository.findByGameOrderByCreatedAtDesc(game);
		assertEquals(1, notes.size());
		assertEquals("카드 2장 분실", notes.get(0).getContent());
		assertEquals(me.getId(), notes.get(0).getAuthor().getId());
	}

	@Test
	@WithMockUser(username = "20240001")
	void 공백뿐인_신고는_특이사항으로_남기지_않는다() throws Exception {
		Rental rental = rentalRepository.save(new Rental(game, me, LocalDate.now()));

		mockMvc.perform(returnRequest(rental.getId(), "   ")).andExpect(status().isOk());

		assertEquals(0, gameNoteRepository.count());
	}

	@Test
	@WithMockUser(username = "20240001")
	void 신고_내용은_1000자까지_허용한다() throws Exception {
		Rental rental = rentalRepository.save(new Rental(game, me, LocalDate.now()));

		mockMvc.perform(returnRequest(rental.getId(), "가".repeat(1000))).andExpect(status().isOk());

		assertEquals(1, gameNoteRepository.count());
	}

	@Test
	@WithMockUser(username = "20240001")
	void 신고_내용이_1000자를_넘으면_400이고_반납되지_않는다() throws Exception {
		Rental rental = rentalRepository.save(new Rental(game, me, LocalDate.now()));

		mockMvc.perform(returnRequest(rental.getId(), "가".repeat(1001)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

		assertEquals(0, gameNoteRepository.count());
		mockMvc.perform(get("/api/rentals/me")).andExpect(jsonPath("$[0].status").value("RENTED"));
	}

	private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder rentRequest(
			Long gameId) {
		return post("/api/rentals").contentType(MediaType.APPLICATION_JSON)
				.content("{\"gameId\":" + gameId + "}");
	}

	private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder returnRequest(
			Long rentalId, String report) {
		return post("/api/rentals/" + rentalId + "/return").contentType(MediaType.APPLICATION_JSON)
				.content("{\"report\":\"" + report + "\"}");
	}
}
