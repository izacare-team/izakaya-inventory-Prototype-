package com.izacare.web;

import com.izacare.domain.Member;
import com.izacare.dto.Dtos.AuditResponse;
import com.izacare.dto.Dtos.OverrideLineRequest;
import com.izacare.service.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** 실사(AI 사진 인식) 화면 — 모두 storeId 범위로 한정 */
@RestController
@RequestMapping("/api/audits")
public class AuditController {

    /** 한 실사에 올릴 수 있는 사진 장수 — 냉장고·주류고·창고·주방 정도면 충분 */
    private static final int MAX_IMAGES = 8;

    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    private Long storeId(HttpServletRequest request) {
        return ((Member) request.getAttribute("loginMember")).getStoreId();
    }

    /** 사진 업로드(여러 장 가능) → AI 인식 → DRAFT 실사 생성 */
    @PostMapping("/scan")
    @ResponseStatus(HttpStatus.CREATED)
    public AuditResponse scan(@RequestParam("images") List<MultipartFile> images,
                              HttpServletRequest request) throws IOException {
        List<byte[]> bytes = new ArrayList<>();
        for (MultipartFile image : images) {
            if (!image.isEmpty()) bytes.add(image.getBytes());
        }
        if (bytes.isEmpty()) {
            throw new IllegalArgumentException("이미지 파일이 비어 있습니다.");
        }
        if (bytes.size() > MAX_IMAGES) {
            throw new IllegalArgumentException("사진은 한 번에 " + MAX_IMAGES + "장까지 올릴 수 있습니다.");
        }
        return auditService.createAuditFromImages(storeId(request), bytes,
                images.get(0).getContentType());
    }

    /** 실사에 쓰인 사진 원본 (수량 조정 근거 확인용) */
    @GetMapping("/{id}/images/{index}")
    public ResponseEntity<byte[]> image(@PathVariable Long id, @PathVariable int index,
                                        HttpServletRequest request) {
        byte[] bytes = auditService.readImage(storeId(request), id, index);
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .cacheControl(CacheControl.noCache().cachePrivate())  // 실사 사진은 공유 캐시에 남기지 않는다
                .body(bytes);
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
