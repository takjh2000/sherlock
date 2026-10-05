package com.sherlock.repository;

import com.sherlock.domain.Game;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GameRepository extends JpaRepository<Game, Long> {

	List<Game> findByDeletedFalse();
}
