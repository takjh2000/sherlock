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
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.sherlock.domain.Game;
import com.sherlock.domain.GameCategory;
import com.sherlock.domain.Member;
import com.sherlock.domain.Rental;
import com.sherlock.domain.RentalStatus;
import com.sherlock.domain.Role;
import com.sherlock.repository.GameRepository;
import com.sherlock.repository.MemberRepository;
import com.sherlock.repository.RentalRepository;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@WithMockUser(username = "20200001", roles = "ADMIN")
class AdminRentalControllerTest {

	@Autowired
	MockMvc mockMvc;
	@Autowired
	GameRepository gameRepository;
	@Autowired
	RentalRepository rentalRepository;
	@Autowired
	MemberRepository memberRepository;

	Member hong;
	Member kim;
	Game catan;
	Game splendor;

	LocalDate today = LocalDate.now();

	@BeforeEach
	void setUp() {
		memberRepository.save(new Member("20200001", "임원", Role.ADMIN));
		hong = memberRepository.save(new Member("20240001", "홍길동", Role.MEMBER));
		kim = memberRepository.save(new Member("20240002", "김철수", Role.MEMBER));
		catan = gameRepository.save(new Game("카탄", GameCategory.BOARD_GAME, null, 5));
		splendor = gameRepository.save(new Game("스플렌더", GameCategory.BOARD_GAME, null, 5));
	}

	@Test
	void 전체_대여_중_목록은_반납된_건을_제외한다() throws Exception {
		rentalRepository.save(new Rental(catan, hong, today));
		Rental returned = rentalRepository.save(new Rental(splendor, kim, today.minusDays(2)));
		returned.markReturned(today);

		mockMvc.perform(get("/api/admin/rentals/active"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].gameName").value("카탄"))
				.andExpect(jsonPath("$[0].studentId").value("20240001"))
				.andExpect(jsonPath("$[0].memberName").value("홍길동"))
				.andExpect(jsonPath("$[0].overdueDays").value(0));
	}

	@Test
	void 연체_목록은_연체_일수가_긴_순이고_기한_내_대여는_제외한다() throws Exception {
		// 대여 8일 전 -> 반납 예정일 1일 전 -> 연체 1일
		rentalRepository.save(new Rental(catan, hong, today.minusDays(8)));
		// 대여 17일 전 -> 연체 10일
		rentalRepository.save(new Rental(splendor, kim, today.minusDays(17)));
		// 대여 7일 전 -> 오늘이 반납 예정일이므로 연체 아님
		rentalRepository.save(new Rental(catan, kim, today.minusDays(7)));

		mockMvc.perform(get("/api/admin/rentals/overdue"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[0].gameName").value("스플렌더"))
				.andExpect(jsonPath("$[0].overdueDays").value(10))
				.andExpect(jsonPath("$[1].gameName").value("카탄"))
				.andExpect(jsonPath("$[1].overdueDays").value(1));
	}

	@Test
	void 회원별_연체_누적은_반납_완료된_연체도_합산한다() throws Exception {
		// 홍길동: 지금 연체 중 3일 + 이미 반납했지만 2일 늦은 건 -> 2건, 5일, 현재 연체 1건
		rentalRepository.save(new Rental(catan, hong, today.minusDays(10)));
		Rental lateReturned = rentalRepository.save(new Rental(splendor, hong, today.minusDays(20)));
		lateReturned.markReturned(today.minusDays(11)); // 예정일 today-13 -> 2일 연체
		// 정시 반납은 집계되지 않는다
		Rental onTime = rentalRepository.save(new Rental(catan, hong, today.minusDays(30)));
		onTime.markReturned(today.minusDays(25));
		// 김철수: 1일 연체
		rentalRepository.save(new Rental(catan, kim, today.minusDays(8)));

		mockMvc.perform(get("/api/admin/rentals/overdue-stats"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[0].studentId").value("20240001"))
				.andExpect(jsonPath("$[0].overdueCount").value(2))
				.andExpect(jsonPath("$[0].overdueDays").value(5))
				.andExpect(jsonPath("$[0].currentlyOverdue").value(1))
				.andExpect(jsonPath("$[1].studentId").value("20240002"))
				.andExpect(jsonPath("$[1].overdueCount").value(1))
				.andExpect(jsonPath("$[1].overdueDays").value(1));
	}

	@Test
	void 강제_반납하면_오늘_날짜로_반납_처리된다() throws Exception {
		Rental rental = rentalRepository.save(new Rental(catan, hong, today.minusDays(9)));

		mockMvc.perform(post("/api/admin/rentals/" + rental.getId() + "/force-return"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("RETURNED"))
				.andExpect(jsonPath("$.returnedDate").value(today.toString()))
				.andExpect(jsonPath("$.overdueDays").value(2));
		assertEquals(RentalStatus.RETURNED, rentalRepository.findById(rental.getId()).orElseThrow().getStatus());
	}

	@Test
	void 이미_반납된_건이나_없는_건은_강제_반납할_수_없다() throws Exception {
		Rental rental = rentalRepository.save(new Rental(catan, hong, today));
		rental.markReturned(today);

		mockMvc.perform(post("/api/admin/rentals/" + rental.getId() + "/force-return"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("ALREADY_RETURNED"));
		mockMvc.perform(post("/api/admin/rentals/999999/force-return")).andExpect(status().isNotFound());
	}
}
