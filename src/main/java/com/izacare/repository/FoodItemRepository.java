package com.izacare.repository;

import com.izacare.domain.FoodItem;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FoodItemRepository extends JpaRepository<FoodItem, Long> {
    List<FoodItem> findByStoreId(Long storeId);
    Optional<FoodItem> findByStoreIdAndName(Long storeId, String name);
    boolean existsByStoreIdAndName(Long storeId, String name);

    /**
     * 재고 증감(입고/폐기/수동조정)처럼 동시에 같은 품목에 쓰기 요청이 몰릴 수 있는 작업 전용.
     * 비관적 쓰기 락을 걸어, 두 트랜잭션이 동시에 같은 품목을 읽고 각자 수량을 갱신해
     * 한쪽 변경이 덮어써지는 현상(lost update)을 막는다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from FoodItem f where f.id = :id")
    Optional<FoodItem> findByIdForUpdate(@Param("id") Long id);
}
