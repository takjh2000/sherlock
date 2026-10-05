package com.sherlock.dto;

import java.util.List;

/** 엑셀 일괄 등록 결과. skipped에는 등록하지 않은 행(엑셀 행 번호 기준)과 사유가 담긴다. */
public record MemberImportResponse(int created, List<Skipped> skipped) {

	public record Skipped(int row, String studentId, String reason) {
	}
}
