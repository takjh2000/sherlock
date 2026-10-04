package com.sherlock.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "game")
public class Game {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private String name;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private GameCategory category;

	// 소유자는 없을 수 있다.
	private String owner;

	@Column(nullable = false)
	private int totalQuantity;

	// soft delete 여부
	@Column(nullable = false)
	private boolean deleted = false;

	@Column(nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@Column(nullable = false)
	private LocalDateTime updatedAt;

	protected Game() {
	}

	public Game(String name, GameCategory category, String owner, int totalQuantity) {
		validateQuantity(totalQuantity);
		this.name = name;
		this.category = category;
		this.owner = owner;
		this.totalQuantity = totalQuantity;
	}

	@PrePersist
	void onCreate() {
		LocalDateTime now = LocalDateTime.now();
		this.createdAt = now;
		this.updatedAt = now;
	}

	@PreUpdate
	void onUpdate() {
		this.updatedAt = LocalDateTime.now();
	}

	/** 대여 가능 수량 = 전체 수량 - 미반납 대여 수. */
	public int availableQuantity(long activeRentalCount) {
		return (int) Math.max(0, totalQuantity - activeRentalCount);
	}

	public void update(String name, GameCategory category, String owner, int totalQuantity) {
		validateQuantity(totalQuantity);
		this.name = name;
		this.category = category;
		this.owner = owner;
		this.totalQuantity = totalQuantity;
	}

	public void delete() {
		this.deleted = true;
	}

	private static void validateQuantity(int totalQuantity) {
		if (totalQuantity < 1) {
			throw new IllegalArgumentException("전체 수량은 1 이상이어야 합니다.");
		}
	}

	public Long getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public GameCategory getCategory() {
		return category;
	}

	public String getOwner() {
		return owner;
	}

	public int getTotalQuantity() {
		return totalQuantity;
	}

	public boolean isDeleted() {
		return deleted;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public LocalDateTime getUpdatedAt() {
		return updatedAt;
	}
}
