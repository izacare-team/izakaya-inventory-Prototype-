package com.izacare.repository;

import com.izacare.domain.CommentHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CommentHistoryRepository extends JpaRepository<CommentHistory, Long> {
    List<CommentHistory> findByCommentIdOrderByEditedAtDesc(Long commentId);

    /** 댓글 삭제 시 이력도 함께 정리 */
    void deleteByCommentId(Long commentId);
}
