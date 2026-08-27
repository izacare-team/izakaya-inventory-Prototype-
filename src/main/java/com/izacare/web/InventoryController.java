package com.izacare.web;

import com.izacare.dto.Dtos.*;
import com.izacare.service.InventoryService;
import com.izacare.domain.Member;
import com.izacare.domain.Notification;
import com.izacare.service.NotificationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class InventoryController {

    private final InventoryService inventoryService;
    private final NotificationService notificationService;

    public InventoryController(InventoryService inventoryService,
                               NotificationService notificationService) {
        this.inventoryService = inventoryService;
        this.notificationService = notificationService;
    }

    private Member me(HttpServletRequest request) {
        return (Member) request.getAttribute("loginMember");
    }
    private Long storeId(HttpServletRequest request) {
        return me(request).getStoreId();
    }

    // ----- 재고 목록 화면 -----

    @GetMapping("/items")
    public List<ItemResponse> items(HttpServletRequest request) {
        return inventoryService.findAllItems(storeId(request));
    }

    @GetMapping("/items/{id}")
    public ItemResponse item(@PathVariable Long id, HttpServletRequest request) {
        return inventoryService.findItem(storeId(request), id);
    }

    @PostMapping("/items")
    @ResponseStatus(HttpStatus.CREATED)
    public ItemResponse createItem(@Valid @RequestBody CreateItemRequest req,
                                   HttpServletRequest request) {
        return inventoryService.createItem(storeId(request), req);
    }

    @GetMapping("/items/{id}/transactions")
    public List<TransactionResponse> itemTransactions(@PathVariable Long id,
                                                      HttpServletRequest request) {
        return inventoryService.itemTransactions(storeId(request), id);
    }

    /** 재고 목록에서 수량 / 재고 부족 기준 직접 수정 — 입고·폐기가 아닌 단순 정정 */
    @PatchMapping("/items/{id}/quantity")
    public ItemResponse adjustQuantity(@PathVariable Long id,
                                       @Valid @RequestBody AdjustQuantityRequest req,
                                       HttpServletRequest request) {
        return inventoryService.adjustQuantity(storeId(request), id, req);
    }

    // ----- 입고 화면 -----

    @PostMapping("/inbound")
    @ResponseStatus(HttpStatus.CREATED)
    public TransactionResponse inbound(@Valid @RequestBody InboundRequest req,
                                       HttpServletRequest request) {
        return inventoryService.inbound(storeId(request), req);
    }

    // ----- 폐기 화면 -----

    @PostMapping("/dispose")
    @ResponseStatus(HttpStatus.CREATED)
    public TransactionResponse dispose(@Valid @RequestBody DisposeRequest req,
                                       HttpServletRequest request) {
        Member me = me(request);
        TransactionResponse tx = inventoryService.dispose(me.getStoreId(), req);

        // 폐기는 손실이므로 같은 가게 전 직원에게 공유 (입고는 일상적이라 알림 제외)
        notificationService.notifyAll(me.getStoreId(), Notification.Type.DISPOSE,
                "폐기 · " + tx.itemName() + " " + Math.abs(tx.quantityChange())
                        + " (" + tx.note() + ") → 남은 재고 " + tx.quantityAfter(), me);

        return tx;
    }

    // ----- 공통: 최근 이력 -----

    @GetMapping("/transactions")
    public List<TransactionResponse> transactions(HttpServletRequest request) {
        return inventoryService.recentTransactions(storeId(request));
    }
}
