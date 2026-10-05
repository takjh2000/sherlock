package com.sherlock.dto;

import jakarta.validation.constraints.Size;

/** 반납 요청. report는 분실/파손 신고 내용(선택)이며 GameNote.content 컬럼 길이(1000자)를 넘을 수 없다. */
public record ReturnRequest(@Size(max = ReturnRequest.MAX_REPORT_LENGTH) String report) {

	public static final int MAX_REPORT_LENGTH = 1000;
}
