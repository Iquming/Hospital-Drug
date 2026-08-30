package com.hospital.pharmacy.controller;

import com.hospital.pharmacy.dao.DrugDao;
import com.hospital.pharmacy.constant.DispenseOperation;
import com.hospital.pharmacy.constant.PrescriptionStatus;
import com.hospital.pharmacy.entity.DispenseRecord;
import com.hospital.pharmacy.entity.DrugStock;
import com.hospital.pharmacy.entity.Prescription;
import com.hospital.pharmacy.security.CurrentUser;
import com.hospital.pharmacy.security.SecurityUtils;
import com.hospital.pharmacy.exception.BusinessException;
import com.hospital.pharmacy.service.DrugAcceptanceService;
import com.hospital.pharmacy.service.DrugDispenseService;
import com.hospital.pharmacy.service.IdempotencyService;
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

    @Resource
    private IdempotencyService idempotencyService;

    // 1. 获取库存列表
    @GetMapping("/list")
    public List<DrugStock> getDrugList() {
        return drugDao.findAll();
    }

    // 2. 入库接口
    @PostMapping("/add")
    public ResponseEntity<?> addDrug(@RequestBody DrugStock drug,
                                     @RequestParam(value = "requestId", required = false) String requestId) {
        try {
            String effectiveRequestId = StringUtils.hasText(requestId) ? requestId : drug == null ? null : drug.getRequestId();
            String operator = SecurityUtils.currentUser().operatorLabel();
            String result = idempotencyService.execute(
                    effectiveRequestId,
                    DispenseOperation.DRUG_INBOUND,
                    drug == null ? null : drug.getTraceCode(),
                    operator,
                    drugPayload(drug),
                    () -> {
                        drugAcceptanceService.scanAndAccept(drug, operator);
                        return "入库成功";
                    },
                    String.class
            );
            return ResponseEntity.ok(result);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            return fail("验收失败：" + e.getMessage(), e);
        }
    }

    // 3. 查处方接口
    @GetMapping("/prescriptions")
    public List<Prescription> getPrescriptions(@RequestParam("patientId") String patientId,
                                               @RequestParam(value = "status", defaultValue = PrescriptionStatus.PENDING) String status) {
        return drugDao.findPrescriptionsByPatient(patientId, status);
    }

    // 4. 发药/出库接口
    @PostMapping("/dispense")
    public ResponseEntity<?> dispense(@RequestBody Map<String, String> payload) {
        String code = payload.get("traceCode");
        String patientId = payload.get("patientId");
        String pIdStr = payload.get("prescriptionId");
        String requestId = payload.get("requestId");
        CurrentUser currentUser = SecurityUtils.currentUser();

        try {
            if (!StringUtils.hasText(pIdStr) && "NURSE".equals(currentUser.role())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body("护士账号不能执行药库质控出库");
            }
            String result = idempotencyService.execute(
                    requestId,
                    "DRUG_DISPENSE",
                    code,
                    currentUser.operatorLabel(),
                    payload,
                    () -> {
                        drugDispenseService.executeDispense(code, patientId, pIdStr, currentUser.operatorLabel());
                        return "出库成功";
                    },
                    String.class
            );
            return ResponseEntity.ok(result);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            return fail("失败：" + e.getMessage(), e);
        }
    }

    // 5. 处方退药接口
    @PostMapping("/return")
    public ResponseEntity<?> returnByPrescription(@RequestBody Map<String, String> payload) {
        try {
            String operator = SecurityUtils.currentUser().operatorLabel();
            String result = idempotencyService.execute(
                    payload.get("requestId"),
                    "DRUG_RETURN",
                    payload.get("traceCode"),
                    operator,
                    payload,
                    () -> {
                        drugDispenseService.executeReturn(
                                payload.get("prescriptionId"),
                                payload.get("traceCode"),
                                payload.get("patientId"),
                                payload.get("drugName"),
                                operator
                        );
                        return "退药成功";
                    },
                    String.class
            );
            return ResponseEntity.ok(result);
        } catch (BusinessException e) {
            throw e;
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

    private Map<String, Object> drugPayload(DrugStock drug) {
        Map<String, Object> payload = new HashMap<>();
        if (drug == null) {
            return payload;
        }
        payload.put("drugName", drug.getDrugName());
        payload.put("traceCode", drug.getTraceCode());
        payload.put("batchNumber", drug.getBatchNumber());
        payload.put("expireDate", drug.getExpireDate());
        payload.put("isSplitAllowed", drug.getIsSplitAllowed());
        payload.put("packageUnit", drug.getPackageUnit());
        payload.put("minUnit", drug.getMinUnit());
        payload.put("minUnitsPerPackage", drug.getMinUnitsPerPackage());
        return payload;
    }

    private int safeThreshold(int threshold) {
        return Math.max(1, Math.min(threshold, 10000));
    }

    private int safeDays(int days) {
        return Math.max(1, Math.min(days, 3650));
    }
}
