package com.sherlock.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.sherlock.domain.Member;
import com.sherlock.domain.Role;
import com.sherlock.repository.MemberRepository;

/** 환경변수 ADMIN_STUDENT_ID, ADMIN_PASSWORD가 있고 해당 계정이 없으면 ADMIN을 만든다. */
@Component
public class AdminAccountInitializer implements ApplicationRunner {

	private final MemberRepository memberRepository;
	private final PasswordEncoder passwordEncoder;
	private final String studentId;
	private final String password;

	public AdminAccountInitializer(MemberRepository memberRepository, PasswordEncoder passwordEncoder,
			@Value("${ADMIN_STUDENT_ID:}") String studentId,
			@Value("${ADMIN_PASSWORD:}") String password) {
		this.memberRepository = memberRepository;
		this.passwordEncoder = passwordEncoder;
		this.studentId = studentId;
		this.password = password;
	}

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		if (studentId.isBlank() || password.isBlank()) {
			return;
		}
		if (memberRepository.findByStudentId(studentId).isPresent()) {
			return;
		}
		Member admin = new Member(studentId, "관리자", Role.ADMIN);
		admin.changePasswordHash(passwordEncoder.encode(password));
		memberRepository.save(admin);
	}
}
