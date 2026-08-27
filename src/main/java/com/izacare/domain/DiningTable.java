package com.izacare.domain;

import jakarta.persistence.*;

/**
 * 매장 테이블. 가게마다 자기 테이블을 가지므로 PK는 자동 생성하고,
 * 가게 안에서의 표시 번호(tableNumber)는 별도로 둔다. (예: A가게 1번, B가게 1번이 공존)
 */
@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"store_id", "tableNumber"}))
public class DiningTable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 소속 가게 */
    @Column(nullable = false)
    private Long storeId;

    /** 가게 안에서의 테이블 번호 (1..N) */
    @Column(nullable = false)
    private int tableNumber;

    private int capacity;

    protected DiningTable() {}

    public DiningTable(Long storeId, int tableNumber, int capacity) {
        this.storeId = storeId;
        this.tableNumber = tableNumber;
        this.capacity = capacity;
    }

    public void changeCapacity(int capacity) { this.capacity = capacity; }

    public Long getId() { return id; }
    public Long getStoreId() { return storeId; }
    public int getTableNumber() { return tableNumber; }
    public int getCapacity() { return capacity; }
}
