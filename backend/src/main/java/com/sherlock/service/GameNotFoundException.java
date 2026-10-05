package com.sherlock.service;

public class GameNotFoundException extends RuntimeException {

	public GameNotFoundException(Long id) {
		super("게임을 찾을 수 없습니다. id=" + id);
	}
}
