package com.izacare.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** 재고 품목 (예: 생맥주, 사케, 닭꼬치용 닭고기 ...) */
@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"store_id", "name"}))
public class FoodItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 소속 가게 */
    @Column(nullable = false)
    private Long storeId;

    @Column(nullable = false)
    private String name;

    /** 분류 (주류 / 육류 / 채소 / 소스 등) */
    private String category;

    /** 단위 (병, kg, 개 ...) */
    private String unit;

    /** 현재 시스템상 재고 수량 */
    @Column(nullable = false)
    private int quantity;

    /** 이 수량 이하로 떨어지면 '재고 부족' 알림 */
    private int minQuantity;

    /** 마지막으로 실사(사진 인식 포함)로 확인된 시각 */
    private LocalDateTime lastAuditedAt;

    protected FoodItem() {}

    public FoodItem(Long storeId, String name, String category, String unit, int quantity, int minQuantity) {
        this.storeId = storeId;
        this.name = name;
        this.category = category;
        this.unit = unit;
        this.quantity = quantity;
        this.minQuantity = minQuantity;
    }

    public void addQuantity(int amount) {
        this.quantity += amount;
    }

    public void removeQuantity(int amount) {
        if (amount > this.quantity) {
            throw new IllegalArgumentException(
                    "재고보다 많은 수량을 차감할 수 없습니다. (현재: " + this.quantity + ", 요청: " + amount + ")");
        }
        this.quantity -= amount;
    }

    /** 실사 확정 시 실물 수량으로 덮어쓰기 */
    public void adjustTo(int actualQuantity) {
        this.quantity = actualQuantity;
        this.lastAuditedAt = LocalDateTime.now();
    }

    /**
     * 재고 목록에서 수량을 직접 고칠 때 사용.
     * 실사가 아니므로 lastAuditedAt(마지막 실사 시각)은 건드리지 않는다.
     */
    public void correctQuantityTo(int newQuantity) {
        if (newQuantity < 0) throw new IllegalArgumentException("재고 수량은 0보다 작을 수 없습니다.");
        this.quantity = newQuantity;
    }

    /** 재고 부족 판정 기준 변경 — 이 값 이하로 떨어지면 빨간색/알림 */
    public void changeMinQuantity(int newMinQuantity) {
        if (newMinQuantity < 0) throw new IllegalArgumentException("최소 재고는 0보다 작을 수 없습니다.");
        this.minQuantity = newMinQuantity;
    }

    public boolean isLowStock() {
        return quantity <= minQuantity;
    }

    public Long getId() { return id; }
    public Long getStoreId() { return storeId; }
    public String getName() { return name; }
    public String getCategory() { return category; }
    public String getUnit() { return unit; }
    public int getQuantity() { return quantity; }
    public int getMinQuantity() { return minQuantity; }
    public LocalDateTime getLastAuditedAt() { return lastAuditedAt; }
}
