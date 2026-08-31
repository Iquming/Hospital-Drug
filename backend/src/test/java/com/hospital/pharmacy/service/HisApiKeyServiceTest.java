package com.hospital.pharmacy.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

class HisApiKeyServiceTest {

    private HisApiKeyService service;
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        service = new HisApiKeyService();
        jdbcTemplate = mock(JdbcTemplate.class);
        ReflectionTestUtils.setField(service, "configuredApiKey", "a-strong-test-secret");
        ReflectionTestUtils.setField(service, "mode", "rest");
        ReflectionTestUtils.setField(service, "maxSkewSeconds", 300L);
        ReflectionTestUtils.setField(service, "jdbcTemplate", jdbcTemplate);
    }

    @Test
    void acceptsValidSignatureAndRejectsModifiedBody() {
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String nonce = "nonce-001";
        String path = "/api/integration/his/v1/drug-applications";
        String body = "{\"eventId\":\"event-001\"}";
        String signature = service.signRequest(timestamp, nonce, path, body);

        assertDoesNotThrow(() -> service.requireValid(
                "a-strong-test-secret", timestamp, nonce, signature, path, body));
        assertThrows(SecurityException.class, () -> service.requireValid(
                "a-strong-test-secret", timestamp, "nonce-002", signature, path, body + " "));
    }

    @Test
    void rejectsReplayedNonce() {
        String insertSql = "INSERT INTO his_request_nonce (nonce_value, request_time, create_time) VALUES (?, ?, NOW())";
        String nonce = "replayed-nonce";
        doThrow(new DuplicateKeyException("duplicate"))
                .when(jdbcTemplate).update(eq(insertSql), eq(nonce), any(LocalDateTime.class));
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String path = "/api/integration/his/v1/drug-applications";
        String body = "{}";
        String signature = service.signRequest(timestamp, nonce, path, body);

        assertThrows(SecurityException.class, () -> service.requireValid(
                "a-strong-test-secret", timestamp, nonce, signature, path, body));
    }
}
