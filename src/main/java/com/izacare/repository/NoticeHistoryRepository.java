package com.izacare.repository;

import com.izacare.domain.NoticeHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NoticeHistoryRepository extends JpaRepository<NoticeHistory, Long> {
    List<NoticeHistory> findByNoticeIdOrderByEditedAtDesc(Long noticeId);
    void deleteByNoticeId(Long noticeId);
}
