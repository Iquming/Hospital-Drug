package com.hospital.pharmacy.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DuplicateKeyException;

import jakarta.annotation.Resource;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HexFormat;

@Service
public class HisApiKeyService {

    @Value("${app.his.api-key:his-demo-key}")
    private String configuredApiKey;

    @Value("${app.his.mode:mock}")
    private String mode;

    @Value("${app.his.signature-max-skew-seconds:300}")
    private long maxSkewSeconds;

    @Resource
    private JdbcTemplate jdbcTemplate;

    public void requireValid(String suppliedApiKey) {
        if (!StringUtils.hasText(suppliedApiKey) || !constantTimeEquals(suppliedApiKey, configuredApiKey)) {
            throw new SecurityException("HIS接口密钥无效");
        }
    }

    public void requireValid(String suppliedApiKey, String timestamp, String nonce,
                             String signature, String requestPath, String requestBody) {
        requireValid(suppliedApiKey);
        if (!"rest".equalsIgnoreCase(mode)) {
            return;
        }
        if ("his-demo-key".equals(configuredApiKey)) {
            throw new SecurityException("真实HIS模式禁止使用默认接口密钥");
        }
        if (!StringUtils.hasText(timestamp) || !StringUtils.hasText(nonce)
                || !StringUtils.hasText(signature) || !StringUtils.hasText(requestPath)
                || !StringUtils.hasText(requestBody)) {
            throw new SecurityException("真实HIS请求缺少时间戳、随机数或签名");
        }
        long requestEpoch;
        try {
            requestEpoch = Long.parseLong(timestamp.trim());
        } catch (NumberFormatException e) {
            throw new SecurityException("HIS请求时间戳无效");
        }
        if (Math.abs(Instant.now().getEpochSecond() - requestEpoch) > Math.max(maxSkewSeconds, 30)) {
            throw new SecurityException("HIS请求时间戳已过期");
        }
        String expected = hmac(timestamp.trim() + "\n" + nonce.trim() + "\n"
                + requestPath.trim() + "\n" + sha256(requestBody));
        if (!constantTimeEquals(signature.trim().toLowerCase(), expected)) {
            throw new SecurityException("HIS请求签名无效");
        }
        try {
            jdbcTemplate.update("INSERT INTO his_request_nonce (nonce_value, request_time, create_time) VALUES (?, ?, NOW())",
                    nonce.trim(), LocalDateTime.now());
            jdbcTemplate.update("DELETE FROM his_request_nonce WHERE create_time < DATE_SUB(NOW(), INTERVAL 1 DAY)");
        } catch (DuplicateKeyException e) {
            throw new SecurityException("HIS请求随机数已使用，拒绝重放");
        }
    }

    public String signRequest(String timestamp, String nonce, String requestPath, String requestBody) {
        return hmac(timestamp + "\n" + nonce + "\n" + requestPath + "\n" + sha256(requestBody));
    }

    private String hmac(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(configuredApiKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("HIS签名校验失败", e);
        }
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("HIS报文摘要生成失败", e);
        }
    }

    private boolean constantTimeEquals(String left, String right) {
        if (left == null || right == null) {
            return false;
        }
        return MessageDigest.isEqual(left.getBytes(StandardCharsets.UTF_8), right.getBytes(StandardCharsets.UTF_8));
    }
}
