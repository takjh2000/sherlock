package com.sherlock.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;

import com.sherlock.domain.Game;
import com.sherlock.domain.GameCategory;
import com.sherlock.domain.Member;
import com.sherlock.domain.RentalStatus;
import com.sherlock.domain.Role;
import com.sherlock.repository.GameNoteRepository;
import com.sherlock.repository.GameRepository;
import com.sherlock.repository.MemberRepository;
import com.sherlock.repository.RentalRepository;

/** 실제 트랜잭션 커밋이 필요하므로 @Transactional 없이 여러 스레드로 검증한다. */
@SpringBootTest
class RentalConcurrencyTest {

	@Autowired
	RentalService rentalService;
	@Autowired
	GameRepository gameRepository;
	@Autowired
	RentalRepository rentalRepository;
	@Autowired
	GameNoteRepository gameNoteRepository;
	@Autowired
	MemberRepository memberRepository;

	@AfterEach
	void cleanUp() {
		gameNoteRepository.deleteAll();
		rentalRepository.deleteAll();
		gameRepository.deleteAll();
		memberRepository.deleteAll();
	}

	@Test
	void 마지막_1개를_동시에_대여하면_한_명만_성공한다() throws Exception {
		Game game = gameRepository.save(new Game("카탄", GameCategory.BOARD_GAME, null, 1));
		int threads = 10;
		List<String> studentIds = new ArrayList<>();
		for (int i = 0; i < threads; i++) {
			String studentId = "2024" + String.format("%04d", i);
			memberRepository.save(new Member(studentId, "회원" + i, Role.MEMBER));
			studentIds.add(studentId);
		}

		AtomicInteger success = new AtomicInteger();
		AtomicInteger outOfStock = new AtomicInteger();
		CountDownLatch ready = new CountDownLatch(threads);
		CountDownLatch start = new CountDownLatch(1);
		ExecutorService pool = Executors.newFixedThreadPool(threads);
		try {
			List<Future<?>> futures = new ArrayList<>();
			for (String studentId : studentIds) {
				Callable<Void> task = () -> {
					ready.countDown();
					start.await();
					try {
						rentalService.rent(studentId, game.getId());
						success.incrementAndGet();
					}
					catch (RentalException e) {
						if (e.getStatus() == HttpStatus.CONFLICT) {
							outOfStock.incrementAndGet();
						}
						else {
							throw e;
						}
					}
					return null;
				};
				futures.add(pool.submit(task));
			}
			ready.await();
			start.countDown();
			for (Future<?> f : futures) {
				f.get();
			}
		}
		finally {
			pool.shutdownNow();
		}

		assertEquals(1, success.get());
		assertEquals(threads - 1, outOfStock.get());
		assertEquals(1, rentalRepository.countByGameAndStatus(game, RentalStatus.RENTED));
	}
}
