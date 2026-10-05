package com.sherlock.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "member")
public class Member {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true)
	private String studentId;

	@Column(nullable = false)
	private String name;

	// 첫 로그인 전에는 null
	private String passwordHash;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Role role;

	@Column(nullable = false)
	private boolean active = true;

	protected Member() {
	}

	public Member(String studentId, String name, Role role) {
		this.studentId = studentId;
		this.name = name;
		this.role = role;
	}

	public void changePasswordHash(String passwordHash) {
		this.passwordHash = passwordHash;
	}

	public void deactivate() {
		this.active = false;
	}

	/** 비밀번호를 지워 다시 첫 로그인(비밀번호 설정) 상태로 되돌린다. */
	public void resetPassword() {
		this.passwordHash = null;
	}

	public Long getId() {
		return id;
	}

	public String getStudentId() {
		return studentId;
	}

	public String getName() {
		return name;
	}

	public String getPasswordHash() {
		return passwordHash;
	}

	public Role getRole() {
		return role;
	}

	public boolean isActive() {
		return active;
	}
}
