package com.hospital.pharmacy.service;

import com.hospital.pharmacy.dao.HisIntegrationDao;
import com.hospital.pharmacy.entity.DrugApplication;
import com.hospital.pharmacy.entity.DrugApplicationItem;
import com.hospital.pharmacy.entity.HisCallbackEvent;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import jakarta.annotation.Resource;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class HisCallbackService {

    private static final long[] RETRY_SECONDS = {5, 30, 120, 600};

    @Resource
    private HisIntegrationDao hisIntegrationDao;

    @Resource
    private ObjectMapper objectMapper;

    @Resource
    private HisApiKeyService hisApiKeyService;

    @Value("${app.his.mode:mock}")
    private String mode;

    @Value("${app.his.api-key:his-demo-key}")
    private String apiKey;

    @Value("${app.his.status-callback-url:http://127.0.0.1:8090/api/his/drug-application-status}")
    private String callbackUrl;

    @Value("${app.his.connect-timeout-ms:3000}")
    private int connectTimeoutMs;

    @Value("${app.his.read-timeout-ms:5000}")
    private int readTimeoutMs;

    @Value("${app.his.callback-enabled:true}")
    private boolean callbackEnabled;

    @Value("${app.his.callback-processing-timeout-seconds:120}")
    private int processingTimeoutSeconds;

    @Value("${app.his.allow-insecure-http:false}")
    private boolean allowInsecureHttp;

    public String enqueue(Long applicationId, String eventType, String operator) {
        DrugApplication application = hisIntegrationDao.findApplicationById(applicationId);
        if (application == null) {
            throw new IllegalArgumentException("HIS申请单不存在");
        }
        List<DrugApplicationItem> items = hisIntegrationDao.findItems(applicationId);
        String eventId = "PHARMACY-" + UUID.randomUUID();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventId", eventId);
        payload.put("sourceSystem", application.getSourceSystem());
        payload.put("applicationNo", application.getHisApplicationNo());
        payload.put("revision", application.getRevisionNo());
        payload.put("status", application.getStatus());
        payload.put("reviewStatus", application.getReviewStatus());
        payload.put("reviewedBy", application.getReviewedBy());
        payload.put("reviewedAt", application.getReviewedAt());
        payload.put("patientId", application.getPatientId());
        payload.put("operator", operator);
        payload.put("eventTime", LocalDateTime.now());
        payload.put("items", items.stream().map(item -> Map.of(
                "itemNo", item.getHisItemNo(),
                "hisDrugCode", item.getHisDrugCode(),
                "requestedQuantity", item.getRequestedQuantity(),
                "dispensedQuantity", item.getDispensedQuantity(),
                "returnedQuantity", item.getReturnedQuantity(),
                "unit", item.getUnit(),
                "status", item.getStatus()
        )).toList());
        try {
            hisIntegrationDao.createCallbackEvent(eventId, applicationId, eventType,
                    application.getStatus(), objectMapper.writeValueAsString(payload), operator);
        } catch (JacksonException e) {
            throw new IllegalStateException("HIS回传报文生成失败", e);
        }
        return eventId;
    }

    public List<HisCallbackEvent> list(int limit) {
        return hisIntegrationDao.listCallbacks(Math.max(1, Math.min(limit, 300)));
    }

    public void retry(String eventId) {
        if (!StringUtils.hasText(eventId) || hisIntegrationDao.retryCallback(eventId.trim()) <= 0) {
            throw new IllegalArgumentException("未找到可重试的HIS回传事件");
        }
    }

    @Scheduled(fixedDelayString = "${app.his.callback-poll-ms:5000}")
    public void deliverDueCallbacks() {
        if (!callbackEnabled) {
            return;
        }
        hisIntegrationDao.recoverStaleCallbacks(
                LocalDateTime.now().minusSeconds(Math.max(processingTimeoutSeconds, 30)));
        for (HisCallbackEvent event : hisIntegrationDao.findDueCallbacks(20)) {
            if (hisIntegrationDao.markCallbackProcessing(event.getId()) <= 0) {
                continue;
            }
            try {
                String response = deliver(event);
                hisIntegrationDao.markCallbackSent(event.getId(), response);
            } catch (Exception e) {
                int attempts = event.getAttemptCount() + 1;
                boolean failed = attempts >= 5;
                long delay = RETRY_SECONDS[Math.min(Math.max(attempts - 1, 0), RETRY_SECONDS.length - 1)];
                hisIntegrationDao.markCallbackRetry(event.getId(), attempts,
                        LocalDateTime.now().plusSeconds(delay), errorMessage(e), failed);
            }
        }
    }

    private String deliver(HisCallbackEvent event) throws Exception {
        if (!"rest".equalsIgnoreCase(mode)) {
            return "{\"ack\":true,\"mode\":\"mock\",\"receivedEventId\":\"" + event.getEventId() + "\"}";
        }
        if (!StringUtils.hasText(callbackUrl)) {
            throw new IllegalStateException("未配置HIS状态回传地址");
        }
        URI target = URI.create(callbackUrl);
        if (!allowInsecureHttp && !"https".equalsIgnoreCase(target.getScheme())) {
            throw new IllegalStateException("真实HIS回传地址必须使用HTTPS");
        }
        String timestamp = String.valueOf(java.time.Instant.now().getEpochSecond());
        String nonce = event.getEventId();
        String requestTarget = StringUtils.hasText(target.getRawPath()) ? target.getRawPath() : "/";
        if (StringUtils.hasText(target.getRawQuery())) {
            requestTarget += "?" + target.getRawQuery();
        }
        String signature = hisApiKeyService.signRequest(timestamp, nonce, requestTarget, event.getPayloadJson());
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(Math.max(connectTimeoutMs, 500)))
                .build();
        HttpRequest request = HttpRequest.newBuilder(target)
                .timeout(Duration.ofMillis(Math.max(readTimeoutMs, 1000)))
                .header("Content-Type", "application/json")
                .header("X-HIS-Key", apiKey)
                .header("X-HIS-Timestamp", timestamp)
                .header("X-HIS-Nonce", nonce)
                .header("X-HIS-Signature", signature)
                .POST(HttpRequest.BodyPublishers.ofString(event.getPayloadJson()))
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("HIS返回HTTP " + response.statusCode() + ": " + response.body());
        }
        return response.body();
    }

    private String errorMessage(Exception e) {
        String message = e.getMessage();
        return StringUtils.hasText(message) ? message : e.getClass().getSimpleName();
    }
}
