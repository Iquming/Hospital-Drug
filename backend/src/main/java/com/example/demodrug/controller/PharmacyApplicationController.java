package com.example.demodrug.controller;

import com.example.demodrug.dto.HisDtos;
import com.example.demodrug.entity.DrugApplication;
import com.example.demodrug.entity.HisCallbackEvent;
import com.example.demodrug.entity.HisDrugMapping;
import com.example.demodrug.security.SecurityUtils;
import com.example.demodrug.service.ApplicationDispenseService;
import com.example.demodrug.service.HisApplicationService;
import com.example.demodrug.service.HisCallbackService;
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
        return applicationDispenseService.dispense(itemId, request.traceCode(),
                SecurityUtils.currentUser().operatorLabel());
    }

    @PostMapping("/api/pharmacy/application-items/{itemId}/return")
    public DrugApplication returnDrug(@PathVariable Long itemId, @RequestBody HisDtos.ReturnRequest request) {
        return applicationDispenseService.returnDrug(itemId, request.traceCode(),
                SecurityUtils.currentUser().operatorLabel());
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
