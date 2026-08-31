package com.izacare.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 요청을 보낸 클라이언트의 IP.
 *
 * X-Forwarded-For는 클라이언트가 임의로 보낼 수 있어, 신뢰할 수 있는 프록시
 * 뒤에 있을 때(app.trust-proxy=true)만 사용한다. 그렇지 않으면 요청마다 헤더를 바꿔
 * 로그인 시도 횟수 잠금을 우회하거나, 출근 위치 확인의 매장 IP 검사를 통과시킬 수 있다.
 */
@Component
public class ClientIpResolver {

    private final boolean trustProxy;

    public ClientIpResolver(@Value("${app.trust-proxy:false}") boolean trustProxy) {
        this.trustProxy = trustProxy;
    }

    public String resolve(HttpServletRequest request) {
        if (trustProxy) {
            String xff = request.getHeader("X-Forwarded-For");
            if (xff != null && !xff.isBlank()) return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
