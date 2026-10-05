package com.sherlock.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sherlock.domain.Member;
import com.sherlock.dto.ErrorResponse;
import com.sherlock.dto.LoginRequest;
import com.sherlock.dto.MeResponse;
import com.sherlock.dto.SetupPasswordRequest;
import com.sherlock.service.AuthException;
import com.sherlock.service.AuthService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

	private final AuthService authService;
	private final SecurityContextRepository securityContextRepository;

	public AuthController(AuthService authService, SecurityContextRepository securityContextRepository) {
		this.authService = authService;
		this.securityContextRepository = securityContextRepository;
	}

	@PostMapping("/login")
	public MeResponse login(@Valid @RequestBody LoginRequest body,
			HttpServletRequest request, HttpServletResponse response) {
		Member member = authService.authenticate(body.studentId().trim(), body.password());

		// 세션 고정 공격 방지: 기존 세션이 있으면 ID를 교체한다.
		if (request.getSession(false) != null) {
			request.changeSessionId();
		}
		Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
				member.getStudentId(), null,
				List.of(new SimpleGrantedAuthority("ROLE_" + member.getRole().name())));
		SecurityContext context = SecurityContextHolder.createEmptyContext();
		context.setAuthentication(authentication);
		SecurityContextHolder.setContext(context);
		securityContextRepository.saveContext(context, request, response);
		return MeResponse.from(member);
	}

	@PostMapping("/logout")
	public ResponseEntity<Void> logout(HttpServletRequest request) {
		HttpSession session = request.getSession(false);
		if (session != null) {
			session.invalidate();
		}
		SecurityContextHolder.clearContext();
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/me")
	public MeResponse me(Authentication authentication) {
		return MeResponse.from(authService.getMember(authentication.getName()));
	}

	@PostMapping("/setup-password")
	public ResponseEntity<Void> setupPassword(@Valid @RequestBody SetupPasswordRequest body) {
		authService.setupPassword(body.studentId().trim(), body.name(), body.password());
		return ResponseEntity.noContent().build();
	}

	@ExceptionHandler(AuthException.class)
	ResponseEntity<ErrorResponse> handleAuth(AuthException e) {
		return ResponseEntity.status(e.getStatus()).body(new ErrorResponse(e.getCode(), e.getMessage()));
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	ResponseEntity<ErrorResponse> handleInvalid() {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST)
				.body(new ErrorResponse("INVALID_REQUEST", "요청 값이 올바르지 않습니다."));
	}
}
