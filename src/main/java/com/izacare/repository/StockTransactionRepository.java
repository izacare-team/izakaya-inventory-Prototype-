package com.izacare.repository;

import com.izacare.domain.StockTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StockTransactionRepository extends JpaRepository<StockTransaction, Long> {
    List<StockTransaction> findByItemIdOrderByCreatedAtDesc(Long itemId);
    List<StockTransaction> findTop50ByStoreIdOrderByCreatedAtDesc(Long storeId);
}
