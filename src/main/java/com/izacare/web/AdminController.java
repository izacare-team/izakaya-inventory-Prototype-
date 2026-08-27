package com.izacare.web;

import com.izacare.domain.Member;
import com.izacare.domain.Notification;
import com.izacare.repository.MemberRepository;
import com.izacare.service.NotificationService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/** 사장님 전용: 직원 승인 / 거절 / 퇴직 / 복직 — 내 가게 직원만 대상 */
@RestController
@RequestMapping("/api/admin")
@Transactional
public class AdminController {

    private final MemberRepository memberRepository;
    private final NotificationService notificationService;

    public AdminController(MemberRepository memberRepository,
                           NotificationService notificationService) {
        this.memberRepository = memberRepository;
        this.notificationService = notificationService;
    }

    private Member me(HttpServletRequest request) {
        return (Member) request.getAttribute("loginMember");
    }

    public record MemberRow(Long id, String username, String displayName,
                            String role, String status, LocalDateTime createdAt) {
        static MemberRow from(Member m) {
            return new MemberRow(m.getId(), m.getUsername(), m.getDisplayName(),
                    m.getRole().name(), m.getStatus().name(), m.getCreatedAt());
        }
    }

    @GetMapping("/members")
    @Transactional(readOnly = true)
    public List<MemberRow> members(HttpServletRequest request) {
        return memberRepository.findByStoreIdOrderByStatusAscCreatedAtAsc(me(request).getStoreId())
                .stream().map(MemberRow::from).toList();
    }

    @PostMapping("/members/{id}/approve")
    public MemberRow approve(@PathVariable Long id, HttpServletRequest request) {
        Member m = get(request, id);
        m.approve();

        notificationService.notifyMember(m, Notification.Type.MEMBER,
                "가입이 승인되었습니다. 이제 모든 기능을 사용할 수 있어요.");
        // 합류 소식은 다른 알바생에게는 불필요하므로 사장님에게만
        notificationService.notifyOwners(m.getStoreId(), Notification.Type.MEMBER,
                m.getDisplayName() + "님이 합류했습니다.", me(request));
        return MemberRow.from(m);
    }

    @PostMapping("/members/{id}/reject")
    public MemberRow reject(@PathVariable Long id, HttpServletRequest request) {
        Member m = get(request, id);
        if (m.isOwner()) throw new IllegalArgumentException("사장님 계정은 거절할 수 없습니다.");
        m.reject();
        return MemberRow.from(m);
    }

    /** 퇴직 처리 — 권한 즉시 회수 (로그인·진행 중 세션 모두 차단) */
    @PostMapping("/members/{id}/resign")
    public MemberRow resign(@PathVariable Long id, HttpServletRequest request) {
        Member m = get(request, id);
        if (m.isOwner()) throw new IllegalArgumentException("사장님 계정은 퇴직 처리할 수 없습니다.");
        if (m.getStatus() != Member.Status.ACTIVE) {
            throw new IllegalStateException("근무 중인 직원만 퇴직 처리할 수 있습니다.");
        }
        m.resign();

        notificationService.notifyAll(m.getStoreId(), Notification.Type.MEMBER,
                m.getDisplayName() + "님이 퇴직 처리되었습니다.", m);
        return MemberRow.from(m);
    }

    /** 복직 — 퇴직·거절 직원을 다시 근무 상태로 */
    @PostMapping("/members/{id}/reinstate")
    public MemberRow reinstate(@PathVariable Long id, HttpServletRequest request) {
        Member m = get(request, id);
        if (m.getStatus() == Member.Status.ACTIVE) {
            throw new IllegalStateException("이미 근무 중인 직원입니다.");
        }
        m.approve();

        notificationService.notifyMember(m, Notification.Type.MEMBER,
                "복직 처리되었습니다. 다시 로그인할 수 있어요.");
        notificationService.notifyOwners(m.getStoreId(), Notification.Type.MEMBER,
                m.getDisplayName() + "님이 복직했습니다.", me(request));
        return MemberRow.from(m);
    }

    /** 내 가게 소속 직원만 조회 — 다른 가게 회원 id를 넘기면 찾을 수 없음 */
    private Member get(HttpServletRequest request, Long id) {
        Member m = memberRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("회원을 찾을 수 없습니다: " + id));
        if (!m.getStoreId().equals(me(request).getStoreId())) {
            throw new IllegalArgumentException("회원을 찾을 수 없습니다: " + id);
        }
        return m;
    }
}
