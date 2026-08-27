package com.izacare.repository;

import com.izacare.domain.Notice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NoticeRepository extends JpaRepository<Notice, Long> {
    List<Notice> findByStoreIdOrderByEventDateAsc(Long storeId);
}
