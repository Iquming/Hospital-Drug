package com.hospital.pharmacy.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

@Service
public class TokenService {

    private static final String HMAC_SHA256 = "HmacSHA256";

    @Value("${app.security.token-secret}")
    private String tokenSecret;

    @Value("${app.security.token-expire-hours}")
    private long expireHours;

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
