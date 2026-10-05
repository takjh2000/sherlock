package com.sherlock.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.env.Environment;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

// H2 콘솔 설정이 dev 프로필에서만 적용되는지 확인한다.
class H2ConsoleProfileTest {

	@Nested
	@SpringBootTest
	@AutoConfigureMockMvc
	@ActiveProfiles("prod")
	// prod는 PostgreSQL 환경변수를 요구하므로 테스트에서는 인메모리 H2로 대체한다.
	@TestPropertySource(properties = {
			"spring.datasource.url=jdbc:h2:mem:prodtest;DB_CLOSE_DELAY=-1",
			"spring.datasource.username=sa",
			"spring.datasource.password=",
			"spring.jpa.hibernate.ddl-auto=create-drop"
	})
	class Prod {

		@Autowired
		MockMvc mockMvc;
		@Autowired
		ApplicationContext context;
		@Autowired
		Environment env;

		@Test
		void prod에서는_H2_콘솔이_비활성화된다() {
			assertThat(env.getProperty("spring.h2.console.enabled", Boolean.class, false)).isFalse();
		}

		@Test
		void prod에서는_H2_콘솔_전용_보안_체인이_없다() {
			assertThat(context.containsBean("h2ConsoleFilterChain")).isFalse();
		}

		@Test
		void prod에서는_H2_콘솔_경로가_인증을_요구한다() throws Exception {
			mockMvc.perform(get("/h2-console/")).andExpect(status().isUnauthorized());
		}
	}

	@Nested
	@SpringBootTest
	@AutoConfigureMockMvc
	@ActiveProfiles("dev")
	// 테스트가 로컬 data/ 파일 DB를 건드리지 않도록 인메모리로 대체한다.
	@TestPropertySource(properties = {
			"spring.datasource.url=jdbc:h2:mem:devtest;DB_CLOSE_DELAY=-1",
			"spring.jpa.hibernate.ddl-auto=create-drop"
	})
	class Dev {

		@Autowired
		ApplicationContext context;
		@Autowired
		Environment env;

		@Test
		void dev에서는_H2_콘솔이_활성화되고_보안_체인이_등록된다() {
			assertThat(env.getProperty("spring.h2.console.enabled", Boolean.class, false)).isTrue();
			assertThat(context.containsBean("h2ConsoleFilterChain")).isTrue();
		}
	}
}
