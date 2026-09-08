package com.izacare.web;

import com.izacare.domain.Course;
import com.izacare.domain.DiningTable;
import com.izacare.domain.Member;
import com.izacare.domain.Reservation;
import com.izacare.domain.Store;
import com.izacare.repository.CourseRepository;
import com.izacare.repository.DiningTableRepository;
import com.izacare.repository.ReservationRepository;
import com.izacare.repository.StoreRepository;
import com.izacare.service.StoreCodeGenerator;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalTime;
import java.util.List;

/** 가게 설정 (사장님 전용) — 가게이름·영업시간, 테이블 구성, 코드 재발급 */
@RestController
@RequestMapping("/api/store")
@Transactional
public class StoreSettingController {

    private final StoreRepository storeRepository;
    private final DiningTableRepository tableRepository;
    private final ReservationRepository reservationRepository;
    private final CourseRepository courseRepository;
    private final StoreCodeGenerator codeGenerator;
    private final ClientIpResolver clientIpResolver;

    public StoreSettingController(StoreRepository storeRepository,
                                  DiningTableRepository tableRepository,
                                  ReservationRepository reservationRepository,
                                  CourseRepository courseRepository,
                                  StoreCodeGenerator codeGenerator,
                                  ClientIpResolver clientIpResolver) {
        this.storeRepository = storeRepository;
        this.tableRepository = tableRepository;
        this.reservationRepository = reservationRepository;
        this.courseRepository = courseRepository;
        this.codeGenerator = codeGenerator;
        this.clientIpResolver = clientIpResolver;
    }

    private Member me(HttpServletRequest request) {
        return (Member) request.getAttribute("loginMember");
    }
    private void requireOwner(HttpServletRequest request) {
        if (!me(request).isOwner()) throw new IllegalStateException("사장님만 사용할 수 있는 기능입니다.");
    }
    private Store myStore(HttpServletRequest request) {
        return storeRepository.findById(me(request).getStoreId())
                .orElseThrow(() -> new IllegalStateException("가게 정보를 찾을 수 없습니다."));
    }

    public record StoreInfo(String name, String code, LocalTime open, LocalTime close,
                            List<TableInfo> tables, List<CourseInfo> courses,
                            AttendanceLocation attendanceLocation) {}
    /** 출근 위치 확인 설정 (사장님에게만 내려간다) */
    public record AttendanceLocation(Double latitude, Double longitude,
                                     int radius, String allowedIp) {}
    public record LocationUpdate(@NotNull Double latitude, @NotNull Double longitude,
                                 Integer radius) {}
    public record TableInfo(Long id, int number, int capacity) {}
    public record CourseInfo(Long id, String name, Integer durationMinutes, boolean unlimitedRefill) {}
    public record StoreUpdate(@NotBlank String name, String open, String close) {}
    public record TableAddRequest(@Min(1) int number, @Min(1) int capacity) {}
    public record CourseAddRequest(@NotBlank String name, Integer durationMinutes, Boolean unlimitedRefill) {}

    /** 내 가게 설정 조회 (코드는 사장님에게만) */
    @GetMapping
    @Transactional(readOnly = true)
    public StoreInfo info(HttpServletRequest request) {
        Store s = myStore(request);
        List<TableInfo> tables = tableRepository.findByStoreIdAndActiveTrueOrderByTableNumberAsc(s.getId()).stream()
                .map(t -> new TableInfo(t.getId(), t.getTableNumber(), t.getCapacity())).toList();
        List<CourseInfo> courses = courseRepository.findByStoreIdOrderByIdAsc(s.getId()).stream()
                .map(c -> new CourseInfo(c.getId(), c.getName(), c.getDurationMinutes(), c.isUnlimitedRefill()))
                .toList();
        boolean owner = me(request).isOwner();
        String code = owner ? s.getCode() : null;
        AttendanceLocation location = owner
                ? new AttendanceLocation(s.getLatitude(), s.getLongitude(),
                                         s.getAttendanceRadius(), s.getAllowedIp())
                : null;
        return new StoreInfo(s.getName(), code, s.getBusinessOpen(), s.getBusinessClose(),
                tables, courses, location);
    }

    @PatchMapping
    public StoreInfo update(@Valid @RequestBody StoreUpdate req, HttpServletRequest request) {
        requireOwner(request);
        Store s = myStore(request);
        s.rename(req.name().trim());
        s.setBusinessHours(parse(req.open()), parse(req.close()));
        return info(request);
    }

    /**
     * 테이블 추가. 같은 번호를 예전에 치운 적이 있으면 그 행을 새 정원으로 되살린다 —
     * (store_id, tableNumber) 유니크 제약 때문에 새 행을 넣을 수 없기도 하고,
     * 그렇게 해야 그 번호를 쓰던 지난 예약 기록도 그대로 이어진다.
     */
    @PostMapping("/tables")
    @ResponseStatus(HttpStatus.CREATED)
    public StoreInfo addTable(@Valid @RequestBody TableAddRequest req, HttpServletRequest request) {
        requireOwner(request);
        Long storeId = me(request).getStoreId();
        DiningTable existing = tableRepository
                .findByStoreIdAndTableNumber(storeId, req.number()).orElse(null);

        if (existing != null) {
            if (existing.isActive()) {
                throw new IllegalArgumentException(req.number() + "번 테이블이 이미 있습니다.");
            }
            existing.reactivate(req.capacity());
        } else {
            tableRepository.save(new DiningTable(storeId, req.number(), req.capacity()));
        }
        return info(request);
    }

    /**
     * 테이블 삭제.
     * 사용중인 예약이 걸려 있으면 거부하고, 지난 예약 기록만 있으면 목록에서 감춘다.
     * 예약 기록이 아예 없을 때만 행을 지운다.
     */
    @DeleteMapping("/tables/{id}")
    public StoreInfo removeTable(@PathVariable Long id, HttpServletRequest request) {
        requireOwner(request);
        DiningTable t = tableRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("테이블을 찾을 수 없습니다."));
        Long storeId = me(request).getStoreId();
        if (!t.getStoreId().equals(storeId)) {
            throw new IllegalArgumentException("우리 가게 테이블이 아닙니다.");
        }
        if (reservationRepository.existsByStoreIdAndStatusAndTables_Id(
                storeId, Reservation.Status.ACTIVE, t.getId())) {
            throw new IllegalStateException(t.getTableNumber()
                    + "번 테이블은 사용중인 예약이 있어 삭제할 수 없습니다. 공석 처리 후 다시 시도해 주세요.");
        }
        if (reservationRepository.existsByTables_Id(t.getId())) {
            t.deactivate();   // 지난 예약이 참조하므로 행은 남기고 목록에서만 감춘다
        } else {
            tableRepository.delete(t);
        }
        return info(request);
    }

    @PostMapping("/courses")
    @ResponseStatus(HttpStatus.CREATED)
    public StoreInfo addCourse(@Valid @RequestBody CourseAddRequest req, HttpServletRequest request) {
        requireOwner(request);
        Long storeId = me(request).getStoreId();
        String name = req.name().trim();
        if (courseRepository.existsByStoreIdAndName(storeId, name)) {
            throw new IllegalArgumentException("이미 있는 코스입니다: " + name);
        }
        Integer duration = req.durationMinutes() == null || req.durationMinutes() <= 0 ? null : req.durationMinutes();
        boolean refill = Boolean.TRUE.equals(req.unlimitedRefill());
        courseRepository.save(new Course(storeId, name, duration, refill));
        return info(request);
    }

    @DeleteMapping("/courses/{id}")
    public StoreInfo removeCourse(@PathVariable Long id, HttpServletRequest request) {
        requireOwner(request);
        Course c = courseRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("코스를 찾을 수 없습니다."));
        if (!c.getStoreId().equals(me(request).getStoreId())) {
            throw new IllegalArgumentException("우리 가게 코스가 아닙니다.");
        }
        courseRepository.delete(c);
        return info(request);
    }

    /** 가게 코드 재발급 — 유출 시 이전 코드를 무효화한다 */
    // ===== 출근 위치 확인 설정 =====

    /** 사장님이 매장에서 "현재 위치로 설정"을 눌렀을 때 — 그 좌표가 매장 기준점이 된다 */
    @PatchMapping("/attendance-location")
    public StoreInfo setAttendanceLocation(@Valid @RequestBody LocationUpdate req,
                                           HttpServletRequest request) {
        requireOwner(request);
        myStore(request).setAttendanceLocation(req.latitude(), req.longitude(), req.radius());
        return info(request);
    }

    /** 매장 Wi-Fi에서 이 버튼을 눌러야 한다 — 지금 접속한 공인 IP를 매장 IP로 등록한다 */
    @PostMapping("/attendance-ip")
    public StoreInfo setAttendanceIp(HttpServletRequest request) {
        requireOwner(request);
        myStore(request).setAllowedIp(clientIpResolver.resolve(request));
        return info(request);
    }

    @DeleteMapping("/attendance-ip")
    public StoreInfo clearAttendanceIp(HttpServletRequest request) {
        requireOwner(request);
        myStore(request).setAllowedIp(null);
        return info(request);
    }

    @PostMapping("/regenerate-code")
    public StoreInfo regenerateCode(HttpServletRequest request) {
        requireOwner(request);
        Store s = myStore(request);
        s.changeCode(codeGenerator.generateUnique());
        return info(request);
    }

    private LocalTime parse(String hhmm) {
        return (hhmm == null || hhmm.isBlank()) ? null : LocalTime.parse(hhmm);
    }
}
