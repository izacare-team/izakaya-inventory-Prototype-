package com.izacare.repository;

import com.izacare.domain.Member;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, Long> {

    /** 아이디로 조회 (로그인 — 아이디는 전역 유니크) */
    Optional<Member> findByUsername(String username);

    /** 아이디 중복 검사 (가입 — 전역) */
    boolean existsByUsername(String username);

    /** 가게 직원 목록 */
    List<Member> findByStoreIdOrderByStatusAscCreatedAtAsc(Long storeId);

    /** 가게 내 상태·역할별 회원 (알림 대상 산출 등) */
    List<Member> findByStoreIdAndStatus(Long storeId, Member.Status status);
}
