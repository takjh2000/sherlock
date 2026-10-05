package com.sherlock.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
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
class AdminGameControllerTest {

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

	Member admin;
	Member member;
	Game game;

	@BeforeEach
	void setUp() {
		admin = memberRepository.save(new Member("20200001", "임원", Role.ADMIN));
		member = memberRepository.save(new Member("20240001", "홍길동", Role.MEMBER));
		game = gameRepository.save(new Game("카탄", GameCategory.BOARD_GAME, "홍길동", 2));
	}

	private MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder builder, String body) {
		return builder.contentType(MediaType.APPLICATION_JSON).content(body);
	}

	private static final String GAME_BODY = "{\"name\":\"스플렌더\",\"category\":\"BOARD_GAME\","
			+ "\"owner\":\"동아리\",\"totalQuantity\":3}";

	// ---- 권한 ----

	@Test
	void 로그인하지_않으면_관리자_API는_401() throws Exception {
		mockMvc.perform(get("/api/admin/games")).andExpect(status().isUnauthorized());
	}

	@Test
	@WithMockUser(username = "20240001", roles = "MEMBER")
	void MEMBER는_모든_관리자_API에서_403() throws Exception {
		mockMvc.perform(get("/api/admin/games")).andExpect(status().isForbidden());
		mockMvc.perform(json(post("/api/admin/games"), GAME_BODY)).andExpect(status().isForbidden());
		mockMvc.perform(delete("/api/admin/games/" + game.getId())).andExpect(status().isForbidden());
		mockMvc.perform(get("/api/admin/members")).andExpect(status().isForbidden());
		mockMvc.perform(get("/api/admin/rentals/active")).andExpect(status().isForbidden());
		mockMvc.perform(post("/api/admin/rentals/1/force-return")).andExpect(status().isForbidden());
	}

	// ---- 게임 등록/수정 ----

	@Test
	@WithMockUser(username = "20200001", roles = "ADMIN")
	void 관리자는_게임을_등록한다() throws Exception {
		mockMvc.perform(json(post("/api/admin/games"), GAME_BODY))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.name").value("스플렌더"))
				.andExpect(jsonPath("$.totalQuantity").value(3))
				.andExpect(jsonPath("$.availableQuantity").value(3))
				.andExpect(jsonPath("$.deleted").value(false));
	}

	@Test
	@WithMockUser(username = "20200001", roles = "ADMIN")
	void 수량이_1_미만이거나_이름이_비면_400() throws Exception {
		mockMvc.perform(json(post("/api/admin/games"),
				"{\"name\":\"x\",\"category\":\"BOARD_GAME\",\"totalQuantity\":0}"))
				.andExpect(status().isBadRequest());
		mockMvc.perform(json(post("/api/admin/games"),
				"{\"name\":\" \",\"category\":\"BOARD_GAME\",\"totalQuantity\":1}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	@WithMockUser(username = "20200001", roles = "ADMIN")
	void 관리자는_게임을_수정한다() throws Exception {
		mockMvc.perform(json(put("/api/admin/games/" + game.getId()), GAME_BODY))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("스플렌더"))
				.andExpect(jsonPath("$.totalQuantity").value(3));
	}

	@Test
	@WithMockUser(username = "20200001", roles = "ADMIN")
	void 대여_중인_수량보다_적게_줄일_수_없다() throws Exception {
		rentalRepository.save(new Rental(game, member, LocalDate.now()));
		rentalRepository.save(new Rental(game, admin, LocalDate.now()));

		mockMvc.perform(json(put("/api/admin/games/" + game.getId()),
				"{\"name\":\"카탄\",\"category\":\"BOARD_GAME\",\"totalQuantity\":1}"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("QUANTITY_BELOW_RENTED"));
	}

	// ---- 삭제(soft delete) ----

	@Test
	@WithMockUser(username = "20200001", roles = "ADMIN")
	void 대여_중인_게임은_삭제할_수_없다() throws Exception {
		rentalRepository.save(new Rental(game, member, LocalDate.now()));

		mockMvc.perform(delete("/api/admin/games/" + game.getId()))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("GAME_RENTED"));
		assertEquals(false, gameRepository.findById(game.getId()).orElseThrow().isDeleted());
	}

	@Test
	@WithMockUser(username = "20200001", roles = "ADMIN")
	void 반납된_게임은_삭제할_수_있고_행은_남는다() throws Exception {
		Rental rental = rentalRepository.save(new Rental(game, member, LocalDate.now().minusDays(3)));
		rental.markReturned(LocalDate.now());

		mockMvc.perform(delete("/api/admin/games/" + game.getId())).andExpect(status().isNoContent());

		assertEquals(true, gameRepository.findById(game.getId()).orElseThrow().isDeleted());
	}

	@Test
	@WithMockUser(username = "20200001", roles = "ADMIN")
	void 삭제된_게임은_수정_삭제_대상이_아니다() throws Exception {
		game.delete();
		gameRepository.flush();

		mockMvc.perform(delete("/api/admin/games/" + game.getId())).andExpect(status().isNotFound());
		mockMvc.perform(json(put("/api/admin/games/" + game.getId()), GAME_BODY))
				.andExpect(status().isNotFound());
	}

	@Test
	void 삭제된_게임은_회원용_목록_검색_상세에서_빠진다() throws Exception {
		Game other = gameRepository.save(new Game("스플렌더", GameCategory.BOARD_GAME, null, 1));
		game.delete();
		gameRepository.flush();

		mockMvc.perform(get("/api/games").with(user("20240001")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(1))
				.andExpect(jsonPath("$.content[0].id").value(other.getId()));
		mockMvc.perform(get("/api/games").param("keyword", "카탄").with(user("20240001")))
				.andExpect(jsonPath("$.totalElements").value(0));
		mockMvc.perform(get("/api/games/" + game.getId()).with(user("20240001")))
				.andExpect(status().isNotFound());
	}

	@Test
	@WithMockUser(username = "20200001", roles = "ADMIN")
	void 관리자_목록은_기본적으로_삭제된_게임을_숨기고_옵션으로_보여준다() throws Exception {
		gameRepository.save(new Game("스플렌더", GameCategory.BOARD_GAME, null, 1));
		game.delete();
		gameRepository.flush();

		mockMvc.perform(get("/api/admin/games"))
				.andExpect(jsonPath("$.totalElements").value(1))
				.andExpect(jsonPath("$.content[0].deleted").value(false));
		mockMvc.perform(get("/api/admin/games").param("includeDeleted", "true"))
				.andExpect(jsonPath("$.totalElements").value(2))
				.andExpect(jsonPath("$.content[0].name").value("스플렌더"))
				.andExpect(jsonPath("$.content[1].name").value("카탄"))
				.andExpect(jsonPath("$.content[1].deleted").value(true));
		mockMvc.perform(get("/api/admin/games/" + game.getId()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.deleted").value(true));
	}

	@Test
	@WithMockUser(username = "20240001", roles = "MEMBER")
	void 삭제된_게임은_대여할_수_없다() throws Exception {
		game.delete();
		gameRepository.flush();

		mockMvc.perform(json(post("/api/rentals"), "{\"gameId\":" + game.getId() + "}"))
				.andExpect(status().isNotFound());
	}

	// ---- 특이사항 ----

	@Test
	@WithMockUser(username = "20200001", roles = "ADMIN")
	void 특이사항을_작성_수정_삭제한다() throws Exception {
		String created = mockMvc.perform(json(post("/api/admin/games/" + game.getId() + "/notes"),
				"{\"content\":\"주사위 1개 분실\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.content").value("주사위 1개 분실"))
				.andExpect(jsonPath("$.authorName").value("임원"))
				.andReturn().getResponse().getContentAsString();
		Long noteId = Long.valueOf(created.replaceAll(".*\"id\":(\\d+).*", "$1"));

		mockMvc.perform(json(put("/api/admin/notes/" + noteId), "{\"content\":\"주사위 찾음\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content").value("주사위 찾음"));
		mockMvc.perform(get("/api/admin/games/" + game.getId()))
				.andExpect(jsonPath("$.notes[0].content").value("주사위 찾음"));

		mockMvc.perform(delete("/api/admin/notes/" + noteId)).andExpect(status().isNoContent());
		assertEquals(0, gameNoteRepository.findByGameOrderByCreatedAtDesc(game).size());
	}

	@Test
	@WithMockUser(username = "20200001", roles = "ADMIN")
	void 회원이_남긴_특이사항도_관리자가_수정할_수_있다() throws Exception {
		GameNote note = gameNoteRepository.save(new GameNote(game, "파손", member));

		mockMvc.perform(json(put("/api/admin/notes/" + note.getId()), "{\"content\":\"수리 완료\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.authorName").value("홍길동"))
				.andExpect(jsonPath("$.content").value("수리 완료"));
	}

	@Test
	@WithMockUser(username = "20200001", roles = "ADMIN")
	void 없는_특이사항은_404_빈_내용은_400() throws Exception {
		mockMvc.perform(delete("/api/admin/notes/999999")).andExpect(status().isNotFound());
		mockMvc.perform(json(post("/api/admin/games/" + game.getId() + "/notes"), "{\"content\":\" \"}"))
				.andExpect(status().isBadRequest());
	}
}
