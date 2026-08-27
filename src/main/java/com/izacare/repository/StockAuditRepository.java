package com.izacare.repository;

import com.izacare.domain.StockAudit;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StockAuditRepository extends JpaRepository<StockAudit, Long> {

    @EntityGraph(attributePaths = {"lines", "lines.item"})
    Optional<StockAudit> findWithLinesById(Long id);

    List<StockAudit> findTop20ByStoreIdOrderByCreatedAtDesc(Long storeId);
}
