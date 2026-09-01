package com.izacare.web;

import com.izacare.domain.Member;
import com.izacare.domain.Notification;
import com.izacare.domain.WorkSchedule;
import com.izacare.repository.MemberRepository;
import com.izacare.repository.WorkScheduleRepository;
import com.izacare.service.NotificationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/** 알바 근무 스케줄 — 사장님이 등록하면 해당 직원에게 알림이 간다 */
@RestController
@RequestMapping("/api/schedules")
@Transactional
public class ScheduleController {

    private final WorkScheduleRepository scheduleRepository;
    private final MemberRepository memberRepository;
    private final NotificationService notificationService;

    public ScheduleController(WorkScheduleRepository scheduleRepository,
                              MemberRepository memberRepository,
                              NotificationService notificationService) {
        this.scheduleRepository = scheduleRepository;
        this.memberRepository = memberRepository;
        this.notificationService = notificationService;
    }

    public record ScheduleRequest(@NotNull Long memberId, @NotNull LocalDate workDate,
                                  String startTime, String endTime, String memo) {}
    public record ScheduleResponse(Long id, Long memberId, String memberName,
                                   LocalDate workDate, LocalTime startTime,
                                   LocalTime endTime, String memo) {
        static ScheduleResponse from(WorkSchedule s) {
            return new ScheduleResponse(s.getId(), s.getMember().getId(),
                    s.getMember().getDisplayName(), s.getWorkDate(),
                    s.getStartTime(), s.getEndTime(), s.getMemo());
        }
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<ScheduleResponse> byDate(@RequestParam LocalDate date, HttpServletRequest request) {
        Member me = (Member) request.getAttribute("loginMember");
        return scheduleRepository.findByStoreIdAndWorkDateOrderByStartTimeAsc(me.getStoreId(), date)
                .stream()
                .filter(s -> me.isOwner() || s.getMember().getId().equals(me.getId()))
                .map(ScheduleResponse::from).toList();
    }

    /** 달력 표시용 월 단위 조회 */
    @GetMapping("/month")
    @Transactional(readOnly = true)
    public List<ScheduleResponse> byMonth(@RequestParam int year, @RequestParam int month,
                                          HttpServletRequest request) {
        Member me = (Member) request.getAttribute("loginMember");
        LocalDate start = LocalDate.of(year, month, 1);
        LocalDate end = start.plusMonths(1).minusDays(1);
        return scheduleRepository.findByStoreIdAndWorkDateBetweenOrderByWorkDateAscStartTimeAsc(
                        me.getStoreId(), start, end)
                .stream()
                .filter(s -> me.isOwner() || s.getMember().getId().equals(me.getId()))
                .map(ScheduleResponse::from).toList();
    }

    /** 스케줄 등록 (사장님 전용) — 등록되면 해당 직원에게 알림 발송 */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ScheduleResponse create(@Valid @RequestBody ScheduleRequest req,
                                   HttpServletRequest request) {
        Member me = (Member) request.getAttribute("loginMember");
        if (!me.isOwner()) throw new IllegalStateException("사장님만 스케줄을 등록할 수 있습니다.");

        Member target = memberRepository.findById(req.memberId())
                .orElseThrow(() -> new IllegalArgumentException("직원을 찾을 수 없습니다: " + req.memberId()));
        if (!target.getStoreId().equals(me.getStoreId())) {
            throw new IllegalArgumentException("우리 가게 직원이 아닙니다.");
        }

        LocalTime start = parse(req.startTime());
        LocalTime end = parse(req.endTime());

        WorkSchedule saved = scheduleRepository.save(
                new WorkSchedule(target, req.workDate(), start, end, req.memo()));

        notificationService.notifyMember(target, Notification.Type.SCHEDULE,
                "근무 스케줄 등록 · " + req.workDate()
                        + (start != null ? " " + start + (end != null ? "~" + end : "") : "")
                        + (req.memo() == null || req.memo().isBlank() ? "" : " (" + req.memo() + ")"));

        return ScheduleResponse.from(saved);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id, HttpServletRequest request) {
        Member me = (Member) request.getAttribute("loginMember");
        if (!me.isOwner()) throw new IllegalStateException("사장님만 스케줄을 삭제할 수 있습니다.");
        scheduleRepository.findById(id)
                .filter(s -> s.getStoreId().equals(me.getStoreId()))
                .ifPresent(scheduleRepository::delete);
    }

    private LocalTime parse(String hhmm) {
        return (hhmm == null || hhmm.isBlank()) ? null : LocalTime.parse(hhmm);
    }
}
