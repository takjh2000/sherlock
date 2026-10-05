package com.sherlock.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/** 게임 특이사항 이력(부속품 분실 등). */
@Entity
@Table(name = "game_note")
public class GameNote {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "game_id")
	private Game game;

	@Column(nullable = false, length = 1000)
	private String content;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "author_id")
	private Member author;

	@Column(nullable = false, updatable = false)
	private LocalDateTime createdAt;

	protected GameNote() {
	}

	public GameNote(Game game, String content, Member author) {
		this.game = game;
		this.content = content;
		this.author = author;
	}

	@PrePersist
	void onCreate() {
		this.createdAt = LocalDateTime.now();
	}

	public void updateContent(String content) {
		this.content = content;
	}

	public Long getId() {
		return id;
	}

	public Game getGame() {
		return game;
	}

	public String getContent() {
		return content;
	}

	public Member getAuthor() {
		return author;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}
}
