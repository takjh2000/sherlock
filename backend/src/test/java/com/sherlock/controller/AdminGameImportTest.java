package com.sherlock.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sherlock.domain.Game;
import com.sherlock.domain.GameCategory;
import com.sherlock.domain.Member;
import com.sherlock.domain.Rental;
import com.sherlock.domain.Role;
import com.sherlock.repository.GameNoteRepository;
import com.sherlock.repository.GameRepository;
import com.sherlock.repository.MemberRepository;
import com.sherlock.repository.RentalRepository;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * 게임 엑셀 가져오기 테스트. 데이터는 모두 가짜이며 xlsx는 테스트 안에서 만든다(파일을 커밋하지 않는다).
 * 실제 파일에서 확인한 특이사항(빈 행, 실수 수량, 숫자 이름, 빈/복수/'???' 소유자, 무시할 시트)을 반영한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@WithMockUser(username = "20200001", roles = "ADMIN")
class AdminGameImportTest {

	static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
	static final String URL = "/api/admin/games/import";

	@Autowired
	MockMvc mockMvc;
	@Autowired
	GameRepository gameRepository;
	@Autowired
	GameNoteRepository gameNoteRepository;
	@Autowired
	MemberRepository memberRepository;
	@Autowired
	RentalRepository rentalRepository;

	@BeforeEach
	void setUp() {
		memberRepository.save(new Member("20200001", "임원", Role.ADMIN));
	}

	private static void header(XSSFSheet sheet) {
		Row row = sheet.createRow(0);
		String[] titles = { "게임 이름", "소유자", "전체 수량", "대여 중인 수량", "남은 재고", "비고" };
		for (int i = 0; i < titles.length; i++) {
			row.createCell(i).setCellValue(titles[i]);
		}
	}

	/** 실수 수량(1.0)과 숫자 이름(719)을 그대로 셀 타입으로 넣는다. */
	private static void put(XSSFSheet sheet, int rowIndex, Object name, String owner, double quantity, String note) {
		Row row = sheet.createRow(rowIndex);
		if (name instanceof Number n) {
			row.createCell(0).setCellValue(n.doubleValue());
		} else {
			row.createCell(0).setCellValue((String) name);
		}
		if (owner != null) {
			row.createCell(1).setCellValue(owner);
		}
		row.createCell(2).setCellValue(quantity);
		row.createCell(3).setCellValue(99); // 계산값: 무시되어야 한다.
		row.createCell(4).setCellValue(99);
		if (note != null) {
			row.createCell(5).setCellValue(note);
		}
	}

	private static MockMultipartFile workbook(java.util.function.Consumer<XSSFWorkbook> fill) throws IOException {
		try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			fill.accept(wb);
			wb.write(out);
			return new MockMultipartFile("file", "games.xlsx", XLSX, out.toByteArray());
		}
	}

	private static MockMultipartFile sample() throws IOException {
		return workbook(wb -> {
			XSSFSheet board = wb.createSheet("보드게임_현황");
			header(board);
			put(board, 1, "가짜카탄", "셜록, 신의한", 1.0, "부속품 1개 분실");
			put(board, 2, 719, "???", 2.0, null);
			put(board, 3, "가짜우노", null, 3.0, null);
			// 중간에 빈 행이 있어도(게임 이름 없음) 건너뛴다.
			board.createRow(4).createCell(2).setCellValue(5);
			board.createRow(5);
			XSSFSheet crime = wb.createSheet("크라임씬_현황");
			header(crime);
			put(crime, 1, "가짜살인사건", null, 1.0, null);
			// 무시해야 하는 시트
			XSSFSheet ignored = wb.createSheet("Validation_Data");
			ignored.createRow(0).createCell(0).setCellValue("보드게임");
			XSSFSheet rentals = wb.createSheet("대여기록");
			rentals.createRow(0).createCell(0).setCellValue("가짜기록");
			wb.createSheet("연체기록").createRow(0).createCell(0).setCellValue("가짜기록");
		});
	}

	@Test
	void 두_시트를_분류별로_가져오고_특이사항을_반영한다() throws Exception {
		mockMvc.perform(multipart(URL).file(sample()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.created").value(4))
				.andExpect(jsonPath("$.updated").value(0))
				.andExpect(jsonPath("$.failed.length()").value(0));

		List<Game> games = gameRepository.findByDeletedFalse();
		assertEquals(4, games.size());
		Game catan = find(games, GameCategory.BOARD_GAME, "가짜카탄");
		assertEquals(1, catan.getTotalQuantity());
		assertEquals("셜록, 신의한", catan.getOwner());
		assertEquals("부속품 1개 분실", gameNoteRepository.findByGameOrderByCreatedAtDesc(catan).get(0).getContent());
		Game numeric = find(games, GameCategory.BOARD_GAME, "719");
		assertEquals("???", numeric.getOwner());
		assertEquals(2, numeric.getTotalQuantity());
		assertNull(find(games, GameCategory.BOARD_GAME, "가짜우노").getOwner());
		Game crime = find(games, GameCategory.CRIME_SCENE, "가짜살인사건");
		assertNull(crime.getOwner());
		assertEquals(0, gameNoteRepository.findByGameOrderByCreatedAtDesc(numeric).size());
	}

	@Test
	void 재업로드하면_중복_생성_없이_갱신하고_특이사항도_중복_추가하지_않는다() throws Exception {
		mockMvc.perform(multipart(URL).file(sample())).andExpect(status().isOk());
		mockMvc.perform(multipart(URL).file(sample()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.created").value(0))
				.andExpect(jsonPath("$.updated").value(4));

		assertEquals(4, gameRepository.findByDeletedFalse().size());
		assertEquals(1, gameNoteRepository.count());
	}

	@Test
	void 같은_이름이라도_분류가_다르면_별개이고_기존_게임은_수량과_소유자를_갱신한다() throws Exception {
		Game existing = gameRepository.save(new Game("가짜카탄", GameCategory.CRIME_SCENE, "예전", 9));
		Game boardExisting = gameRepository.save(new Game("가짜카탄", GameCategory.BOARD_GAME, "예전", 9));

		mockMvc.perform(multipart(URL).file(sample()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.created").value(3))
				.andExpect(jsonPath("$.updated").value(1));

		Game reloaded = gameRepository.findById(boardExisting.getId()).orElseThrow();
		assertEquals(1, reloaded.getTotalQuantity());
		assertEquals("셜록, 신의한", reloaded.getOwner());
		assertEquals(9, gameRepository.findById(existing.getId()).orElseThrow().getTotalQuantity());
	}

	@Test
	void 잘못된_행은_행_번호와_사유로_알려주고_나머지는_반영한다() throws Exception {
		Member member = memberRepository.save(new Member("20240001", "홍길동", Role.MEMBER));
		Game rented = gameRepository.save(new Game("가짜대여중", GameCategory.BOARD_GAME, null, 2));
		rentalRepository.save(new Rental(rented, member, LocalDate.now()));
		rentalRepository.save(new Rental(rented, member, LocalDate.now()));

		MockMultipartFile file = workbook(wb -> {
			XSSFSheet board = wb.createSheet("보드게임_현황");
			header(board);
			put(board, 1, "가짜정상", null, 1.0, null); // 행 2
			put(board, 2, "가짜수량0", null, 0.0, null); // 행 3
			put(board, 3, "가짜소수", null, 1.5, null); // 행 4
			put(board, 4, "가짜정상", null, 1.0, null); // 행 5: 파일 내 중복
			put(board, 5, "가짜대여중", null, 1.0, null); // 행 6: 대여 중인 수량보다 적음
		});

		mockMvc.perform(multipart(URL).file(file))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.created").value(1))
				.andExpect(jsonPath("$.updated").value(0))
				.andExpect(jsonPath("$.failed.length()").value(4))
				.andExpect(jsonPath("$.failed[0].row").value(3))
				.andExpect(jsonPath("$.failed[0].sheet").value("보드게임_현황"))
				.andExpect(jsonPath("$.failed[1].row").value(4))
				.andExpect(jsonPath("$.failed[2].row").value(5))
				.andExpect(jsonPath("$.failed[3].row").value(6))
				.andExpect(jsonPath("$.failed[3].reason").value("대여 중인 수량(2개)보다 적게 줄일 수 없습니다."));
		assertEquals(2, rented.getTotalQuantity());
	}

	@Test
	void 대상_시트가_없으면_400() throws Exception {
		MockMultipartFile file = workbook(wb -> wb.createSheet("대여기록"));
		mockMvc.perform(multipart(URL).file(file))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("SHEET_NOT_FOUND"));
	}

	@Test
	void 필수_열이_없으면_400() throws Exception {
		MockMultipartFile file = workbook(wb -> wb.createSheet("보드게임_현황").createRow(0).createCell(0)
				.setCellValue("이름"));
		mockMvc.perform(multipart(URL).file(file))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_HEADER"));
	}

	@Test
	void xlsx가_아닌_파일은_400() throws Exception {
		MockMultipartFile file = new MockMultipartFile("file", "games.xlsx", XLSX, "not an excel".getBytes());
		mockMvc.perform(multipart(URL).file(file))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_FILE"));
	}

	@Test
	void 파일이_5MB를_넘으면_413() throws Exception {
		MockMultipartFile file = new MockMultipartFile("file", "games.xlsx", XLSX,
				new byte[5 * 1024 * 1024 + 1]);
		mockMvc.perform(multipart(URL).file(file))
				.andExpect(status().isPayloadTooLarge())
				.andExpect(jsonPath("$.code").value("FILE_TOO_LARGE"));
	}

	@Test
	@WithMockUser(username = "20240001", roles = "MEMBER")
	void MEMBER는_403() throws Exception {
		mockMvc.perform(multipart(URL).file(sample())).andExpect(status().isForbidden());
	}

	private static Game find(List<Game> games, GameCategory category, String name) {
		return games.stream().filter(g -> g.getCategory() == category && g.getName().equals(name)).findFirst()
				.orElseThrow();
	}
}
