package com.sherlock.dto;

/**
 * 회원별 연체 누적. overdueCount는 연체한 대여 건수(반납 완료 포함),
 * overdueDays는 그 연체 일수의 합, currentlyOverdue는 지금 연체 중인 미반납 건수.
 */
public record MemberOverdueStatResponse(String studentId, String name, long overdueCount, long overdueDays,
		long currentlyOverdue) {
}
