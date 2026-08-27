package com.izacare.web;

import com.izacare.domain.Member;
import com.izacare.dto.Dtos.AuditResponse;
import com.izacare.dto.Dtos.OverrideLineRequest;
import com.izacare.service.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/** 실사(AI 사진 인식) 화면 — 모두 storeId 범위로 한정 */
@RestController
@RequestMapping("/api/audits")
public class AuditController {

    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    private Long storeId(HttpServletRequest request) {
        return ((Member) request.getAttribute("loginMember")).getStoreId();
    }

    /** 사진 업로드 → AI 인식 → DRAFT 실사 생성 */
    @PostMapping("/scan")
    @ResponseStatus(HttpStatus.CREATED)
    public AuditResponse scan(@RequestParam("image") MultipartFile image,
                              HttpServletRequest request) throws IOException {
        if (image.isEmpty()) {
            throw new IllegalArgumentException("이미지 파일이 비어 있습니다.");
        }
        return auditService.createAuditFromImage(storeId(request), image.getBytes(), image.getContentType());
    }

    @GetMapping
    public List<AuditResponse> recent(HttpServletRequest request) {
        return auditService.recentAudits(storeId(request));
    }

    @GetMapping("/{id}")
    public AuditResponse get(@PathVariable Long id, HttpServletRequest request) {
        return auditService.findAudit(storeId(request), id);
    }

    /** 오인식 수량 수동 보정 */
    @PatchMapping("/{auditId}/lines/{lineId}")
    public AuditResponse overrideLine(@PathVariable Long auditId,
                                      @PathVariable Long lineId,
                                      @Valid @RequestBody OverrideLineRequest req,
                                      HttpServletRequest request) {
        return auditService.overrideLine(storeId(request), auditId, lineId, req.finalQuantity());
    }

    /** 확정 → 재고 반영 */
    @PostMapping("/{id}/confirm")
    public AuditResponse confirm(@PathVariable Long id, HttpServletRequest request) {
        return auditService.confirm(storeId(request), id);
    }

    @PostMapping("/{id}/cancel")
    public AuditResponse cancel(@PathVariable Long id, HttpServletRequest request) {
        return auditService.cancel(storeId(request), id);
    }
}
