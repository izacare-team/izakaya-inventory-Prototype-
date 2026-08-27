package com.izacare.web;

import com.izacare.domain.Course;
import com.izacare.domain.DiningTable;
import com.izacare.domain.Member;
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

    public StoreSettingController(StoreRepository storeRepository,
                                  DiningTableRepository tableRepository,
                                  ReservationRepository reservationRepository,
                                  CourseRepository courseRepository,
                                  StoreCodeGenerator codeGenerator) {
        this.storeRepository = storeRepository;
        this.tableRepository = tableRepository;
        this.reservationRepository = reservationRepository;
        this.courseRepository = courseRepository;
        this.codeGenerator = codeGenerator;
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
                            List<TableInfo> tables, List<CourseInfo> courses) {}
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
        List<TableInfo> tables = tableRepository.findByStoreIdOrderByTableNumberAsc(s.getId()).stream()
                .map(t -> new TableInfo(t.getId(), t.getTableNumber(), t.getCapacity())).toList();
        List<CourseInfo> courses = courseRepository.findByStoreIdOrderByIdAsc(s.getId()).stream()
                .map(c -> new CourseInfo(c.getId(), c.getName(), c.getDurationMinutes(), c.isUnlimitedRefill()))
                .toList();
        String code = me(request).isOwner() ? s.getCode() : null;
        return new StoreInfo(s.getName(), code, s.getBusinessOpen(), s.getBusinessClose(), tables, courses);
    }

    @PatchMapping
    public StoreInfo update(@Valid @RequestBody StoreUpdate req, HttpServletRequest request) {
        requireOwner(request);
        Store s = myStore(request);
        s.rename(req.name().trim());
        s.setBusinessHours(parse(req.open()), parse(req.close()));
        return info(request);
    }

    @PostMapping("/tables")
    @ResponseStatus(HttpStatus.CREATED)
    public StoreInfo addTable(@Valid @RequestBody TableAddRequest req, HttpServletRequest request) {
        requireOwner(request);
        Long storeId = me(request).getStoreId();
        if (tableRepository.existsByStoreIdAndTableNumber(storeId, req.number())) {
            throw new IllegalArgumentException(req.number() + "번 테이블이 이미 있습니다.");
        }
        tableRepository.save(new DiningTable(storeId, req.number(), req.capacity()));
        return info(request);
    }

    @DeleteMapping("/tables/{id}")
    public StoreInfo removeTable(@PathVariable Long id, HttpServletRequest request) {
        requireOwner(request);
        DiningTable t = tableRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("테이블을 찾을 수 없습니다."));
        if (!t.getStoreId().equals(me(request).getStoreId())) {
            throw new IllegalArgumentException("우리 가게 테이블이 아닙니다.");
        }
        tableRepository.delete(t);
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
