package com.sherlock.dto;

import java.util.List;

/** 게임 엑셀 가져오기 결과. failed에는 반영하지 않은 행(시트 이름, 엑셀 행 번호 기준)과 사유가 담긴다. */
public record GameImportResponse(int created, int updated, List<Failure> failed) {

	public record Failure(String sheet, int row, String name, String reason) {
	}
}
