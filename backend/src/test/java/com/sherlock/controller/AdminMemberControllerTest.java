package com.sherlock.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import com.sherlock.domain.Member;
import com.sherlock.domain.Role;
import com.sherlock.repository.MemberRepository;

/** 테스트에 쓰는 명단은 모두 가짜 데이터이며, xlsx는 테스트 안에서 만들어 쓴다(파일을 커밋하지 않는다). */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminMemberControllerTest {

	static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

	@Autowired
	MockMvc mockMvc;
	@Autowired
	MemberRepository memberRepository;
	@Autowired
	PasswordEncoder passwordEncoder;

	Member admin;
	Member member;

	@BeforeEach
	void setUp() {
		admin = new Member("20200001", "임원", Role.ADMIN);
		admin.changePasswordHash(passwordEncoder.encode("adminpass123"));
		memberRepository.save(admin);
		member = new Member("20240001", "홍길동", Role.MEMBER);
		member.changePasswordHash(passwordEncoder.encode("password123"));
		memberRepository.save(member);
	}

	private MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder builder, String body) {
		return builder.contentType(MediaType.APPLICATION_JSON).content(body);
	}

	private MockHttpSession login(String studentId, String password) throws Exception {
		return (MockHttpSession) mockMvc.perform(json(post("/api/auth/login"),
				"{\"studentId\":\"" + studentId + "\",\"password\":\"" + password + "\"}"))
				.andExpect(status().isOk())
				.andReturn().getRequest().getSession(false);
	}

	/** rows의 각 원소는 {학번, 이름}. 학번이 숫자 문자열이면 숫자 셀로 쓴다(엑셀에서 흔한 형태). */
	private static MockMultipartFile xlsx(boolean header, String[]... rows) throws IOException {
		try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			XSSFSheet sheet = workbook.createSheet("명단");
			int r = 0;
			if (header) {
				Row row = sheet.createRow(r++);
				row.createCell(0).setCellValue("학번");
				row.createCell(1).setCellValue("이름");
			}
			for (String[] data : rows) {
				Row row = sheet.createRow(r++);
				if (data[0].matches("\\d+")) {
					row.createCell(0).setCellValue(Double.parseDouble(data[0]));
				} else {
					row.createCell(0).setCellValue(data[0]);
				}
				row.createCell(1).setCellValue(data[1]);
			}
			workbook.write(out);
			return new MockMultipartFile("file", "members.xlsx", XLSX, out.toByteArray());
		}
	}

	// ---- 단건 등록 / 목록 ----

	@Test
	@WithMockUser(username = "20200001", roles = "ADMIN")
	void 학번과_이름으로_단건_등록하면_첫_로그인_상태가_된다() throws Exception {
		mockMvc.perform(json(post("/api/admin/members"), "{\"studentId\":\" 20250001 \",\"name\":\"김신입\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.studentId").value("20250001"))
				.andExpect(jsonPath("$.role").value("MEMBER"))
				.andExpect(jsonPath("$.active").value(true))
				.andExpect(jsonPath("$.passwordSet").value(false));
	}

	@Test
	@WithMockUser(username = "20200001", roles = "ADMIN")
	void 이미_있는_학번이면_409_값이_비면_400() throws Exception {
		mockMvc.perform(json(post("/api/admin/members"), "{\"studentId\":\"20240001\",\"name\":\"중복\"}"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("DUPLICATE_STUDENT_ID"));
		mockMvc.perform(json(post("/api/admin/members"), "{\"studentId\":\"\",\"name\":\"가\"}"))
				.andExpect(status().isBadRequest());
		mockMvc.perform(json(post("/api/admin/members"), "{\"studentId\":\"2025 01\",\"name\":\"가\"}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	@WithMockUser(username = "20200001", roles = "ADMIN")
	void 목록은_키워드로_학번_또는_이름을_검색한다() throws Exception {
		mockMvc.perform(get("/api/admin/members"))
				.andExpect(jsonPath("$.totalElements").value(2));
		mockMvc.perform(get("/api/admin/members").param("keyword", "홍길"))
				.andExpect(jsonPath("$.totalElements").value(1))
				.andExpect(jsonPath("$.content[0].studentId").value("20240001"))
				.andExpect(jsonPath("$.content[0].passwordSet").value(true));
	}

	// ---- 엑셀 일괄 등록 ----

	@Test
	@WithMockUser(username = "20200001", roles = "ADMIN")
	void 엑셀로_일괄_등록한다_머리글과_숫자_학번을_처리한다() throws Exception {
		MockMultipartFile file = xlsx(true,
				new String[] { "20250001", "가짜일" }, new String[] { "20250002", "가짜이" },
				new String[] { "B2025003", "가짜삼" });

		mockMvc.perform(multipart("/api/admin/members/import").file(file))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.created").value(3))
				.andExpect(jsonPath("$.skipped.length()").value(0));

		Member saved = memberRepository.findByStudentId("20250001").orElseThrow();
		assertEquals("가짜일", saved.getName());
		assertEquals(Role.MEMBER, saved.getRole());
		assertNull(saved.getPasswordHash());
		assertTrue(memberRepository.findByStudentId("B2025003").isPresent());
	}

	@Test
	@WithMockUser(username = "20200001", roles = "ADMIN")
	void 엑셀에서_기존_회원_파일_내_중복_빈_값은_건너뛰고_사유를_알려준다() throws Exception {
		MockMultipartFile file = xlsx(false,
				new String[] { "20240001", "홍길동" }, // 이미 등록됨 (행 1)
				new String[] { "20250001", "가짜일" }, // 정상 (행 2)
				new String[] { "20250001", "가짜일복사" }, // 파일 내 중복 (행 3)
				new String[] { "20250002", "" }); // 이름 없음 (행 4)

		mockMvc.perform(multipart("/api/admin/members/import").file(file))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.created").value(1))
				.andExpect(jsonPath("$.skipped.length()").value(3))
				.andExpect(jsonPath("$.skipped[0].row").value(1))
				.andExpect(jsonPath("$.skipped[0].reason").value("이미 등록된 학번입니다."))
				.andExpect(jsonPath("$.skipped[1].row").value(3))
				.andExpect(jsonPath("$.skipped[2].row").value(4));

		// 기존 회원은 덮어쓰지 않는다.
		assertEquals("홍길동", memberRepository.findByStudentId("20240001").orElseThrow().getName());
		assertEquals(List.of("가짜일"),
				memberRepository.findByStudentIdIn(List.of("20250001")).stream().map(Member::getName).toList());
	}

	@Test
	@WithMockUser(username = "20200001", roles = "ADMIN")
	void xlsx가_아닌_파일은_400() throws Exception {
		MockMultipartFile file = new MockMultipartFile("file", "members.xlsx", XLSX, "not an excel".getBytes());

		mockMvc.perform(multipart("/api/admin/members/import").file(file))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_FILE"));
	}

	@Test
	@WithMockUser(username = "20200001", roles = "ADMIN")
	void 파일_파트가_없으면_400() throws Exception {
		mockMvc.perform(multipart("/api/admin/members/import")).andExpect(status().isBadRequest());
	}

	@Test
	@WithMockUser(username = "20240001", roles = "MEMBER")
	void MEMBER는_엑셀_등록도_403() throws Exception {
		mockMvc.perform(multipart("/api/admin/members/import").file(xlsx(false, new String[] { "1", "가" })))
				.andExpect(status().isForbidden());
	}

	// ---- 비활성화 / 비밀번호 초기화 + 세션 무효화 ----

	@Test
	void 비밀번호를_초기화하면_첫_로그인_상태가_되고_기존_세션이_무효화된다() throws Exception {
		MockHttpSession session = login("20240001", "password123");
		mockMvc.perform(get("/api/games").session(session)).andExpect(status().isOk());

		mockMvc.perform(post("/api/admin/members/" + member.getId() + "/reset-password")
				.with(user("20200001").roles("ADMIN")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.passwordSet").value(false));
		assertNull(member.getPasswordHash());

		// 초기화 전에 발급된 세션은 더 이상 쓸 수 없다.
		mockMvc.perform(get("/api/auth/me").session(session)).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/games").session(session)).andExpect(status().isUnauthorized());

		// 다시 첫 로그인 흐름: 로그인은 설정 안내, 비밀번호 설정 후 로그인 가능
		mockMvc.perform(json(post("/api/auth/login"), "{\"studentId\":\"20240001\",\"password\":\"password123\"}"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("PASSWORD_NOT_SET"));
		mockMvc.perform(json(post("/api/auth/setup-password"),
				"{\"studentId\":\"20240001\",\"name\":\"홍길동\",\"password\":\"newpassword123\"}"))
				.andExpect(status().isNoContent());
		MockHttpSession fresh = login("20240001", "newpassword123");
		mockMvc.perform(get("/api/auth/me").session(fresh)).andExpect(status().isOk());
		// 새 비밀번호로 로그인해도 옛 세션은 되살아나지 않는다.
		mockMvc.perform(get("/api/auth/me").session(session)).andExpect(status().isUnauthorized());
	}

	@Test
	void 비활성화하면_로그인이_막히고_기존_세션도_끊긴다() throws Exception {
		MockHttpSession session = login("20240001", "password123");

		mockMvc.perform(post("/api/admin/members/" + member.getId() + "/deactivate")
				.with(user("20200001").roles("ADMIN")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.active").value(false));

		mockMvc.perform(get("/api/games").session(session)).andExpect(status().isUnauthorized());
		mockMvc.perform(json(post("/api/auth/login"), "{\"studentId\":\"20240001\",\"password\":\"password123\"}"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("BAD_CREDENTIALS"));
	}

	@Test
	void 초기화하지_않은_다른_회원과_관리자_세션은_유지된다() throws Exception {
		Member other = new Member("20240002", "김철수", Role.MEMBER);
		other.changePasswordHash(passwordEncoder.encode("password456"));
		memberRepository.save(other);
		MockHttpSession otherSession = login("20240002", "password456");
		MockHttpSession adminSession = login("20200001", "adminpass123");

		mockMvc.perform(post("/api/admin/members/" + member.getId() + "/reset-password").session(adminSession))
				.andExpect(status().isOk());

		mockMvc.perform(get("/api/auth/me").session(otherSession)).andExpect(status().isOk());
		mockMvc.perform(get("/api/auth/me").session(adminSession)).andExpect(status().isOk());
	}

	@Test
	@WithMockUser(username = "20200001", roles = "ADMIN")
	void 본인_계정은_비활성화_초기화할_수_없다() throws Exception {
		mockMvc.perform(post("/api/admin/members/" + admin.getId() + "/deactivate"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("CANNOT_MODIFY_SELF"));
		mockMvc.perform(post("/api/admin/members/" + admin.getId() + "/reset-password"))
				.andExpect(status().isBadRequest());
		mockMvc.perform(post("/api/admin/members/999999/deactivate")).andExpect(status().isNotFound());
	}
}
