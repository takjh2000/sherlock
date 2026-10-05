package com.sherlock.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import com.sherlock.domain.Member;
import com.sherlock.repository.MemberRepository;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * 로그인 세션이 아직 유효한 계정을 가리키는지 요청마다 확인한다.
 * 로그인 시점의 비밀번호 지문과 달라졌거나(비밀번호 초기화/변경) 계정이 비활성화되었으면
 * 세션을 무효화해 그 요청부터 미인증(401) 처리한다.
 */
public class SessionValidationFilter extends OncePerRequestFilter {

	static final String FINGERPRINT_ATTRIBUTE = "AUTH_FINGERPRINT";

	private final MemberRepository memberRepository;

	public SessionValidationFilter(MemberRepository memberRepository) {
		this.memberRepository = memberRepository;
	}

	/** 비밀번호 해시의 SHA-256. 비밀번호가 바뀌거나 지워지면 값이 달라진다. */
	public static String fingerprint(Member member) {
		String hash = member.getPasswordHash() == null ? "" : member.getPasswordHash();
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(hash.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	/** 로그인 직후 세션에 현재 비밀번호 지문을 기록한다. */
	public static void remember(HttpServletRequest request, Member member) {
		request.getSession().setAttribute(FINGERPRINT_ATTRIBUTE, fingerprint(member));
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
			FilterChain chain) throws ServletException, IOException {
		HttpSession session = request.getSession(false);
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (session != null && authentication != null && authentication.isAuthenticated()
				&& !(authentication instanceof AnonymousAuthenticationToken)
				&& !isStillValid(session, authentication.getName())) {
			session.invalidate();
			SecurityContextHolder.clearContext();
		}
		chain.doFilter(request, response);
	}

	private boolean isStillValid(HttpSession session, String studentId) {
		Object saved = session.getAttribute(FINGERPRINT_ATTRIBUTE);
		if (saved == null) {
			// 로그인 API가 만든 세션이 아니다(이 필터 배포 전에 발급된 세션, 테스트용 가짜 인증 등).
			// 세션 만료 시간이 지나면 모두 사라지므로 그대로 둔다.
			return true;
		}
		return memberRepository.findByStudentId(studentId)
				.filter(Member::isActive)
				.map(m -> fingerprint(m).equals(saved))
				.orElse(false);
	}
}
