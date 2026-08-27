package com.izacare.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** 재고 변동 이력 (입고 / 폐기 / 실사 조정) */
@Entity
public class StockTransaction {

    public enum Type { INBOUND, DISPOSE, AUDIT_ADJUST, MANUAL_ADJUST }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 소속 가게 (품목에서 파생) */
    @Column(nullable = false)
    private Long storeId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private FoodItem item;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Type type;

    /** 변동량 (입고 +, 폐기 -, 실사조정은 보정치 그대로) */
    private int quantityChange;

    /** 변동 후 수량 스냅샷 */
    private int quantityAfter;

    /** 사유 (폐기 사유, 실사 메모 등) */
    private String note;

    private LocalDateTime createdAt;

    protected StockTransaction() {}

    public StockTransaction(FoodItem item, Type type, int quantityChange, int quantityAfter, String note) {
        this.item = item;
        this.storeId = item.getStoreId();
        this.type = type;
        this.quantityChange = quantityChange;
        this.quantityAfter = quantityAfter;
        this.note = note;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Long getStoreId() { return storeId; }
    public FoodItem getItem() { return item; }
    public Type getType() { return type; }
    public int getQuantityChange() { return quantityChange; }
    public int getQuantityAfter() { return quantityAfter; }
    public String getNote() { return note; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
