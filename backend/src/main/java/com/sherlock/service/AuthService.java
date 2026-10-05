package com.sherlock.service;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sherlock.domain.Member;
import com.sherlock.repository.MemberRepository;

@Service
public class AuthService {

	private final MemberRepository memberRepository;
	private final PasswordEncoder passwordEncoder;

	public AuthService(MemberRepository memberRepository, PasswordEncoder passwordEncoder) {
		this.memberRepository = memberRepository;
		this.passwordEncoder = passwordEncoder;
	}

	/** 학번/비밀번호를 검사한다. 실패 사유는 외부에 구분해 알리지 않는다(비밀번호 미설정 제외). */
	@Transactional(readOnly = true)
	public Member authenticate(String studentId, String rawPassword) {
		Member member = memberRepository.findByStudentId(studentId)
				.filter(Member::isActive)
				.orElseThrow(AuthService::badCredentials);
		if (member.getPasswordHash() == null) {
			throw new AuthException(HttpStatus.UNAUTHORIZED, "PASSWORD_NOT_SET",
					"첫 로그인입니다. 비밀번호를 먼저 설정해주세요.");
		}
		if (!passwordEncoder.matches(rawPassword, member.getPasswordHash())) {
			throw badCredentials();
		}
		return member;
	}

	/** 비밀번호가 없는 회원이 학번 + 이름 확인 후 비밀번호를 설정한다. */
	@Transactional
	public void setupPassword(String studentId, String name, String rawPassword) {
		Member member = memberRepository.findByStudentId(studentId)
				.filter(Member::isActive)
				.filter(m -> m.getPasswordHash() == null)
				.filter(m -> m.getName().equals(name.trim()))
				.orElseThrow(() -> new AuthException(HttpStatus.BAD_REQUEST, "SETUP_REJECTED",
						"학번과 이름을 확인할 수 없거나 이미 비밀번호가 설정되어 있습니다."));
		member.changePasswordHash(passwordEncoder.encode(rawPassword));
	}

	@Transactional(readOnly = true)
	public Member getMember(String studentId) {
		return memberRepository.findByStudentId(studentId)
				.filter(Member::isActive)
				.orElseThrow(() -> new AuthException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED",
						"로그인이 필요합니다."));
	}

	private static AuthException badCredentials() {
		return new AuthException(HttpStatus.UNAUTHORIZED, "BAD_CREDENTIALS",
				"학번 또는 비밀번호가 올바르지 않습니다.");
	}
}
