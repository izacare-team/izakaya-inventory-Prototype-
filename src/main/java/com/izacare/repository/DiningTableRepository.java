package com.izacare.repository;

import com.izacare.domain.DiningTable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DiningTableRepository extends JpaRepository<DiningTable, Long> {
    List<DiningTable> findByStoreIdOrderByTableNumberAsc(Long storeId);
    boolean existsByStoreIdAndTableNumber(Long storeId, int tableNumber);
}
