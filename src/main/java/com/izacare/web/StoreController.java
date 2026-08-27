package com.izacare.web;

import com.izacare.domain.*;
import com.izacare.repository.*;
import com.izacare.service.NotificationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/** 홈(공지) / 출근 / 일보 / 예약 — 모든 조회·저장은 로그인 회원의 가게(storeId)로 한정된다. */
@RestController
@RequestMapping("/api")
@Transactional
public class StoreController {

    private final NoticeRepository noticeRepository;
    private final AttendanceRepository attendanceRepository;
    private final DailyReportRepository reportRepository;
    private final DiningTableRepository tableRepository;
    private final ReservationRepository reservationRepository;
    private final CourseRepository courseRepository;
    private final NoticeAckRepository noticeAckRepository;
    private final ReportCommentRepository commentRepository;
    private final NoticeHistoryRepository noticeHistoryRepository;
    private final ReportHistoryRepository reportHistoryRepository;
    private final CommentHistoryRepository commentHistoryRepository;
    private final NotificationService notificationService;

    public StoreController(NoticeRepository noticeRepository,
                           AttendanceRepository attendanceRepository,
                           DailyReportRepository reportRepository,
                           DiningTableRepository tableRepository,
                           ReservationRepository reservationRepository,
                           CourseRepository courseRepository,
                           NoticeAckRepository noticeAckRepository,
                           ReportCommentRepository commentRepository,
                           NoticeHistoryRepository noticeHistoryRepository,
                           ReportHistoryRepository reportHistoryRepository,
                           CommentHistoryRepository commentHistoryRepository,
                           NotificationService notificationService) {
        this.noticeRepository = noticeRepository;
        this.attendanceRepository = attendanceRepository;
        this.reportRepository = reportRepository;
        this.tableRepository = tableRepository;
        this.reservationRepository = reservationRepository;
        this.courseRepository = courseRepository;
        this.noticeAckRepository = noticeAckRepository;
        this.commentHistoryRepository = commentHistoryRepository;
        this.commentRepository = commentRepository;
        this.noticeHistoryRepository = noticeHistoryRepository;
        this.reportHistoryRepository = reportHistoryRepository;
        this.notificationService = notificationService;
    }

    private Member loginMember(HttpServletRequest request) {
        return (Member) request.getAttribute("loginMember");
    }
    private Long sid(HttpServletRequest request) {
        return loginMember(request).getStoreId();
    }

    // ================= 공지 =================

    public record NoticeRequest(@NotBlank String title, LocalDate eventDate) {}
    public record NoticeResponse(Long id, String title, LocalDate eventDate,
                                 boolean edited, LocalDateTime editedAt,
                                 int ackCount, boolean ackedByMe, List<String> ackedNames) {}
    public record NoticeHistoryResponse(Long id, String previousTitle,
                                        LocalDate previousEventDate,
                                        String editedBy, LocalDateTime editedAt) {
        static NoticeHistoryResponse from(NoticeHistory h) {
            return new NoticeHistoryResponse(h.getId(), h.getPreviousTitle(),
                    h.getPreviousEventDate(), h.getEditedBy(), h.getEditedAt());
        }
    }

    private NoticeResponse toNoticeResponse(Notice n, Member me) {
        List<NoticeAck> acks = noticeAckRepository.findByNoticeId(n.getId());
        return new NoticeResponse(n.getId(), n.getTitle(), n.getEventDate(),
                n.isEdited(), n.getEditedAt(),
                acks.size(),
                acks.stream().anyMatch(a -> a.getMember().getId().equals(me.getId())),
                acks.stream().map(a -> a.getMember().getDisplayName()).toList());
    }

    /** 내 가게 공지만 조회 — 다른 가게 id면 예외 */
    private Notice getNotice(HttpServletRequest request, Long id) {
        Notice n = noticeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("공지를 찾을 수 없습니다: " + id));
        if (!n.getStoreId().equals(sid(request))) {
            throw new IllegalArgumentException("공지를 찾을 수 없습니다: " + id);
        }
        return n;
    }

    @GetMapping("/notices")
    @Transactional(readOnly = true)
    public List<NoticeResponse> notices(HttpServletRequest request) {
        Member me = loginMember(request);
        return noticeRepository.findByStoreIdOrderByEventDateAsc(me.getStoreId())
                .stream().map(n -> toNoticeResponse(n, me)).toList();
    }

    @PostMapping("/notices")
    @ResponseStatus(HttpStatus.CREATED)
    public NoticeResponse createNotice(@Valid @RequestBody NoticeRequest req,
                                       HttpServletRequest request) {
        Member me = loginMember(request);
        Notice saved = noticeRepository.save(new Notice(me.getStoreId(), req.title(), req.eventDate()));

        String preview = req.title().replaceAll("\\s+", " ").trim();
        if (preview.length() > 40) preview = preview.substring(0, 40) + "...";
        notificationService.notifyAll(me.getStoreId(), Notification.Type.NOTICE, "새 공지 · " + preview, me);

        return toNoticeResponse(saved, me);
    }

    @PatchMapping("/notices/{id}")
    public NoticeResponse editNotice(@PathVariable Long id,
                                     @Valid @RequestBody NoticeRequest req,
                                     HttpServletRequest request) {
        Member me = loginMember(request);
        if (!me.isOwner()) throw new IllegalStateException("사장님만 공지를 수정할 수 있습니다.");
        Notice notice = getNotice(request, id);
        noticeHistoryRepository.save(new NoticeHistory(notice,
                notice.getTitle(), notice.getEventDate(), me.getDisplayName()));
        notice.edit(req.title(), req.eventDate());
        return toNoticeResponse(notice, me);
    }

    @GetMapping("/notices/{id}/history")
    @Transactional(readOnly = true)
    public List<NoticeHistoryResponse> noticeHistory(@PathVariable Long id, HttpServletRequest request) {
        getNotice(request, id);   // 소유 검증
        return noticeHistoryRepository.findByNoticeIdOrderByEditedAtDesc(id)
                .stream().map(NoticeHistoryResponse::from).toList();
    }

    @DeleteMapping("/notices/{id}")
    public void deleteNotice(@PathVariable Long id, HttpServletRequest request) {
        getNotice(request, id);   // 소유 검증
        noticeHistoryRepository.deleteByNoticeId(id);
        noticeAckRepository.deleteByNoticeId(id);
        noticeRepository.deleteById(id);
    }

    @PostMapping("/notices/{id}/ack")
    public NoticeResponse ackNotice(@PathVariable Long id, HttpServletRequest request) {
        Member me = loginMember(request);
        Notice notice = getNotice(request, id);
        if (!noticeAckRepository.existsByNoticeIdAndMemberId(id, me.getId())) {
            noticeAckRepository.save(new NoticeAck(notice, me));
        }
        return toNoticeResponse(notice, me);
    }

    // ================= 출근 =================

    public record ClockRequest(@NotBlank String action) {}
    public record AttendanceResponse(Long id, String staffName, LocalDate workDate,
                                     LocalTime clockIn, LocalTime breakAt, LocalTime breakEnd,
                                     LocalTime clockOut) {
        static AttendanceResponse from(Attendance a) {
            return new AttendanceResponse(a.getId(), a.getStaffName(), a.getWorkDate(),
                    a.getClockIn(), a.getBreakAt(), a.getBreakEnd(), a.getClockOut());
        }
    }

    @GetMapping("/attendance/today")
    @Transactional(readOnly = true)
    public AttendanceResponse todayAttendance(HttpServletRequest request) {
        Member me = loginMember(request);
        String name = me.getDisplayName();
        return attendanceRepository.findByStoreIdAndStaffNameAndWorkDate(me.getStoreId(), name, LocalDate.now())
                .map(AttendanceResponse::from)
                .orElse(new AttendanceResponse(null, name, LocalDate.now(), null, null, null, null));
    }

    @PostMapping("/attendance/clock")
    public AttendanceResponse clock(@Valid @RequestBody ClockRequest req,
                                    HttpServletRequest request) {
        Member me = loginMember(request);
        String name = me.getDisplayName();
        Attendance attendance = attendanceRepository
                .findByStoreIdAndStaffNameAndWorkDate(me.getStoreId(), name, LocalDate.now())
                .orElseGet(() -> attendanceRepository.save(
                        new Attendance(me.getStoreId(), name, LocalDate.now())));
        attendance.mark(req.action(), LocalTime.now().withSecond(0).withNano(0));
        return AttendanceResponse.from(attendance);
    }

    // ================= 일보 =================

    public record ReportRequest(@NotBlank String author, @NotNull LocalDate reportDate, String content) {}
    public record ReportEditRequest(@NotNull LocalDate reportDate,
                                    @NotBlank(message = "일보 내용을 입력해 주세요.") String content) {}
    public record ReportResponse(Long id, String author, LocalDate reportDate, String content,
                                 boolean edited, LocalDateTime editedAt) {
        static ReportResponse from(DailyReport r) {
            return new ReportResponse(r.getId(), r.getAuthor(), r.getReportDate(), r.getContent(),
                    r.isEdited(), r.getEditedAt());
        }
    }
    public record ReportHistoryResponse(Long id, String previousContent,
                                        LocalDate previousReportDate,
                                        String editedBy, LocalDateTime editedAt) {
        static ReportHistoryResponse from(ReportHistory h) {
            return new ReportHistoryResponse(h.getId(), h.getPreviousContent(),
                    h.getPreviousReportDate(), h.getEditedBy(), h.getEditedAt());
        }
    }

    private DailyReport getReport(HttpServletRequest request, Long id) {
        DailyReport r = reportRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("일보를 찾을 수 없습니다: " + id));
        if (!r.getStoreId().equals(sid(request))) {
            throw new IllegalArgumentException("일보를 찾을 수 없습니다: " + id);
        }
        return r;
    }

    @GetMapping("/reports")
    @Transactional(readOnly = true)
    public List<ReportResponse> reports(HttpServletRequest request) {
        return reportRepository.findByStoreIdOrderByReportDateDescIdDesc(sid(request))
                .stream().map(ReportResponse::from).toList();
    }

    @GetMapping("/reports/{id}")
    @Transactional(readOnly = true)
    public ReportResponse report(@PathVariable Long id, HttpServletRequest request) {
        return ReportResponse.from(getReport(request, id));
    }

    @PostMapping("/reports")
    @ResponseStatus(HttpStatus.CREATED)
    public ReportResponse createReport(@Valid @RequestBody ReportRequest req,
                                       HttpServletRequest request) {
        Member me = loginMember(request);
        DailyReport saved = reportRepository.save(
                new DailyReport(me.getStoreId(), req.author(), req.reportDate(), req.content()));

        notificationService.notifyAll(me.getStoreId(), Notification.Type.REPORT,
                req.author() + "님이 " + req.reportDate() + " 일보를 작성했습니다.", me);

        return ReportResponse.from(saved);
    }

    @PatchMapping("/reports/{id}")
    public ReportResponse editReport(@PathVariable Long id,
                                     @Valid @RequestBody ReportEditRequest req,
                                     HttpServletRequest request) {
        Member me = loginMember(request);
        DailyReport report = getReport(request, id);
        if (!me.isOwner() && !report.getAuthor().equals(me.getDisplayName())) {
            throw new IllegalStateException("본인이 작성한 일보만 수정할 수 있습니다.");
        }
        reportHistoryRepository.save(new ReportHistory(report,
                report.getContent(), report.getReportDate(), me.getDisplayName()));
        report.edit(req.reportDate(), req.content());
        return ReportResponse.from(report);
    }

    @GetMapping("/reports/{id}/history")
    @Transactional(readOnly = true)
    public List<ReportHistoryResponse> reportHistory(@PathVariable Long id, HttpServletRequest request) {
        getReport(request, id);   // 소유 검증
        return reportHistoryRepository.findByReportIdOrderByEditedAtDesc(id)
                .stream().map(ReportHistoryResponse::from).toList();
    }

    // ---- 일보 댓글 ----

    public record CommentRequest(@NotBlank String content) {}
    public record CommentResponse(Long id, String author, String content,
                                  LocalDateTime createdAt, LocalDateTime editedAt) {
        static CommentResponse from(ReportComment c) {
            return new CommentResponse(c.getId(), c.getAuthor(), c.getContent(),
                    c.getCreatedAt(), c.getEditedAt());
        }
    }
    public record CommentHistoryResponse(Long id, String previousContent,
                                         String editedBy, LocalDateTime editedAt) {
        static CommentHistoryResponse from(CommentHistory h) {
            return new CommentHistoryResponse(h.getId(), h.getPreviousContent(),
                    h.getEditedBy(), h.getEditedAt());
        }
    }

    @GetMapping("/reports/{id}/comments")
    @Transactional(readOnly = true)
    public List<CommentResponse> comments(@PathVariable Long id, HttpServletRequest request) {
        getReport(request, id);   // 소유 검증
        return commentRepository.findByReportIdOrderByCreatedAtAsc(id)
                .stream().map(CommentResponse::from).toList();
    }

    @PostMapping("/reports/{id}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public CommentResponse addComment(@PathVariable Long id,
                                      @Valid @RequestBody CommentRequest req,
                                      HttpServletRequest request) {
        DailyReport report = getReport(request, id);
        ReportComment saved = commentRepository.save(new ReportComment(
                report, loginMember(request).getDisplayName(), req.content()));
        return CommentResponse.from(saved);
    }

    /** 댓글 수정 — 작성자 본인 또는 사장님. 고치기 전 내용은 이력으로 남긴다 */
    @PatchMapping("/comments/{commentId}")
    public CommentResponse editComment(@PathVariable Long commentId,
                                       @Valid @RequestBody CommentRequest req,
                                       HttpServletRequest request) {
        Member me = loginMember(request);
        ReportComment comment = getComment(request, commentId);
        if (!comment.canBeModifiedBy(me)) {
            throw new AccessDeniedException("본인이 작성한 댓글만 수정할 수 있습니다.");
        }
        commentHistoryRepository.save(
                new CommentHistory(comment, comment.getContent(), me.getDisplayName()));
        comment.edit(req.content());
        return CommentResponse.from(comment);
    }

    @DeleteMapping("/comments/{commentId}")
    public void deleteComment(@PathVariable Long commentId, HttpServletRequest request) {
        Member me = loginMember(request);
        ReportComment comment = getComment(request, commentId);
        if (!comment.canBeModifiedBy(me)) {
            throw new AccessDeniedException("본인이 작성한 댓글만 삭제할 수 있습니다.");
        }
        commentHistoryRepository.deleteByCommentId(commentId);
        commentRepository.delete(comment);
    }

    @GetMapping("/comments/{commentId}/history")
    @Transactional(readOnly = true)
    public List<CommentHistoryResponse> commentHistory(@PathVariable Long commentId,
                                                       HttpServletRequest request) {
        getComment(request, commentId);   // 소유 검증
        return commentHistoryRepository.findByCommentIdOrderByEditedAtDesc(commentId)
                .stream().map(CommentHistoryResponse::from).toList();
    }

    /** 우리 가게 일보에 달린 댓글만 조회 — 다른 가게 댓글 id를 넘기면 찾을 수 없음 */
    private ReportComment getComment(HttpServletRequest request, Long commentId) {
        ReportComment c = commentRepository.findById(commentId)
                .orElseThrow(() -> new IllegalArgumentException("댓글을 찾을 수 없습니다: " + commentId));
        if (!c.getReport().getStoreId().equals(loginMember(request).getStoreId())) {
            throw new IllegalArgumentException("댓글을 찾을 수 없습니다: " + commentId);
        }
        return c;
    }

    // ================= 예약 =================

    /** id=테이블 PK(선택·예약용), number=가게 내 표시번호 */
    public record TableResponse(Long id, int number, int capacity, boolean reserved) {}
    public record ReservationRequest(@NotNull LocalDate reserveDate, @NotBlank String timeSlot,
                                     @Min(1) int people, @NotNull List<Long> tableIds,
                                     String customerName, String courseName) {}
    public record ReservationResponse(Long id, LocalDate reserveDate, String timeSlot,
                                      int people, List<Integer> tableNumbers, int totalCapacity,
                                      String customerName, String courseName, Integer courseDurationMinutes,
                                      String status) {
        static ReservationResponse from(Reservation r) {
            return new ReservationResponse(r.getId(), r.getReserveDate(), r.getTimeSlot(),
                    r.getPeople(), r.tableNumbers(), r.totalCapacity(), r.getCustomerName(),
                    r.getCourseName(), r.getCourseDurationMinutes(), r.getStatus().name());
        }
    }

    private Reservation getReservation(HttpServletRequest request, Long id) {
        Reservation r = reservationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("예약을 찾을 수 없습니다: " + id));
        if (!r.getStoreId().equals(sid(request))) {
            throw new IllegalArgumentException("예약을 찾을 수 없습니다: " + id);
        }
        return r;
    }

    /** 특정 날짜의 테이블 현황 — 공석 처리 전까지 사용중으로 본다. */
    @GetMapping("/tables")
    @Transactional(readOnly = true)
    public List<TableResponse> tables(@RequestParam LocalDate date, @RequestParam String timeSlot,
                                      HttpServletRequest request) {
        Long storeId = sid(request);
        return tableRepository.findByStoreIdOrderByTableNumberAsc(storeId).stream()
                .map(t -> new TableResponse(t.getId(), t.getTableNumber(), t.getCapacity(),
                        reservationRepository.existsByStoreIdAndReserveDateAndStatusAndTables_Id(
                                storeId, date, Reservation.Status.ACTIVE, t.getId())))
                .toList();
    }

    @GetMapping("/reservations")
    @Transactional(readOnly = true)
    public List<ReservationResponse> reservations(@RequestParam LocalDate date, HttpServletRequest request) {
        return reservationRepository.findByStoreIdAndReserveDateOrderByTimeSlotAsc(sid(request), date)
                .stream().map(ReservationResponse::from).toList();
    }

    @GetMapping("/reservations/upcoming")
    @Transactional(readOnly = true)
    public List<ReservationResponse> upcomingReservations(HttpServletRequest request) {
        return reservationRepository
                .findByStoreIdAndReserveDateGreaterThanEqualOrderByReserveDateAscTimeSlotAsc(
                        sid(request), LocalDate.now())
                .stream().map(ReservationResponse::from).toList();
    }

    @GetMapping("/reservations/{id}")
    @Transactional(readOnly = true)
    public ReservationResponse reservationDetail(@PathVariable Long id, HttpServletRequest request) {
        return ReservationResponse.from(getReservation(request, id));
    }

    @GetMapping("/reservations/month")
    @Transactional(readOnly = true)
    public List<ReservationResponse> monthReservations(@RequestParam int year, @RequestParam int month,
                                                       HttpServletRequest request) {
        LocalDate start = LocalDate.of(year, month, 1);
        LocalDate end = start.plusMonths(1).minusDays(1);
        return reservationRepository
                .findByStoreIdAndReserveDateBetweenOrderByReserveDateAscTimeSlotAsc(sid(request), start, end)
                .stream().map(ReservationResponse::from).toList();
    }

    /** 예약 확정 — 다중 테이블(단체) 지원, 정원 합계 검증, 사용중 테이블 거절 */
    @PostMapping("/reservations")
    @ResponseStatus(HttpStatus.CREATED)
    public ReservationResponse reserve(@Valid @RequestBody ReservationRequest req,
                                       HttpServletRequest request) {
        Member me = loginMember(request);
        Long storeId = me.getStoreId();
        if (req.tableIds() == null || req.tableIds().isEmpty()) {
            throw new IllegalArgumentException("테이블을 1개 이상 선택해 주세요.");
        }

        List<DiningTable> tables = new ArrayList<>();
        for (Long tableId : req.tableIds()) {
            DiningTable t = tableRepository.findById(tableId)
                    .orElseThrow(() -> new IllegalArgumentException("테이블을 찾을 수 없습니다: " + tableId));
            if (!t.getStoreId().equals(storeId)) {
                throw new IllegalArgumentException("우리 가게 테이블이 아닙니다.");
            }
            if (reservationRepository.existsByStoreIdAndReserveDateAndStatusAndTables_Id(
                    storeId, req.reserveDate(), Reservation.Status.ACTIVE, tableId)) {
                throw new IllegalStateException(
                        t.getTableNumber() + "번 테이블은 사용중입니다. 공석 처리 후 다시 배정할 수 있습니다.");
            }
            tables.add(t);
        }

        int capacity = tables.stream().mapToInt(DiningTable::getCapacity).sum();
        if (req.people() > capacity) {
            throw new IllegalArgumentException("선택한 테이블 정원이 " + capacity
                    + "명입니다. 테이블을 더 선택해 주세요. (예약 인원 " + req.people() + "명)");
        }

        Integer courseDuration = (req.courseName() == null || req.courseName().isBlank()) ? null
                : courseRepository.findByStoreIdAndName(storeId, req.courseName())
                        .map(Course::getDurationMinutes).orElse(null);

        Reservation saved = reservationRepository.save(new Reservation(
                req.reserveDate(), req.timeSlot(), req.people(), tables, req.customerName(),
                req.courseName(), courseDuration));

        String tableLabel = tables.stream()
                .map(t -> String.valueOf(t.getTableNumber())).collect(Collectors.joining(", "));
        notificationService.notifyAll(storeId, Notification.Type.RESERVATION,
                "새 예약 · " + (saved.getCourseName() == null ? "" : saved.getCourseName() + " · ")
                        + req.reserveDate() + " " + req.timeSlot()
                        + " " + req.people() + "명 (테이블 " + tableLabel + ")"
                        + (req.customerName() == null || req.customerName().isBlank()
                           ? "" : " - " + req.customerName()),
                me);
        return ReservationResponse.from(saved);
    }

    @DeleteMapping("/reservations/{id}")
    public void cancelReservation(@PathVariable Long id, HttpServletRequest request) {
        Reservation r = getReservation(request, id);
        reservationRepository.delete(r);
    }

    /** 공석 처리 — 손님 퇴장 후 근무자가 테이블을 비운다 */
    @PostMapping("/reservations/{id}/release")
    public ReservationResponse release(@PathVariable Long id, HttpServletRequest request) {
        Reservation reservation = getReservation(request, id);
        reservation.release();
        return ReservationResponse.from(reservation);
    }
}
