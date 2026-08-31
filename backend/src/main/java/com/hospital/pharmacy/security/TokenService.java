package com.hospital.pharmacy.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;

import jakarta.annotation.Resource;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.security.MessageDigest;
import java.util.HexFormat;

@Service
public class TokenService {

    private static final String HMAC_SHA256 = "HmacSHA256";

    @Value("${app.security.token-secret}")
    private String tokenSecret;

    @Value("${app.security.token-expire-hours}")
    private long expireHours;

    @Resource
    private JdbcTemplate jdbcTemplate;

    public String generate(CurrentUser user) {
        long expiresAt = Instant.now().plusSeconds(expireHours * 3600).getEpochSecond();
        String payload = user.id() + ":" + user.username() + ":" + expiresAt;
        String encodedPayload = base64Url(payload.getBytes(StandardCharsets.UTF_8));
        return encodedPayload + "." + sign(encodedPayload);
    }

    public TokenPayload parse(String token) {
        if (token == null || !token.contains(".")) {
            return null;
        }
        String[] parts = token.split("\\.", 2);
        String expectedSignature = sign(parts[0]);
        if (!constantTimeEquals(expectedSignature, parts[1])) {
            return null;
        }
        String payload = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
        String[] values = payload.split(":", 3);
        if (values.length != 3) {
            return null;
        }
        long expiresAt;
        try {
            expiresAt = Long.parseLong(values[2]);
        } catch (NumberFormatException e) {
            return null;
        }
        if (Instant.now().getEpochSecond() > expiresAt) {
            return null;
        }
        try {
            return new TokenPayload(Long.parseLong(values[0]), values[1], expiresAt);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public boolean isRevoked(String token) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM revoked_token WHERE token_hash = ? AND expires_at > NOW()",
                Integer.class, tokenHash(token));
        return count != null && count > 0;
    }

    public void revoke(String token) {
        TokenPayload payload = parse(token);
        if (payload == null) {
            return;
        }
        LocalDateTime expiresAt = LocalDateTime.ofInstant(
                Instant.ofEpochSecond(payload.expiresAt()), ZoneId.systemDefault());
        jdbcTemplate.update("INSERT INTO revoked_token (token_hash, expires_at, revoked_at) VALUES (?, ?, NOW()) " +
                        "ON DUPLICATE KEY UPDATE expires_at = VALUES(expires_at), revoked_at = NOW()",
                tokenHash(token), expiresAt);
        jdbcTemplate.update("DELETE FROM revoked_token WHERE expires_at <= NOW()");
    }

    private String tokenHash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Token摘要生成失败", e);
        }
    }

    private String sign(String value) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(tokenSecret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
            return base64Url(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Token 签名失败", e);
        }
    }

    private String base64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private boolean constantTimeEquals(String left, String right) {
        if (left == null || right == null || left.length() != right.length()) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < left.length(); i++) {
            result |= left.charAt(i) ^ right.charAt(i);
        }
        return result == 0;
    }

    public record TokenPayload(Long userId, String username, long expiresAt) {
    }
}
