package com.sherlock.dto;

import com.sherlock.domain.Member;
import com.sherlock.domain.Role;

/** passwordSet이 false면 아직 첫 로그인(비밀번호 설정) 전이다. */
public record AdminMemberResponse(Long id, String studentId, String name, Role role, boolean active,
		boolean passwordSet) {

	public static AdminMemberResponse from(Member member) {
		return new AdminMemberResponse(member.getId(), member.getStudentId(), member.getName(),
				member.getRole(), member.isActive(), member.getPasswordHash() != null);
	}
}
