package com.example.demodrug.controller;

import com.example.demodrug.security.CurrentUser;
import com.example.demodrug.security.SecurityUtils;
import com.example.demodrug.service.DeviceScanService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/device/scan")
public class DeviceScanController {

    private static final Set<String> NURSE_SCENES = Set.of("DISPENSE", "RETURN");

    @Resource
    private DeviceScanService deviceScanService;

    @PostMapping("/verify")
    public ResponseEntity<?> verify(@RequestBody Map<String, String> payload) {
        try {
            String scene = normalizeScene(payload.get("scene"));
            CurrentUser currentUser = SecurityUtils.currentUser();
            if ("NURSE".equals(currentUser.role()) && !NURSE_SCENES.contains(scene)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body("护士账号只能调用发药和退药扫描核对");
            }
            return ResponseEntity.ok(deviceScanService.verify(
                    scene,
                    payload.get("traceCode"),
                    payload.get("expectedDrugName")
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
        }
    }

    private String normalizeScene(String scene) {
        if (scene == null || scene.trim().isEmpty()) {
            throw new IllegalArgumentException("扫描场景不能为空");
        }
        return scene.trim().toUpperCase(Locale.ROOT);
    }
}
