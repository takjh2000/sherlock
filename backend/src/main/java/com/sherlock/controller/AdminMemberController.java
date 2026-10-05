package com.sherlock.controller;

import com.sherlock.dto.AdminMemberResponse;
import com.sherlock.dto.MemberCreateRequest;
import com.sherlock.dto.MemberImportResponse;
import com.sherlock.dto.PageResponse;
import com.sherlock.service.AdminException;
import com.sherlock.service.AdminMemberService;
import jakarta.validation.Valid;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** 회원 명단 관리. ADMIN 전용(SecurityConfig의 /api/admin/**). */
@RestController
@RequestMapping("/api/admin/members")
public class AdminMemberController {

	private final AdminMemberService adminMemberService;

	public AdminMemberController(AdminMemberService adminMemberService) {
		this.adminMemberService = adminMemberService;
	}

	@GetMapping
	public PageResponse<AdminMemberResponse> list(@RequestParam(required = false) String keyword,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
		return adminMemberService.list(keyword, page, size);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public AdminMemberResponse create(@Valid @RequestBody MemberCreateRequest body) {
		return adminMemberService.create(body.studentId(), body.name());
	}

	/** 엑셀(xlsx) 일괄 등록. 파트 이름은 file. */
	@PostMapping(path = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public MemberImportResponse importExcel(@RequestParam("file") MultipartFile file) {
		try {
			return adminMemberService.importFromExcel(file.getInputStream());
		} catch (IOException e) {
			throw new AdminException(HttpStatus.BAD_REQUEST, "INVALID_FILE", "파일을 읽을 수 없습니다.");
		}
	}

	@PostMapping("/{id}/deactivate")
	public AdminMemberResponse deactivate(@PathVariable Long id, Authentication authentication) {
		return adminMemberService.deactivate(authentication.getName(), id);
	}

	@PostMapping("/{id}/reset-password")
	public AdminMemberResponse resetPassword(@PathVariable Long id, Authentication authentication) {
		return adminMemberService.resetPassword(authentication.getName(), id);
	}
}
