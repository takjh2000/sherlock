package com.sherlock.service;

import org.springframework.http.HttpStatus;

public class RentalException extends RuntimeException {

	private final HttpStatus status;
	private final String code;

	public RentalException(HttpStatus status, String code, String message) {
		super(message);
		this.status = status;
		this.code = code;
	}

	public HttpStatus getStatus() {
		return status;
	}

	public String getCode() {
		return code;
	}
}
