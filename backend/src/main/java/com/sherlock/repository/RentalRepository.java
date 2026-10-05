package com.sherlock.repository;

import com.sherlock.domain.Game;
import com.sherlock.domain.Member;
import com.sherlock.domain.Rental;
import com.sherlock.domain.RentalStatus;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RentalRepository extends JpaRepository<Rental, Long> {

	/** 미반납 대여 수 (RENTED). */
	long countByGameAndStatus(Game game, RentalStatus status);

	List<Rental> findByMemberAndStatus(Member member, RentalStatus status);

	/** 게임별 미반납 대여 수. 대여가 없는 게임은 결과에 없다. [gameId, count] */
	@Query("select r.game.id, count(r) from Rental r where r.game.id in :gameIds "
			+ "and r.status = :status group by r.game.id")
	List<Object[]> countByGameIdsAndStatus(@Param("gameIds") Collection<Long> gameIds,
			@Param("status") RentalStatus status);
}
