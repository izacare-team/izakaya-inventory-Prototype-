package com.izacare.web;

/**
 * 로그인은 되어 있지만 그 기능을 쓸 권한이 없을 때 (예: 알바생이 사장님 전용 알림을 열람).
 * 잘못된 입력(400)이나 상태 충돌(409)과 구분해 403으로 내려준다.
 */
public class AccessDeniedException extends RuntimeException {

    public AccessDeniedException() {
        super("권한이 없습니다.");
    }

    public AccessDeniedException(String message) {
        super(message);
    }
}
