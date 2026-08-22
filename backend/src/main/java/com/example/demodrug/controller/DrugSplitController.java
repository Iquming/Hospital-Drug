package com.example.demodrug.controller;

import com.example.demodrug.exception.BusinessException;
import com.example.demodrug.security.SecurityUtils;
import com.example.demodrug.service.DrugSplitService;
import com.example.demodrug.service.IdempotencyService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import java.util.Map;

@RestController
@RequestMapping("/split")
public class DrugSplitController {

    @Resource
    private DrugSplitService drugSplitService;

    @Resource
    private IdempotencyService idempotencyService;

    @PostMapping("/create")
    public ResponseEntity<?> create(@RequestBody Map<String, String> payload) {
        try {
            Integer splitUnits = payload.get("splitUnits") == null ? null : Integer.parseInt(payload.get("splitUnits"));
            String operator = SecurityUtils.currentUser().operatorLabel();
            return ResponseEntity.ok(idempotencyService.execute(
                    payload.get("requestId"),
                    "SPLIT_CREATE",
                    payload.get("parentTraceCode"),
                    operator,
                    payload,
                    () -> drugSplitService.createSplitCode(payload.get("parentTraceCode"), splitUnits, operator),
                    Map.class
            ));
        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest().body("拆零数量格式不正确");
        } catch (BusinessException e) {
            throw e;
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
        }
    }

    @GetMapping("/{childCode}/label")
    public ResponseEntity<?> label(@PathVariable("childCode") String childCode) {
        try {
            return ResponseEntity.ok(drugSplitService.label(childCode));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
