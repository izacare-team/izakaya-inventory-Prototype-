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

    /**
     * 사용중인 테이블인지. 예약 기록이 걸린 테이블은 지워도 행을 남기고 이 값만 내린다 —
     * 물리 삭제하면 reservation_tables 외래키에 막혀 아예 지워지지 않고,
     * 지난 예약이 어느 테이블이었는지도 잃어버리기 때문.
     */
    // 파일 DB에 이미 행이 있어서 기본값 없이 NOT NULL 컬럼을 붙이면 ddl-auto=update가 실패한다
    @Column(nullable = false, columnDefinition = "boolean default true")
    private boolean active = true;

    protected DiningTable() {}

    public DiningTable(Long storeId, int tableNumber, int capacity) {
        this.storeId = storeId;
        this.tableNumber = tableNumber;
        this.capacity = capacity;
    }

    public void changeCapacity(int capacity) { this.capacity = capacity; }

    /** 목록·좌석 맵에서 감춘다. 지난 예약이 참조하는 행 자체는 남는다 */
    public void deactivate() { this.active = false; }

    /** 같은 번호로 다시 등록할 때 — 감춰 둔 행을 새 정원으로 되살린다 */
    public void reactivate(int capacity) {
        this.active = true;
        this.capacity = capacity;
    }

    public Long getId() { return id; }
    public Long getStoreId() { return storeId; }
    public int getTableNumber() { return tableNumber; }
    public int getCapacity() { return capacity; }
    public boolean isActive() { return active; }
}
