package com.izacare.service;

import com.izacare.domain.Member;
import com.izacare.domain.Notification;
import com.izacare.repository.MemberRepository;
import com.izacare.repository.NotificationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 알림 생성·조회 — 종 아이콘에 모이는 이벤트성 알림을 담당.
 * 알림은 수신자(Member)를 통해 가게에 귀속되므로, 발송 대상도 항상 같은 가게로 한정한다.
 */
@Service
@Transactional
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final MemberRepository memberRepository;

    public NotificationService(NotificationRepository notificationRepository,
                               MemberRepository memberRepository) {
        this.notificationRepository = notificationRepository;
        this.memberRepository = memberRepository;
    }

    /** 특정 직원 1명에게 (예: 근무 스케줄 등록) */
    public void notifyMember(Member recipient, Notification.Type type, String message) {
        notificationRepository.save(new Notification(recipient, type, message));
    }

    /** 해당 가게의 사장님(OWNER) 전원에게 (예: 알바생 가입 신청) */
    public void notifyOwners(Long storeId, Notification.Type type, String message) {
        notifyOwners(storeId, type, message, null);
    }

    /** 사장님 전원에게. 발생시킨 본인은 제외 (사장님이 직접 한 일은 본인에게 알리지 않음) */
    public void notifyOwners(Long storeId, Notification.Type type, String message, Member exclude) {
        memberRepository.findByStoreIdAndStatus(storeId, Member.Status.ACTIVE).stream()
                .filter(Member::isOwner)
                .filter(m -> exclude == null || !m.getId().equals(exclude.getId()))
                .forEach(m -> notificationRepository.save(new Notification(m, type, message)));
    }

    /**
     * 해당 가게의 근무 중 전원에게 (예: 새 예약). 발생시킨 본인은 제외.
     * 사장님 전용 알림(폐기·일보 등)이면 알바생에게는 보내지 않는다.
     */
    public void notifyAll(Long storeId, Notification.Type type, String message, Member exclude) {
        List<Member> targets = memberRepository.findByStoreIdAndStatus(storeId, Member.Status.ACTIVE).stream()
                .filter(m -> exclude == null || !m.getId().equals(exclude.getId()))
                .filter(m -> !type.isOwnerOnly() || m.isOwner())
                .toList();
        for (Member m : targets) {
            notificationRepository.save(new Notification(m, type, message));
        }
    }

    @Transactional(readOnly = true)
    public List<Notification> unread(Long memberId) {
        return notificationRepository.findByRecipientIdAndReadFlagFalseOrderByCreatedAtDesc(memberId);
    }

    public void markAllRead(Long memberId) {
        unread(memberId).forEach(Notification::markRead);
    }
}
