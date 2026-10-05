package com.sherlock.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.sherlock.domain.Member;
import com.sherlock.domain.Role;
import com.sherlock.repository.MemberRepository;

@SpringBootTest(properties = { "ADMIN_STUDENT_ID=20190001", "ADMIN_PASSWORD=test-admin-pass" })
class AdminAccountInitializerTest {

	@Autowired
	MemberRepository memberRepository;
	@Autowired
	PasswordEncoder passwordEncoder;
	@Autowired
	AdminAccountInitializer initializer;

	@Test
	void 환경변수가_있으면_앱_시작_시_ADMIN_계정이_생성된다() {
		Member admin = memberRepository.findByStudentId("20190001").orElseThrow();

		assertThat(admin.getRole()).isEqualTo(Role.ADMIN);
		assertThat(passwordEncoder.matches("test-admin-pass", admin.getPasswordHash())).isTrue();
	}

	@Test
	void 이미_계정이_있으면_다시_만들지_않는다() {
		long before = memberRepository.count();

		initializer.run(null);

		assertThat(memberRepository.count()).isEqualTo(before);
	}
}
