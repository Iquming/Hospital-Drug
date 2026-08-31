package com.hospital.pharmacy.controller;

import com.hospital.pharmacy.dto.HisDtos;
import com.hospital.pharmacy.entity.DrugApplication;
import com.hospital.pharmacy.entity.HisCallbackEvent;
import com.hospital.pharmacy.entity.HisDrugMapping;
import com.hospital.pharmacy.security.SecurityUtils;
import com.hospital.pharmacy.service.ApplicationDispenseService;
import com.hospital.pharmacy.service.HisApplicationService;
import com.hospital.pharmacy.service.HisCallbackService;
import com.hospital.pharmacy.service.IdempotencyService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import java.util.List;
import java.util.Map;

@RestController
public class PharmacyApplicationController {

    @Resource
    private HisApplicationService hisApplicationService;

    @Resource
    private ApplicationDispenseService applicationDispenseService;

    @Resource
    private HisCallbackService hisCallbackService;

    @Resource
    private IdempotencyService idempotencyService;

    @Value("${app.his.mode:mock}")
    private String hisMode;

    @Value("${app.his.status-callback-url:http://127.0.0.1:8090/api/his/drug-application-status}")
    private String callbackUrl;

    @GetMapping("/api/pharmacy/applications")
    public List<DrugApplication> applications(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "priority", required = false) String priority) {
        return hisApplicationService.list(status, keyword, priority);
    }

    @GetMapping("/api/pharmacy/applications/{id}")
    public DrugApplication application(@PathVariable Long id) {
        return hisApplicationService.detail(id);
    }

    @GetMapping("/api/pharmacy/his-drug-mappings")
    public List<HisDrugMapping> mappings() {
        return hisApplicationService.mappings();
    }

    @PostMapping("/api/pharmacy/his-drug-mappings")
    public Map<String, Object> saveMapping(@RequestBody HisDtos.MappingRequest request) {
        hisApplicationService.saveMapping(request, SecurityUtils.currentUser().operatorLabel());
        return Map.of("message", "HIS药品编码映射已保存");
    }

    @PostMapping("/api/pharmacy/application-items/{itemId}/dispense")
    public DrugApplication dispense(@PathVariable Long itemId, @RequestBody HisDtos.DispenseRequest request) {
        String operator = SecurityUtils.currentUser().operatorLabel();
        return idempotencyService.execute(request == null ? null : request.requestId(),
                "HIS_APPLICATION_DISPENSE", String.valueOf(itemId), operator,
                Map.of("itemId", itemId, "traceCode", request == null ? "" : request.traceCode()),
                () -> applicationDispenseService.dispense(itemId, request == null ? null : request.traceCode(), operator),
                DrugApplication.class);
    }

    @PostMapping("/api/pharmacy/application-items/{itemId}/return")
    public DrugApplication returnDrug(@PathVariable Long itemId, @RequestBody HisDtos.ReturnRequest request) {
        String operator = SecurityUtils.currentUser().operatorLabel();
        return idempotencyService.execute(request == null ? null : request.requestId(),
                "HIS_APPLICATION_RETURN", String.valueOf(itemId), operator,
                Map.of("itemId", itemId, "traceCode", request == null ? "" : request.traceCode()),
                () -> applicationDispenseService.returnDrug(itemId, request == null ? null : request.traceCode(), operator),
                DrugApplication.class);
    }

    @PostMapping("/api/pharmacy/applications/{id}/review")
    public DrugApplication review(@PathVariable Long id, @RequestBody HisDtos.ReviewRequest request) {
        return hisApplicationService.review(id, request, SecurityUtils.currentUser().operatorLabel());
    }

    @GetMapping("/api/his/callbacks")
    public List<HisCallbackEvent> callbacks(@RequestParam(value = "limit", defaultValue = "100") int limit) {
        return hisCallbackService.list(limit);
    }

    @PostMapping("/api/his/callbacks/{eventId}/retry")
    public Map<String, Object> retry(@PathVariable String eventId) {
        hisCallbackService.retry(eventId);
        return Map.of("message", "HIS状态回传已重新进入发送队列");
    }

    @GetMapping("/api/his/integration/status")
    public Map<String, Object> integrationStatus() {
        return Map.of(
                "mode", hisMode,
                "callbackUrl", "rest".equalsIgnoreCase(hisMode) ? callbackUrl : "本地模拟HIS",
                "callbackPollSeconds", 5
        );
    }

    @PostMapping("/api/admin/his-simulator/applications")
    public HisDtos.ReceiveResponse simulate(@RequestBody HisDtos.ApplicationRequest request) {
        return hisApplicationService.receive(request);
    }
}
