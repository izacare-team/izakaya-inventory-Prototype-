package com.izacare.service;

import com.izacare.domain.FoodItem;
import com.izacare.domain.StockTransaction;
import com.izacare.dto.Dtos.*;
import com.izacare.repository.FoodItemRepository;
import com.izacare.repository.StockTransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** 재고 목록 / 입고 / 폐기 — 모든 조회·저장은 storeId 범위로 한정된다. */
@Service
@Transactional
public class InventoryService {

    private final FoodItemRepository itemRepository;
    private final StockTransactionRepository transactionRepository;

    public InventoryService(FoodItemRepository itemRepository,
                            StockTransactionRepository transactionRepository) {
        this.itemRepository = itemRepository;
        this.transactionRepository = transactionRepository;
    }

    @Transactional(readOnly = true)
    public List<ItemResponse> findAllItems(Long storeId) {
        return itemRepository.findByStoreId(storeId).stream().map(ItemResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public ItemResponse findItem(Long storeId, Long id) {
        return ItemResponse.from(getItem(storeId, id));
    }

    public ItemResponse createItem(Long storeId, CreateItemRequest req) {
        if (itemRepository.existsByStoreIdAndName(storeId, req.name())) {
            throw new IllegalArgumentException("이미 등록된 품목입니다: " + req.name());
        }
        FoodItem item = new FoodItem(storeId, req.name(), req.category(), req.unit(),
                req.quantity(), req.minQuantity());
        return ItemResponse.from(itemRepository.save(item));
    }

    /** 입고: 수량 증가 + 이력 기록. itemId가 없으면 신규 품목 등록 후 입고. */
    public TransactionResponse inbound(Long storeId, InboundRequest req) {
        FoodItem item;
        boolean created = false;

        if (req.itemId() != null) {
            item = getItemForUpdate(storeId, req.itemId());
        } else {
            String name = req.newItemName() == null ? "" : req.newItemName().trim();
            if (name.isEmpty()) {
                throw new IllegalArgumentException("품목을 선택하거나 신규 품목명을 입력하세요.");
            }
            FoodItem existing = itemRepository.findByStoreIdAndName(storeId, name).orElse(null);
            if (existing != null) {
                item = existing;
            } else {
                item = itemRepository.save(new FoodItem(storeId, name, req.category(), req.unit(),
                        0, req.minQuantity()));
                created = true;
            }
        }

        item.addQuantity(req.quantity());

        String note = req.note();
        if (created) {
            note = "[신규 품목 등록] " + (note == null || note.isBlank() ? "" : note);
        }
        StockTransaction tx = new StockTransaction(item, StockTransaction.Type.INBOUND,
                req.quantity(), item.getQuantity(), note);
        return TransactionResponse.from(transactionRepository.save(tx));
    }

    /** 폐기: 수량 감소 + 사유 필수 기록 */
    public TransactionResponse dispose(Long storeId, DisposeRequest req) {
        FoodItem item = getItemForUpdate(storeId, req.itemId());
        item.removeQuantity(req.quantity());

        StockTransaction tx = new StockTransaction(item, StockTransaction.Type.DISPOSE,
                -req.quantity(), item.getQuantity(), req.reason());
        return TransactionResponse.from(transactionRepository.save(tx));
    }

    /**
     * 재고 목록에서 품목을 직접 고친다 (수량 / 재고 부족 기준).
     * 수량은 "고친 뒤 수량"이므로 현재 수량과의 차이를 변동 이력으로 남기고,
     * 최소 재고는 판정 기준이라 이력 없이 값만 바꾼다.
     */
    public ItemResponse adjustQuantity(Long storeId, Long itemId, AdjustQuantityRequest req) {
        FoodItem item = getItemForUpdate(storeId, itemId);
        int before = item.getQuantity();

        boolean qtyChanged = req.quantity() != null && req.quantity() != before;
        boolean minChanged = req.minQuantity() != null && req.minQuantity() != item.getMinQuantity();
        if (!qtyChanged && !minChanged) {
            throw new IllegalArgumentException("변경된 내용이 없습니다.");
        }

        if (minChanged) item.changeMinQuantity(req.minQuantity());

        if (qtyChanged) {
            int after = req.quantity();
            item.correctQuantityTo(after);
            String note = req.note() == null || req.note().isBlank()
                    ? "수량 수정 " + before + " → " + after
                    : req.note().trim();
            transactionRepository.save(new StockTransaction(item, StockTransaction.Type.MANUAL_ADJUST,
                    after - before, after, note));
        }
        return ItemResponse.from(item);
    }

    @Transactional(readOnly = true)
    public List<TransactionResponse> recentTransactions(Long storeId) {
        return transactionRepository.findTop50ByStoreIdOrderByCreatedAtDesc(storeId)
                .stream().map(TransactionResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<TransactionResponse> itemTransactions(Long storeId, Long itemId) {
        getItem(storeId, itemId);   // 소유 검증
        return transactionRepository.findByItemIdOrderByCreatedAtDesc(itemId)
                .stream().map(TransactionResponse::from).toList();
    }

    /** 우리 가게 소속 품목만 조회 — 다른 가게 id를 넘기면 찾을 수 없음 */
    private FoodItem getItem(Long storeId, Long id) {
        FoodItem item = itemRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("품목을 찾을 수 없습니다: id=" + id));
        if (!item.getStoreId().equals(storeId)) {
            throw new IllegalArgumentException("품목을 찾을 수 없습니다: id=" + id);
        }
        return item;
    }

    /**
     * 재고를 실제로 증감시키는 작업(입고/폐기/수동조정) 전용 조회.
     * 비관적 락으로 해당 품목 행을 잠가, 동시 요청이 들어와도 순차적으로 처리되게 한다.
     * 트랜잭션이 끝날 때까지 락이 유지되므로 @Transactional 메서드 안에서만 호출해야 한다.
     */
    private FoodItem getItemForUpdate(Long storeId, Long id) {
        FoodItem item = itemRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new IllegalArgumentException("품목을 찾을 수 없습니다: id=" + id));
        if (!item.getStoreId().equals(storeId)) {
            throw new IllegalArgumentException("품목을 찾을 수 없습니다: id=" + id);
        }
        return item;
    }
}
