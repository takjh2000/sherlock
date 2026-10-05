package com.sherlock.controller;

import com.sherlock.dto.AdminRentalResponse;
import com.sherlock.dto.MemberOverdueStatResponse;
import com.sherlock.service.AdminRentalService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 대여 현황 / 연체 / 강제 반납. ADMIN 전용(SecurityConfig의 /api/admin/**). */
@RestController
@RequestMapping("/api/admin/rentals")
public class AdminRentalController {

	private final AdminRentalService adminRentalService;

	public AdminRentalController(AdminRentalService adminRentalService) {
		this.adminRentalService = adminRentalService;
	}

	@GetMapping("/active")
	public List<AdminRentalResponse> active() {
		return adminRentalService.activeRentals();
	}

	@GetMapping("/overdue")
	public List<AdminRentalResponse> overdue() {
		return adminRentalService.overdueRentals();
	}

	@GetMapping("/overdue-stats")
	public List<MemberOverdueStatResponse> overdueStats() {
		return adminRentalService.memberOverdueStats();
	}

	@PostMapping("/{id}/force-return")
	public AdminRentalResponse forceReturn(@PathVariable Long id) {
		return adminRentalService.forceReturn(id);
	}
}
