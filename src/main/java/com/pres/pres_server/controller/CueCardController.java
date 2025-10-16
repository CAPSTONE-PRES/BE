package com.pres.pres_server.controller;

import com.pres.pres_server.domain.User;
import com.pres.pres_server.service.CueCardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@Tag(name = "CueCard Controller", description = "큐카드 및 발표자료 관련 API")
@RequestMapping("/projects")
public class CueCardController {

    private final CueCardService cueCardService;

    @Operation(summary = "큐카드 체크/취소 api", description = "슬라이드별 큐카드(1,2)에 대한 체크 표시 생성 및 삭제")
    @PatchMapping("/{fileId}/cucard/{slideNumber}/{cueId}/check")
    public ResponseEntity<Map<String,Object>> toggleCueCheck(
            @PathVariable Long fileId,
            @PathVariable int slideNumber,
            @PathVariable Long cueId,
            @RequestParam String status, // on / off
            @AuthenticationPrincipal User user) {

        if (!"on".equalsIgnoreCase(status) && !"off".equalsIgnoreCase(status)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "status는 on 또는 off여야 합니다.");
        }
        boolean checked = "on".equalsIgnoreCase(status);
        cueCardService.setCheckStatus(fileId, slideNumber, cueId, user, checked);

        Map<String,Object> resp = new HashMap<>();
        resp.put("cueId", cueId);
        resp.put("status", checked ? "on" : "off");
        resp.put("message", "체크 상태가 업데이트되었습니다.");
        return ResponseEntity.ok(resp);
    }

}
