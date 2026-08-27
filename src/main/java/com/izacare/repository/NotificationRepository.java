package com.izacare.repository;

import com.izacare.domain.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findByRecipientIdAndReadFlagFalseOrderByCreatedAtDesc(Long memberId);
    List<Notification> findTop30ByRecipientIdOrderByCreatedAtDesc(Long memberId);
}
