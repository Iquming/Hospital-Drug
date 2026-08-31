package com.hospital.pharmacy.controller;

import com.hospital.pharmacy.dto.HisDtos;
import com.hospital.pharmacy.service.HisApiKeyService;
import com.hospital.pharmacy.service.HisApplicationService;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;

@RestController
@RequestMapping("/api/integration/his/v1")
public class HisIntegrationController {

    @Resource
    private HisApiKeyService hisApiKeyService;

    @Resource
    private HisApplicationService hisApplicationService;

    @Resource
    private ObjectMapper objectMapper;

    @PostMapping("/drug-applications")
    public HisDtos.ReceiveResponse receive(
            @RequestHeader("X-HIS-Key") String apiKey,
            @RequestHeader(value = "X-HIS-Timestamp", required = false) String timestamp,
            @RequestHeader(value = "X-HIS-Nonce", required = false) String nonce,
            @RequestHeader(value = "X-HIS-Signature", required = false) String signature,
            HttpServletRequest servletRequest,
            @RequestBody String body) {
        HisDtos.ApplicationRequest request = parse(body, HisDtos.ApplicationRequest.class);
        hisApiKeyService.requireValid(apiKey, timestamp, nonce, signature,
                requestTarget(servletRequest), body);
        return hisApplicationService.receive(request);
    }

    @PostMapping("/drug-applications/{applicationNo}/cancel")
    public Map<String, Object> cancel(
            @RequestHeader("X-HIS-Key") String apiKey,
            @RequestHeader(value = "X-HIS-Timestamp", required = false) String timestamp,
            @RequestHeader(value = "X-HIS-Nonce", required = false) String nonce,
            @RequestHeader(value = "X-HIS-Signature", required = false) String signature,
            @PathVariable String applicationNo,
            @RequestParam(value = "sourceSystem", defaultValue = "HIS") String sourceSystem,
            HttpServletRequest servletRequest,
            @RequestBody String body) {
        HisDtos.CancelRequest request = parse(body, HisDtos.CancelRequest.class);
        hisApiKeyService.requireValid(apiKey, timestamp, nonce, signature,
                requestTarget(servletRequest), body);
        return hisApplicationService.cancel(sourceSystem, applicationNo, request);
    }

    private <T> T parse(String body, Class<T> type) {
        try {
            return objectMapper.readValue(body, type);
        } catch (JacksonException e) {
            throw new IllegalArgumentException("HIS请求JSON格式无效");
        }
    }

    private String requestTarget(HttpServletRequest request) {
        return request.getQueryString() == null
                ? request.getRequestURI()
                : request.getRequestURI() + "?" + request.getQueryString();
    }
}
