package com.izacare.repository;

import com.izacare.domain.DiningTable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DiningTableRepository extends JpaRepository<DiningTable, Long> {

    /** 화면에 보여줄 테이블 — 치운 테이블(active=false)은 뺀다 */
    List<DiningTable> findByStoreIdAndActiveTrueOrderByTableNumberAsc(Long storeId);

    /** 번호로 찾기 — 치운 테이블도 포함해서 찾아야 같은 번호를 되살릴 수 있다 */
    Optional<DiningTable> findByStoreIdAndTableNumber(Long storeId, int tableNumber);
}
