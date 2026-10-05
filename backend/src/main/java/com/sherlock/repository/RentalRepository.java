package com.sherlock.repository;

import com.sherlock.domain.Game;
import com.sherlock.domain.Member;
import com.sherlock.domain.Rental;
import com.sherlock.domain.RentalStatus;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
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

	/** 내 대여 목록(최신순). 화면에서 게임 이름을 쓰므로 게임을 함께 가져온다. */
	@Query("select r from Rental r join fetch r.game where r.member = :member order by r.id desc")
	List<Rental> findByMemberWithGame(@Param("member") Member member);

	/** 관리자용: 전체 미반납 대여(오래된 대여일 순). 게임과 회원을 함께 가져온다. */
	@Query("select r from Rental r join fetch r.game join fetch r.member where r.status = :status "
			+ "order by r.dueDate asc, r.id asc")
	List<Rental> findAllWithGameAndMemberByStatus(@Param("status") RentalStatus status);

	/** 관리자용: 연체 집계 후보. 미반납이면서 예정일이 지났거나, 예정일을 넘겨 반납한 대여. */
	@Query("select r from Rental r join fetch r.game join fetch r.member "
			+ "where (r.status = com.sherlock.domain.RentalStatus.RENTED and r.dueDate < :today) "
			+ "or (r.status = com.sherlock.domain.RentalStatus.RETURNED and r.returnedDate > r.dueDate)")
	List<Rental> findOverdueCandidates(@Param("today") java.time.LocalDate today);

	/** 반납 중복 처리를 막기 위해 대여 행에 락을 걸고 조회한다. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select r from Rental r where r.id = :id")
	Optional<Rental> findByIdForUpdate(@Param("id") Long id);
}
