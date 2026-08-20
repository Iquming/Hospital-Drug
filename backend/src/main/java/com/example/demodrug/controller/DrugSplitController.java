package com.example.demodrug.controller;

import com.example.demodrug.security.SecurityUtils;
import com.example.demodrug.service.DrugSplitService;
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

    @PostMapping("/create")
    public ResponseEntity<?> create(@RequestBody Map<String, String> payload) {
        try {
            Integer splitUnits = payload.get("splitUnits") == null ? null : Integer.parseInt(payload.get("splitUnits"));
            return ResponseEntity.ok(drugSplitService.createSplitCode(
                    payload.get("parentTraceCode"),
                    splitUnits,
                    SecurityUtils.currentUser().operatorLabel()
            ));
        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest().body("拆零数量格式不正确");
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
        }
    }
}
