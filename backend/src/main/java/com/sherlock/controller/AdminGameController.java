package com.sherlock.controller;

import com.sherlock.domain.GameCategory;
import com.sherlock.dto.AdminGameDetailResponse;
import com.sherlock.dto.AdminGameResponse;
import com.sherlock.dto.GameNoteResponse;
import com.sherlock.dto.GameRequest;
import com.sherlock.dto.NoteRequest;
import com.sherlock.dto.PageResponse;
import com.sherlock.service.AdminGameService;
import com.sherlock.service.GameQueryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 게임/특이사항 관리. ADMIN 전용(SecurityConfig의 /api/admin/**). */
@RestController
@RequestMapping("/api/admin")
public class AdminGameController {

	private final AdminGameService adminGameService;
	private final GameQueryService gameQueryService;

	public AdminGameController(AdminGameService adminGameService, GameQueryService gameQueryService) {
		this.adminGameService = adminGameService;
		this.gameQueryService = gameQueryService;
	}

	/** includeDeleted=true면 삭제된 게임도 함께 보여준다. */
	@GetMapping("/games")
	public PageResponse<AdminGameResponse> list(
			@RequestParam(required = false) String keyword,
			@RequestParam(required = false) GameCategory category,
			@RequestParam(defaultValue = "false") boolean includeDeleted,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		return gameQueryService.searchForAdmin(keyword, category, includeDeleted, page, size);
	}

	@GetMapping("/games/{id}")
	public AdminGameDetailResponse detail(@PathVariable Long id) {
		return gameQueryService.adminDetail(id);
	}

	@PostMapping("/games")
	@ResponseStatus(HttpStatus.CREATED)
	public AdminGameDetailResponse create(@Valid @RequestBody GameRequest body) {
		return adminGameService.create(body);
	}

	@PutMapping("/games/{id}")
	public AdminGameDetailResponse update(@PathVariable Long id, @Valid @RequestBody GameRequest body) {
		return adminGameService.update(id, body);
	}

	@DeleteMapping("/games/{id}")
	public ResponseEntity<Void> delete(@PathVariable Long id) {
		adminGameService.delete(id);
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/games/{id}/notes")
	@ResponseStatus(HttpStatus.CREATED)
	public GameNoteResponse addNote(@PathVariable Long id, @Valid @RequestBody NoteRequest body,
			Authentication authentication) {
		return adminGameService.addNote(authentication.getName(), id, body.content());
	}

	@PutMapping("/notes/{noteId}")
	public GameNoteResponse updateNote(@PathVariable Long noteId, @Valid @RequestBody NoteRequest body) {
		return adminGameService.updateNote(noteId, body.content());
	}

	@DeleteMapping("/notes/{noteId}")
	public ResponseEntity<Void> deleteNote(@PathVariable Long noteId) {
		adminGameService.deleteNote(noteId);
		return ResponseEntity.noContent().build();
	}
}
