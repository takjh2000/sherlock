package com.sherlock.repository;

import com.sherlock.domain.Game;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GameRepository extends JpaRepository<Game, Long>, JpaSpecificationExecutor<Game> {

	List<Game> findByDeletedFalse();

	/** 대여 동시성 제어용: 삭제되지 않은 게임 행에 비관적 쓰기 락을 걸고 조회한다. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select g from Game g where g.id = :id and g.deleted = false")
	Optional<Game> findActiveByIdForUpdate(@Param("id") Long id);
}
