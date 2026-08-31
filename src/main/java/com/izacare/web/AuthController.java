package com.izacare.web;

import com.izacare.domain.Member;
import com.izacare.domain.Notification;
import com.izacare.domain.Store;
import com.izacare.repository.MemberRepository;
import com.izacare.repository.StoreRepository;
import com.izacare.service.LoginGuard;
import com.izacare.service.NotificationService;
import com.izacare.service.SignupGuard;
import com.izacare.service.StoreCodeGenerator;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** 회원가입 / 로그인(세션) / 로그아웃 — 가게(멀티테넌트) 단위 */
@RestController
@RequestMapping("/api/auth")
@Transactional
public class AuthController {

    public static final String SESSION_KEY = "LOGIN_MEMBER_ID";

    private final MemberRepository memberRepository;
    private final StoreRepository storeRepository;
    private final StoreCodeGenerator codeGenerator;
    private final NotificationService notificationService;
    private final SignupGuard signupGuard;
    private final LoginGuard loginGuard;
    private final ClientIpResolver clientIpResolver;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public AuthController(MemberRepository memberRepository,
                          StoreRepository storeRepository,
                          StoreCodeGenerator codeGenerator,
                          NotificationService notificationService,
                          SignupGuard signupGuard,
                          LoginGuard loginGuard,
                          ClientIpResolver clientIpResolver) {
        this.memberRepository = memberRepository;
        this.storeRepository = storeRepository;
        this.codeGenerator = codeGenerator;
        this.notificationService = notificationService;
        this.signupGuard = signupGuard;
        this.loginGuard = loginGuard;
        this.clientIpResolver = clientIpResolver;
    }

    /**
     * 가입 요청.
     * - 사장님(OWNER): storeName으로 새 가게 생성 + 코드 발급, 즉시 ACTIVE.
     * - 알바생(STAFF): storeCode로 기존 가게에 소속, PENDING(승인 대기).
     */
    public record SignupRequest(@NotBlank String username, @NotBlank String password,
                                @NotBlank String displayName, @NotBlank String role,
                                String storeName, String storeCode) {}
    // 로그인은 아이디+비밀번호만 (아이디가 전역 유니크라 가게 코드 불필요)
    public record LoginRequest(@NotBlank String username, @NotBlank String password) {}
    public record MeResponse(Long id, String username, String displayName, String role,
                             String storeName, String storeCode) {
        static MeResponse from(Member m) {
            // 가게 코드는 사장님에게만 노출
            String code = m.isOwner() ? m.getStore().getCode() : null;
            return new MeResponse(m.getId(), m.getUsername(), m.getDisplayName(),
                    m.getRole().name(), m.getStore().getName(), code);
        }
    }

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, String> signup(@Valid @RequestBody SignupRequest req,
                                      HttpServletRequest request) {
        signupGuard.checkAndRecord(clientIp(request));

        boolean isOwner = "OWNER".equals(req.role());
        Store store;

        if (isOwner) {
            if (req.storeName() == null || req.storeName().isBlank()) {
                throw new IllegalArgumentException("가게 이름을 입력해 주세요.");
            }
            store = storeRepository.save(new Store(req.storeName().trim(), codeGenerator.generateUnique()));
        } else {
            if (req.storeCode() == null || req.storeCode().isBlank()) {
                throw new IllegalArgumentException("가게 코드를 입력해 주세요.");
            }
            store = storeRepository.findByCode(req.storeCode().trim().toUpperCase())
                    .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 가게 코드입니다."));
        }

        if (memberRepository.existsByUsername(req.username().trim())) {
            throw new IllegalArgumentException("이미 사용 중인 아이디입니다.");
        }

        Member.Role role = isOwner ? Member.Role.OWNER : Member.Role.STAFF;
        Member.Status status = isOwner ? Member.Status.ACTIVE : Member.Status.PENDING;
        Member saved = memberRepository.save(new Member(store, req.username().trim(),
                encoder.encode(req.password()), req.displayName().trim(), role, status));

        if (!isOwner) {
            notificationService.notifyOwners(store.getId(), Notification.Type.SIGNUP,
                    saved.getDisplayName() + "님이 가입 신청했습니다. 승인이 필요합니다.");
        }

        if (isOwner) {
            return Map.of("message", "가게가 개설되었습니다! 아래 코드를 직원에게 알려주세요.",
                    "storeName", store.getName(), "storeCode", store.getCode());
        }
        return Map.of("message", "가입 완료! 사장님 승인 후 로그인할 수 있습니다.");
    }

    @PostMapping("/login")
    public MeResponse login(@Valid @RequestBody LoginRequest req, HttpServletRequest request,
                            HttpSession session) {
        String username = req.username().trim();
        String guardKey = username + "|" + clientIp(request);
        loginGuard.checkLocked(guardKey);

        Member member = memberRepository.findByUsername(username).orElse(null);

        if (member == null || !encoder.matches(req.password(), member.getPasswordHash())) {
            loginGuard.recordFail(guardKey);
            throw new IllegalArgumentException("아이디 또는 비밀번호가 올바르지 않습니다.");
        }
        if (member.getStatus() == Member.Status.PENDING) {
            throw new IllegalStateException("사장님 승인 대기 중입니다. 승인 후 로그인할 수 있어요.");
        }
        if (member.getStatus() == Member.Status.REJECTED) {
            throw new IllegalStateException("가입이 거절된 계정입니다. 사장님께 문의하세요.");
        }
        if (member.getStatus() == Member.Status.RESIGNED) {
            throw new IllegalStateException("퇴직 처리된 계정입니다. 사장님께 문의하세요.");
        }

        loginGuard.clear(guardKey);
        request.changeSessionId();   // 세션 고정 방지 — 로그인 시 세션 ID 재발급
        session.setAttribute(SESSION_KEY, member.getId());
        return MeResponse.from(member);
    }

    @PostMapping("/logout")
    public Map<String, String> logout(HttpSession session) {
        session.invalidate();
        return Map.of("message", "로그아웃 되었습니다.");
    }

    @GetMapping("/me")
    public ResponseEntity<MeResponse> me(HttpSession session) {
        Long id = (Long) session.getAttribute(SESSION_KEY);
        if (id == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        return memberRepository.findById(id)
                .filter(m -> m.getStatus() == Member.Status.ACTIVE)
                .map(m -> ResponseEntity.ok(MeResponse.from(m)))
                .orElse(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }

    private String clientIp(HttpServletRequest request) {
        return clientIpResolver.resolve(request);
    }
}
