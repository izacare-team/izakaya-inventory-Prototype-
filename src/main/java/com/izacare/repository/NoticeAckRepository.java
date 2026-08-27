package com.izacare.repository;

import com.izacare.domain.NoticeAck;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NoticeAckRepository extends JpaRepository<NoticeAck, Long> {
    List<NoticeAck> findByNoticeId(Long noticeId);
    boolean existsByNoticeIdAndMemberId(Long noticeId, Long memberId);
    void deleteByNoticeId(Long noticeId);
}
