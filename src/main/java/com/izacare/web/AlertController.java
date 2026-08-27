package com.izacare.web;

import com.izacare.domain.FoodItem;
import com.izacare.domain.Member;
import com.izacare.domain.Notification;
import com.izacare.repository.FoodItemRepository;
import com.izacare.service.NotificationService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 상단 알림(종 아이콘) 통합 엔드포인트.
 * - LOW_STOCK   : 재고에서 실시간 파생 (재고가 채워지면 자동으로 사라짐)
 * - RESERVATION : 새 예약 등록 시 저장된 알림
 * - SCHEDULE    : 근무 스케줄 등록 시 저장된 알림
 */
@RestController
@RequestMapping("/api/alerts")
@Transactional
public class AlertController {

    private final FoodItemRepository itemRepository;
    private final NotificationService notificationService;

    public AlertController(FoodItemRepository itemRepository,
                           NotificationService notificationService) {
        this.itemRepository = itemRepository;
        this.notificationService = notificationService;
    }

    /**
     * @param route 알림을 눌렀을 때 이동할 화면 (프런트 해시 라우트)
     * @param arg   화면에 넘길 값 (예: 재고 부족이면 해당 품목 id)
     */
    public record AlertItem(Long id, String type, String message, LocalDateTime createdAt,
                            String route, String arg) {}

    /** 알림 종류별 이동 대상 — 재고 부족만 품목 id가 붙는다 (대시보드 상세에서도 재사용) */
    public static String routeOf(Notification.Type type) {
        return switch (type) {
            case RESERVATION -> "reserve-list";
            case SCHEDULE -> "calendar";
            case SIGNUP, MEMBER -> "staff";
            case NOTICE -> "home";
            case REPORT -> "reports";
            case DISPOSE -> "stock";
        };
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<AlertItem> alerts(HttpServletRequest request) {
        Member me = (Member) request.getAttribute("loginMember");
        List<AlertItem> result = new ArrayList<>();

        // 1) 재고 부족 (파생)
        itemRepository.findByStoreId(me.getStoreId()).stream()
                .filter(FoodItem::isLowStock)
                .forEach(i -> result.add(new AlertItem(null, "LOW_STOCK",
                        i.getName() + " " + i.getQuantity() + (i.getUnit() == null ? "개" : i.getUnit())
                                + " (최소 " + i.getMinQuantity() + ")", null,
                        "inbound", String.valueOf(i.getId()))));

        // 2) 저장된 이벤트 알림 (예약 / 스케줄)
        for (Notification n : notificationService.unread(me.getId())) {
            // 사장님 전용 알림이 잘못 쌓였더라도 알바생 화면에는 노출하지 않는다
            if (n.getType().isOwnerOnly() && !me.isOwner()) continue;
            result.add(new AlertItem(n.getId(), n.getType().name(), n.getMessage(), n.getCreatedAt(),
                    routeOf(n.getType()), null));
        }
        return result;
    }

    /**
     * 사장님 전용 알림 열람 — 알바생이 접근하면 403.
     * 화면에서 숨기는 것과 별개로 서버에서도 막는다.
     */
    @GetMapping("/owner")
    @Transactional(readOnly = true)
    public List<AlertItem> ownerAlerts(HttpServletRequest request) {
        Member me = (Member) request.getAttribute("loginMember");
        if (!me.isOwner()) throw new AccessDeniedException();

        return notificationService.unread(me.getId()).stream()
                .filter(n -> n.getType().isOwnerOnly())
                .map(n -> new AlertItem(n.getId(), n.getType().name(), n.getMessage(),
                        n.getCreatedAt(), routeOf(n.getType()), null))
                .toList();
    }

    /** 알림 패널을 확인했을 때 — 이벤트 알림만 읽음 처리 (재고 부족은 재고가 채워져야 사라짐) */
    @PostMapping("/read-all")
    public Map<String, String> readAll(HttpServletRequest request) {
        Member me = (Member) request.getAttribute("loginMember");
        notificationService.markAllRead(me.getId());
        return Map.of("message", "읽음 처리했습니다.");
    }
}
