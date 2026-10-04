package com.sherlock.repository;

import com.sherlock.domain.Game;
import com.sherlock.domain.GameNote;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GameNoteRepository extends JpaRepository<GameNote, Long> {

	List<GameNote> findByGameOrderByCreatedAtDesc(Game game);
}
