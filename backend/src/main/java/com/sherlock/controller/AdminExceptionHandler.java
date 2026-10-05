package com.sherlock.controller;

import com.sherlock.dto.ErrorResponse;
import com.sherlock.service.AdminException;
import com.sherlock.service.GameNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

/** 관리자 API 공통 오류 응답. */
@RestControllerAdvice(assignableTypes = { AdminGameController.class, AdminMemberController.class,
		AdminRentalController.class })
public class AdminExceptionHandler {

	@ExceptionHandler(AdminException.class)
	ResponseEntity<ErrorResponse> handleAdmin(AdminException e) {
		return ResponseEntity.status(e.getStatus()).body(new ErrorResponse(e.getCode(), e.getMessage()));
	}

	@ExceptionHandler(GameNotFoundException.class)
	ResponseEntity<ErrorResponse> handleNotFound(GameNotFoundException e) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND)
				.body(new ErrorResponse("GAME_NOT_FOUND", e.getMessage()));
	}

	@ExceptionHandler(MaxUploadSizeExceededException.class)
	ResponseEntity<ErrorResponse> handleTooLarge() {
		return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
				.body(new ErrorResponse("FILE_TOO_LARGE", "파일이 너무 큽니다."));
	}

	@ExceptionHandler({ MethodArgumentNotValidException.class, HttpMessageNotReadableException.class,
			MethodArgumentTypeMismatchException.class, MissingServletRequestPartException.class,
			MissingServletRequestParameterException.class })
	ResponseEntity<ErrorResponse> handleInvalid() {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST)
				.body(new ErrorResponse("INVALID_REQUEST", "요청 값이 올바르지 않습니다."));
	}
}
