package com.sherlock.controller;

import com.sherlock.domain.GameCategory;
import com.sherlock.dto.ErrorResponse;
import com.sherlock.dto.GameDetailResponse;
import com.sherlock.dto.GameSummaryResponse;
import com.sherlock.dto.PageResponse;
import com.sherlock.service.GameNotFoundException;
import com.sherlock.service.GameQueryService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** 로그인한 회원만 접근한다(SecurityConfig의 anyRequest().authenticated()). */
@RestController
@RequestMapping("/api/games")
public class GameController {

	private final GameQueryService gameQueryService;

	public GameController(GameQueryService gameQueryService) {
		this.gameQueryService = gameQueryService;
	}

	@GetMapping
	public PageResponse<GameSummaryResponse> list(
			@RequestParam(required = false) String keyword,
			@RequestParam(required = false) GameCategory category,
			@RequestParam(defaultValue = "false") boolean availableOnly,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		return gameQueryService.search(keyword, category, availableOnly, page, size);
	}

	@GetMapping("/{id}")
	public GameDetailResponse detail(@PathVariable Long id) {
		return gameQueryService.detail(id);
	}

	@ExceptionHandler(GameNotFoundException.class)
	ResponseEntity<ErrorResponse> handleNotFound(GameNotFoundException e) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND)
				.body(new ErrorResponse("GAME_NOT_FOUND", e.getMessage()));
	}

	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	ResponseEntity<ErrorResponse> handleBadParam() {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST)
				.body(new ErrorResponse("INVALID_REQUEST", "요청 값이 올바르지 않습니다."));
	}
}
