package com.sherlock.dto;

import com.sherlock.domain.Member;
import com.sherlock.domain.Role;

public record MeResponse(String studentId, String name, Role role) {

	public static MeResponse from(Member member) {
		return new MeResponse(member.getStudentId(), member.getName(), member.getRole());
	}
}
