package com.izacare.web;

import com.izacare.domain.FoodItem;
import com.izacare.domain.Member;
import com.izacare.domain.Reservation;
import com.izacare.repository.AttendanceRepository;
import com.izacare.repository.FoodItemRepository;
import com.izacare.repository.NotificationRepository;
import com.izacare.repository.ReservationRepository;
import com.izacare.repository.StoreRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** 사장님 홈 대시보드 — 오늘 예약/근무/재고부족/미확인 알림 요약 (모두 내 가게 범위) */
@RestController
@RequestMapping("/api/dashboard")
@Transactional(readOnly = true)
public class DashboardController {

    private final ReservationRepository reservationRepository;
    private final AttendanceRepository attendanceRepository;
    private final FoodItemRepository itemRepository;
    private final NotificationRepository notificationRepository;
    private final StoreRepository storeRepository;

    public DashboardController(ReservationRepository reservationRepository,
                              AttendanceRepository attendanceRepository,
                              FoodItemRepository itemRepository,
                              NotificationRepository notificationRepository,
                              StoreRepository storeRepository) {
        this.reservationRepository = reservationRepository;
        this.attendanceRepository = attendanceRepository;
        this.itemRepository = itemRepository;
        this.notificationRepository = notificationRepository;
        this.storeRepository = storeRepository;
    }

    /**
     * 대시보드가 보여줄 "지금 돌아가는 영업일".
     * 자정을 넘겨 영업하면 새벽 1시에도 어제 저녁 예약과 근무자를 봐야 하므로 달력 날짜를 쓰지 않는다.
     */
    private LocalDate businessToday(Long storeId) {
        return storeRepository.findById(storeId)
                .map(s -> s.businessDayOf(LocalDateTime.now()))
                .orElseGet(LocalDate::now);
    }

    public record Dashboard(long todayReservations, long onDutyCount,
                            long lowStockCount, long unreadAlerts) {}

    /**
     * 상세 목록의 한 줄.
     * @param route 눌렀을 때 이동할 화면 (없으면 null — 그냥 읽기만)
     * @param arg   화면에 넘길 값 (예약 id, 품목 id 등)
     */
    public record DetailRow(String text, String route, String arg) {
        static DetailRow plain(String text) { return new DetailRow(text, null, null); }
    }

    /** 대시보드 숫자를 눌렀을 때 보여줄 상세 내역 */
    public record DashboardDetails(List<DetailRow> todayReservations, List<DetailRow> onDuty,
                                   List<DetailRow> lowStock, List<DetailRow> unreadAlerts) {}

    @GetMapping
    public Dashboard summary(HttpServletRequest request) {
        Member me = (Member) request.getAttribute("loginMember");
        if (!me.isOwner()) throw new AccessDeniedException();
        Long storeId = me.getStoreId();
        LocalDate today = businessToday(storeId);

        long todayReservations = reservationRepository
                .findByStoreIdAndReserveDateOrderByTimeSlotAsc(storeId, today).size();

        // 오늘 출근(clock-in) 기록이 있는 인원
        long onDuty = attendanceRepository.findByStoreIdAndWorkDate(storeId, today).stream()
                .filter(a -> a.getClockIn() != null && a.getClockOut() == null)
                .count();

        long lowStock = itemRepository.findByStoreId(storeId).stream()
                .filter(FoodItem::isLowStock).count();

        long unread = notificationRepository
                .findByRecipientIdAndReadFlagFalseOrderByCreatedAtDesc(me.getId()).size();

        return new Dashboard(todayReservations, onDuty, lowStock, unread);
    }

    /** 각 항목의 실제 내용 — 홈 대시보드에서 숫자를 누르면 펼쳐진다 */
    @GetMapping("/details")
    public DashboardDetails details(HttpServletRequest request) {
        Member me = (Member) request.getAttribute("loginMember");
        if (!me.isOwner()) throw new AccessDeniedException();
        Long storeId = me.getStoreId();
        LocalDate today = businessToday(storeId);
        DateTimeFormatter hhmm = DateTimeFormatter.ofPattern("HH:mm");

        List<DetailRow> reservations = reservationRepository
                .findByStoreIdAndReserveDateOrderByTimeSlotAsc(storeId, today).stream()
                .map(r -> new DetailRow(
                        r.getTimeSlot() + " · " + r.getPeople() + "명"
                                + " · 테이블 " + r.tableNumbers().stream().map(String::valueOf)
                                                    .reduce((a, b) -> a + ", " + b).orElse("-") + "번"
                                + (r.getCustomerName() == null || r.getCustomerName().isBlank()
                                   ? "" : " · " + r.getCustomerName())
                                + (r.getCourseName() == null ? "" : " · " + r.getCourseName()),
                        "reservation", String.valueOf(r.getId())))
                .toList();

        List<DetailRow> onDuty = attendanceRepository.findByStoreIdAndWorkDate(storeId, today).stream()
                .filter(a -> a.getClockIn() != null && a.getClockOut() == null)
                .map(a -> new DetailRow(
                        a.getStaffName() + " · " + a.getClockIn().format(hhmm) + " 출근"
                                + (a.getBreakAt() != null && a.getBreakEnd() == null ? " (휴게 중)" : "")
                                // 매장 밖/위치 미확인 출근은 사장님이 바로 알아볼 수 있게 붙인다
                                + (a.isLocationSuspicious()
                                   ? " · " + StoreController.locationLabel(a) : ""),
                        "staff", null))
                .toList();

        List<DetailRow> lowStock = itemRepository.findByStoreId(storeId).stream()
                .filter(FoodItem::isLowStock)
                .map(i -> new DetailRow(
                        i.getName() + " · " + i.getQuantity()
                                + (i.getUnit() == null ? "개" : i.getUnit())
                                + " (최소 " + i.getMinQuantity() + ")",
                        "inbound", String.valueOf(i.getId())))
                .toList();

        List<DetailRow> alerts = notificationRepository
                .findByRecipientIdAndReadFlagFalseOrderByCreatedAtDesc(me.getId()).stream()
                .map(n -> new DetailRow(n.getMessage(),
                        AlertController.routeOf(n.getType()), null))
                .toList();

        return new DashboardDetails(reservations, onDuty, lowStock, alerts);
    }
}
