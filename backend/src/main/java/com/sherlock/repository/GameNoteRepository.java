package com.sherlock.repository;

import com.sherlock.domain.Game;
import com.sherlock.domain.GameNote;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GameNoteRepository extends JpaRepository<GameNote, Long> {

	List<GameNote> findByGameOrderByCreatedAtDesc(Game game);

	/** 상세 화면용: 작성자까지 한 번에 가져온 특이사항 이력(최신순). */
	@Query("select n from GameNote n join fetch n.author where n.game.id = :gameId "
			+ "order by n.createdAt desc, n.id desc")
	List<GameNote> findHistoryByGameId(@Param("gameId") Long gameId);

	/** 목록 화면용: 게임별 가장 최근 특이사항 1건씩. */
	@Query("select n from GameNote n join fetch n.author where n.game.id in :gameIds and n.id = "
			+ "(select max(n2.id) from GameNote n2 where n2.game.id = n.game.id)")
	List<GameNote> findLatestByGameIds(@Param("gameIds") Collection<Long> gameIds);
}
