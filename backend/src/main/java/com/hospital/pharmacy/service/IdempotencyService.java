package com.hospital.pharmacy.service;

import com.hospital.pharmacy.dao.IdempotentRequestDao;
import com.hospital.pharmacy.entity.IdempotentRequest;
import com.hospital.pharmacy.exception.BusinessException;
import com.hospital.pharmacy.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import jakarta.annotation.Resource;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Supplier;

@Service
public class IdempotencyService {

    @Resource
    private IdempotentRequestDao idempotentRequestDao;

    @Resource
    private AuditLogService auditLogService;

    @Resource
    private ObjectMapper objectMapper;

    public <T> T execute(String requestId,
                         String action,
                         String targetId,
                         String operatorName,
                         Map<String, ?> requestPayload,
                         Supplier<T> operation,
                         Class<T> resultType) {
        String normalizedRequestId = normalizeRequestId(requestId, action, targetId, operatorName);
        String requestHash = hash(requestPayload);
        boolean created = idempotentRequestDao.createProcessing(normalizedRequestId, action, targetId, requestHash, operatorName);
        if (!created) {
            IdempotentRequest existing = idempotentRequestDao.findByRequestId(normalizedRequestId);
            if (existing == null) {
                throw new BusinessException(ErrorCode.IDEMPOTENT_PROCESSING, "请求正在处理中，请稍后重试", normalizedRequestId);
            }
            if (!requestHash.equals(existing.getRequestHash())) {
                auditLogService.record("IDEMPOTENT_CONFLICT", action, targetId, existing.getRequestHash(), requestHash, "FAILED", "幂等号请求摘要冲突");
                throw new BusinessException(ErrorCode.IDEMPOTENT_CONFLICT, "幂等请求号已被不同请求使用", normalizedRequestId);
            }
            if ("SUCCESS".equals(existing.getStatus())) {
                auditLogService.record("IDEMPOTENT_REPLAY", action, targetId, null, existing.getResponseBody(), "SUCCESS", "重复请求返回首次结果");
                return convert(existing.getResponseBody(), resultType);
            }
            if (!"FAILED".equals(existing.getStatus())
                    || !idempotentRequestDao.restartFailed(normalizedRequestId, requestHash)) {
                throw new BusinessException(ErrorCode.IDEMPOTENT_PROCESSING, "请求正在处理中，请稍后重试", normalizedRequestId);
            }
        }

        try {
            T result = operation.get();
            idempotentRequestDao.markSuccess(normalizedRequestId, stringify(result));
            return result;
        } catch (RuntimeException e) {
            idempotentRequestDao.markFailed(normalizedRequestId, e.getMessage());
            throw e;
        }
    }

    public String responseRequestId(String requestId, String action, String targetId, String operatorName) {
        return normalizeRequestId(requestId, action, targetId, operatorName);
    }

    private String normalizeRequestId(String requestId, String action, String targetId, String operatorName) {
        if (StringUtils.hasText(requestId)) {
            return requestId.trim();
        }
        long bucket = Instant.now().getEpochSecond() / 10;
        return action + ":" + safe(targetId) + ":" + safe(operatorName) + ":" + bucket;
    }

    private String hash(Map<String, ?> payload) {
        TreeMap<String, String> normalized = new TreeMap<>();
        if (payload != null) {
            payload.forEach((key, value) -> normalized.put(key, value == null ? "" : String.valueOf(value)));
        }
        return DigestUtils.md5DigestAsHex(normalized.toString().getBytes(StandardCharsets.UTF_8));
    }

    private String stringify(Object value) {
        try {
            return "J:" + base64(objectMapper.writeValueAsString(value));
        } catch (JacksonException e) {
            throw new IllegalStateException("幂等响应序列化失败", e);
        }
    }

    @SuppressWarnings("unchecked")
    private <T> T convert(String value, Class<T> type) {
        if (value != null && value.startsWith("J:")) {
            try {
                return objectMapper.readValue(unbase64(value.substring(2)), type);
            } catch (JacksonException e) {
                throw new IllegalStateException("幂等响应读取失败", e);
            }
        }
        if (String.class.equals(type)) {
            return (T) decodeString(value);
        }
        if (Map.class.equals(type) && value != null && value.startsWith("M:")) {
            Map<String, Object> map = new LinkedHashMap<>();
            String body = value.substring(2);
            if (StringUtils.hasText(body)) {
                for (String pair : body.split("&")) {
                    String[] parts = pair.split("=", 2);
                    if (parts.length == 2) {
                        map.put(unbase64(parts[0]), unbase64(parts[1]));
                    }
                }
            }
            return (T) map;
        }
        throw new BusinessException(ErrorCode.IDEMPOTENT_REPLAY, "该重复请求已成功处理，请刷新页面查看结果");
    }

    private String decodeString(String value) {
        if (value != null && value.startsWith("S:")) {
            return unbase64(value.substring(2));
        }
        return value;
    }

    private String base64(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private String unbase64(String value) {
        return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
    }

    private String safe(String value) {
        return StringUtils.hasText(value) ? value.trim() : "-";
    }
}
