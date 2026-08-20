package com.example.demodrug.controller;

import com.example.demodrug.dao.DrugDao;
import com.example.demodrug.entity.DispenseRecord;
import com.example.demodrug.entity.DrugStock;
import com.example.demodrug.entity.Prescription;
import com.example.demodrug.security.CurrentUser;
import com.example.demodrug.security.SecurityUtils;
import com.example.demodrug.service.DrugAcceptanceService;
import com.example.demodrug.service.DrugDispenseService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
public class DrugController {

    @Resource
    private DrugDao drugDao;

    @Resource
    private DrugAcceptanceService drugAcceptanceService;

    @Resource
    private DrugDispenseService drugDispenseService;

    // 1. 获取库存列表
    @GetMapping("/list")
    public List<DrugStock> getDrugList() {
        return drugDao.findAll();
    }

    // 2. 入库接口
    @PostMapping("/add")
    public ResponseEntity<String> addDrug(@RequestBody DrugStock drug) {
        try {
            drugAcceptanceService.scanAndAccept(drug, SecurityUtils.currentUser().operatorLabel());
            return ResponseEntity.ok("入库成功");
        } catch (Exception e) {
            return fail("验收失败：" + e.getMessage(), e);
        }
    }

    // 3. 查处方接口
    @GetMapping("/prescriptions")
    public List<Prescription> getPrescriptions(@RequestParam("patientId") String patientId,
                                               @RequestParam(value = "status", defaultValue = "待发药") String status) {
        return drugDao.findPrescriptionsByPatient(patientId, status);
    }

    // 4. 发药/出库接口
    @PostMapping("/dispense")
    public ResponseEntity<String> dispense(@RequestBody Map<String, String> payload) {
        String code = payload.get("traceCode");
        String patientId = payload.get("patientId");
        String pIdStr = payload.get("prescriptionId");
        CurrentUser currentUser = SecurityUtils.currentUser();

        try {
            if (!StringUtils.hasText(pIdStr) && "NURSE".equals(currentUser.role())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body("护士账号不能执行药库质控出库");
            }
            drugDispenseService.executeDispense(code, patientId, pIdStr, currentUser.operatorLabel());
            return ResponseEntity.ok("出库成功");
        } catch (Exception e) {
            return fail("失败：" + e.getMessage(), e);
        }
    }

    // 5. 处方退药接口
    @PostMapping("/return")
    public ResponseEntity<String> returnByPrescription(@RequestBody Map<String, String> payload) {
        try {
            drugDispenseService.executeReturn(
                    payload.get("prescriptionId"),
                    payload.get("traceCode"),
                    payload.get("patientId"),
                    payload.get("drugName"),
                    SecurityUtils.currentUser().operatorLabel()
            );
            return ResponseEntity.ok("退药成功");
        } catch (Exception e) {
            return fail("退药失败：" + e.getMessage(), e);
        }
    }

    // 6. 搜索接口
    @GetMapping("/search")
    public ResponseEntity<List<DrugStock>> search(@RequestParam("code") String code) {
        if (!StringUtils.hasText(code)) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(drugDao.findByTraceCode(code.trim()));
    }

    // 7. 获取流水记录
    @GetMapping("/records")
    public List<DispenseRecord> getRecords() {
        return drugDao.getAllRecords();
    }

    @GetMapping("/records/recent")
    public List<DispenseRecord> getRecentRecords(
            @RequestParam(value = "limit", defaultValue = "40") int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 200));
        return drugDao.getRecentRecords(safeLimit);
    }

    // 8. 近效期预警接口
    @GetMapping("/nearExpiry")
    public List<DrugStock> getNearExpiry(
            @RequestParam(value = "days", defaultValue = "90") int days) {
        int safeDays = Math.max(1, Math.min(days, 3650));
        return drugDao.findNearExpiry(safeDays);
    }

    @GetMapping("/dashboard/summary")
    public Map<String, Object> getDashboardSummary(
            @RequestParam(value = "lowThreshold", defaultValue = "50") int lowThreshold,
            @RequestParam(value = "expiryDays", defaultValue = "90") int expiryDays) {
        return drugDao.getDashboardSummary(safeThreshold(lowThreshold), safeDays(expiryDays));
    }

    @GetMapping("/stock/status")
    public Map<String, Object> getStockStatus(
            @RequestParam(value = "lowThreshold", defaultValue = "50") int lowThreshold,
            @RequestParam(value = "expiryDays", defaultValue = "90") int expiryDays) {
        return drugDao.getStockStatus(safeThreshold(lowThreshold), safeDays(expiryDays));
    }

    @GetMapping("/health/db")
    public Map<String, Object> getDbHealth() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("stockRows", drugDao.countStockRows());
        health.put("checkedAt", LocalDateTime.now().toString());
        return health;
    }

    private ResponseEntity<String> fail(String message, Exception e) {
        HttpStatus status = e instanceof IllegalArgumentException ? HttpStatus.BAD_REQUEST : HttpStatus.CONFLICT;
        return ResponseEntity.status(status).body(message);
    }

    private int safeThreshold(int threshold) {
        return Math.max(1, Math.min(threshold, 10000));
    }

    private int safeDays(int days) {
        return Math.max(1, Math.min(days, 3650));
    }
}
