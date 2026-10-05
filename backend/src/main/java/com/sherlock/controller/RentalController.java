package com.sherlock.controller;

import com.sherlock.dto.ErrorResponse;
import com.sherlock.dto.RentRequest;
import com.sherlock.dto.RentalResponse;
import com.sherlock.dto.ReturnRequest;
import com.sherlock.service.GameNotFoundException;
import com.sherlock.service.RentalException;
import com.sherlock.service.RentalService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 로그인한 회원만 접근한다(SecurityConfig의 anyRequest().authenticated()). */
@RestController
@RequestMapping("/api/rentals")
public class RentalController {

	private final RentalService rentalService;

	public RentalController(RentalService rentalService) {
		this.rentalService = rentalService;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public RentalResponse rent(@Valid @RequestBody RentRequest body, Authentication authentication) {
		return rentalService.rent(authentication.getName(), body.gameId());
	}

	@GetMapping("/me")
	public List<RentalResponse> mine(Authentication authentication) {
		return rentalService.myRentals(authentication.getName());
	}

	/** 본문은 선택이다(신고 없이 반납 가능). */
	@PostMapping("/{id}/return")
	public RentalResponse returnRental(@PathVariable Long id,
			@Valid @RequestBody(required = false) ReturnRequest body, Authentication authentication) {
		return rentalService.returnRental(authentication.getName(), id,
				body == null ? null : body.report());
	}

	@ExceptionHandler(RentalException.class)
	ResponseEntity<ErrorResponse> handleRental(RentalException e) {
		return ResponseEntity.status(e.getStatus()).body(new ErrorResponse(e.getCode(), e.getMessage()));
	}

	@ExceptionHandler(GameNotFoundException.class)
	ResponseEntity<ErrorResponse> handleNotFound(GameNotFoundException e) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND)
				.body(new ErrorResponse("GAME_NOT_FOUND", e.getMessage()));
	}

	@ExceptionHandler({ MethodArgumentNotValidException.class, HttpMessageNotReadableException.class })
	ResponseEntity<ErrorResponse> handleInvalid() {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST)
				.body(new ErrorResponse("INVALID_REQUEST", "요청 값이 올바르지 않습니다."));
	}
}
