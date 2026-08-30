package com.example.demodrug.controller;

import com.example.demodrug.dto.HisDtos;
import com.example.demodrug.service.HisApiKeyService;
import com.example.demodrug.service.HisApplicationService;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import java.util.Map;

@RestController
@RequestMapping("/api/integration/his/v1")
public class HisIntegrationController {

    @Resource
    private HisApiKeyService hisApiKeyService;

    @Resource
    private HisApplicationService hisApplicationService;

    @PostMapping("/drug-applications")
    public HisDtos.ReceiveResponse receive(
            @RequestHeader("X-HIS-Key") String apiKey,
            @RequestBody HisDtos.ApplicationRequest request) {
        hisApiKeyService.requireValid(apiKey);
        return hisApplicationService.receive(request);
    }

    @PostMapping("/drug-applications/{applicationNo}/cancel")
    public Map<String, Object> cancel(
            @RequestHeader("X-HIS-Key") String apiKey,
            @PathVariable String applicationNo,
            @RequestParam(value = "sourceSystem", defaultValue = "HIS") String sourceSystem,
            @RequestBody HisDtos.CancelRequest request) {
        hisApiKeyService.requireValid(apiKey);
        return hisApplicationService.cancel(sourceSystem, applicationNo, request);
    }
}
