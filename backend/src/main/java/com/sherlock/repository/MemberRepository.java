package com.sherlock.repository;

import com.sherlock.domain.Member;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberRepository extends JpaRepository<Member, Long> {

	Optional<Member> findByStudentId(String studentId);

	List<Member> findByStudentIdIn(Collection<String> studentIds);

	/** 학번 또는 이름에 키워드가 포함된 회원(대소문자 무시). */
	Page<Member> findByStudentIdContainingIgnoreCaseOrNameContainingIgnoreCase(String studentId, String name,
			Pageable pageable);
}
