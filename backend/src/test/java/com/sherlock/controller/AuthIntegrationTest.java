package com.sherlock.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import com.sherlock.domain.Member;
import com.sherlock.domain.Role;
import com.sherlock.repository.MemberRepository;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthIntegrationTest {

	@Autowired
	MockMvc mockMvc;
	@Autowired
	MemberRepository memberRepository;
	@Autowired
	PasswordEncoder passwordEncoder;

	@BeforeEach
	void setUp() {
		Member member = new Member("20240001", "홍길동", Role.MEMBER);
		member.changePasswordHash(passwordEncoder.encode("password123"));
		memberRepository.save(member);

		Member admin = new Member("20200001", "임원", Role.ADMIN);
		admin.changePasswordHash(passwordEncoder.encode("adminpass123"));
		memberRepository.save(admin);

		// 비밀번호 미설정 회원
		memberRepository.save(new Member("20240002", "김신입", Role.MEMBER));
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

	@Test
	void 로그인에_성공하면_내_정보를_조회할_수_있다() throws Exception {
		MockHttpSession session = login("20240001", "password123");

		mockMvc.perform(get("/api/auth/me").session(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.studentId").value("20240001"))
				.andExpect(jsonPath("$.name").value("홍길동"))
				.andExpect(jsonPath("$.role").value("MEMBER"));
	}

	@Test
	void 비밀번호가_틀리면_401() throws Exception {
		mockMvc.perform(json(post("/api/auth/login"),
				"{\"studentId\":\"20240001\",\"password\":\"wrong-password\"}"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("BAD_CREDENTIALS"));
	}

	@Test
	void 없는_학번도_같은_401() throws Exception {
		mockMvc.perform(json(post("/api/auth/login"),
				"{\"studentId\":\"99999999\",\"password\":\"password123\"}"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("BAD_CREDENTIALS"));
	}

	@Test
	void 비밀번호_미설정_회원은_설정_안내_코드를_받는다() throws Exception {
		mockMvc.perform(json(post("/api/auth/login"),
				"{\"studentId\":\"20240002\",\"password\":\"whatever123\"}"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("PASSWORD_NOT_SET"));
	}

	@Test
	void 로그아웃하면_세션이_무효화된다() throws Exception {
		MockHttpSession session = login("20240001", "password123");

		mockMvc.perform(post("/api/auth/logout").session(session))
				.andExpect(status().isNoContent());

		mockMvc.perform(get("/api/auth/me").session(session))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void 미인증_요청은_401() throws Exception {
		mockMvc.perform(get("/api/auth/me"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
		mockMvc.perform(get("/api/games"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void 일반_회원이_관리자_경로에_접근하면_403() throws Exception {
		MockHttpSession session = login("20240001", "password123");

		mockMvc.perform(get("/api/admin/games").session(session))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("FORBIDDEN"));
	}

	@Test
	void 관리자는_관리자_경로의_권한_검사를_통과한다() throws Exception {
		MockHttpSession session = login("20200001", "adminpass123");

		mockMvc.perform(get("/api/admin/games").session(session))
				.andExpect(status().isOk());
	}

	@Test
	void 첫_비밀번호_설정_후_로그인할_수_있다() throws Exception {
		mockMvc.perform(json(post("/api/auth/setup-password"),
				"{\"studentId\":\"20240002\",\"name\":\"김신입\",\"password\":\"newpassword1\"}"))
				.andExpect(status().isNoContent());

		login("20240002", "newpassword1");
	}

	@Test
	void 이름이_다르면_비밀번호를_설정할_수_없다() throws Exception {
		mockMvc.perform(json(post("/api/auth/setup-password"),
				"{\"studentId\":\"20240002\",\"name\":\"다른이름\",\"password\":\"newpassword1\"}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void 이미_비밀번호가_있는_회원은_다시_설정할_수_없다() throws Exception {
		mockMvc.perform(json(post("/api/auth/setup-password"),
				"{\"studentId\":\"20240001\",\"name\":\"홍길동\",\"password\":\"newpassword1\"}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void 너무_짧은_비밀번호는_거부한다() throws Exception {
		mockMvc.perform(json(post("/api/auth/setup-password"),
				"{\"studentId\":\"20240002\",\"name\":\"김신입\",\"password\":\"short\"}"))
				.andExpect(status().isBadRequest());
	}
}
