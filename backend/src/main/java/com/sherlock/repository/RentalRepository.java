package com.sherlock.repository;

import com.sherlock.domain.Game;
import com.sherlock.domain.Member;
import com.sherlock.domain.Rental;
import com.sherlock.domain.RentalStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RentalRepository extends JpaRepository<Rental, Long> {

	/** 미반납 대여 수 (RENTED). */
	long countByGameAndStatus(Game game, RentalStatus status);

	List<Rental> findByMemberAndStatus(Member member, RentalStatus status);
}
