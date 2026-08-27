package com.izacare.web;

import com.izacare.domain.Member;
import com.izacare.repository.MemberRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * /api/** 접근 제어.
 * - /api/auth/** : 누구나
 * - /api/admin/** , 공지 등록·삭제 : OWNER만
 * - 그 외 /api/** : 로그인(ACTIVE) 필수
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    private final MemberRepository memberRepository;

    public AuthInterceptor(MemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        String uri = request.getRequestURI();
        if (!uri.startsWith("/api/") || uri.startsWith("/api/auth/")) return true;

        HttpSession session = request.getSession(false);
        Long memberId = session == null ? null : (Long) session.getAttribute(AuthController.SESSION_KEY);
        Member member = memberId == null ? null
                : memberRepository.findById(memberId).orElse(null);

        if (member == null || member.getStatus() != Member.Status.ACTIVE) {
            response.setStatus(401);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"error\":\"로그인이 필요합니다.\"}");
            return false;
        }

        boolean ownerOnly = uri.startsWith("/api/admin/")
                || (uri.startsWith("/api/notices") &&
                    ("POST".equals(request.getMethod()) && !uri.endsWith("/ack")
                     || "DELETE".equals(request.getMethod())));

        if (ownerOnly && !member.isOwner()) {
            response.setStatus(403);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"error\":\"사장님만 사용할 수 있는 기능입니다.\"}");
            return false;
        }

        request.setAttribute("loginMember", member);   // 컨트롤러에서 재사용
        return true;
    }
}
