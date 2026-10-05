package com.sherlock.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

@Entity
@Table(name = "rental")
public class Rental {

	/** 대여 기간(일). */
	public static final int RENTAL_DAYS = 7;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "game_id")
	private Game game;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "member_id")
	private Member member;

	@Column(nullable = false)
	private LocalDate rentedDate;

	@Column(nullable = false)
	private LocalDate dueDate;

	private LocalDate returnedDate;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private RentalStatus status;

	protected Rental() {
	}

	public Rental(Game game, Member member, LocalDate rentedDate) {
		this.game = game;
		this.member = member;
		this.rentedDate = rentedDate;
		this.dueDate = rentedDate.plusDays(RENTAL_DAYS);
		this.status = RentalStatus.RENTED;
	}

	public void markReturned(LocalDate returnedDate) {
		if (status == RentalStatus.RETURNED) {
			throw new IllegalStateException("이미 반납된 대여입니다.");
		}
		this.returnedDate = returnedDate;
		this.status = RentalStatus.RETURNED;
	}

	/** 미반납이면 기준일이, 반납됐으면 반납일이 반납 예정일을 넘겼을 때 연체. */
	public boolean isOverdue(LocalDate today) {
		return overdueDays(today) > 0;
	}

	/** 연체 일수. 연체가 아니면 0. */
	public long overdueDays(LocalDate today) {
		LocalDate end = status == RentalStatus.RETURNED ? returnedDate : today;
		return Math.max(0, ChronoUnit.DAYS.between(dueDate, end));
	}

	public Long getId() {
		return id;
	}

	public Game getGame() {
		return game;
	}

	public Member getMember() {
		return member;
	}

	public LocalDate getRentedDate() {
		return rentedDate;
	}

	public LocalDate getDueDate() {
		return dueDate;
	}

	public LocalDate getReturnedDate() {
		return returnedDate;
	}

	public RentalStatus getStatus() {
		return status;
	}
}
