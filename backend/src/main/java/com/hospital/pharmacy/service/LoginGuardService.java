package com.hospital.pharmacy.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class LoginGuardService {

    private static final int MAX_FAILURES = 5;
    private static final int WINDOW_MINUTES = 15;

    @Resource
    private JdbcTemplate jdbcTemplate;

    public void requireAllowed(String username, String clientAddress) {
        String key = guardKey(username, clientAddress);
        List<LocalDateTime> locks = jdbcTemplate.query(
                "SELECT locked_until FROM auth_login_guard WHERE guard_key = ? AND locked_until > NOW()",
                (rs, rowNum) -> rs.getTimestamp(1).toLocalDateTime(), key);
        if (!locks.isEmpty()) {
            throw new SecurityException("登录尝试过多，请稍后再试");
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void recordFailure(String username, String clientAddress) {
        String key = guardKey(username, clientAddress);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT failure_count, first_failure_time FROM auth_login_guard WHERE guard_key = ? FOR UPDATE", key);
        LocalDateTime now = LocalDateTime.now();
        int failures = 1;
        LocalDateTime firstFailure = now;
        if (!rows.isEmpty()) {
            Map<String, Object> row = rows.get(0);
            LocalDateTime storedFirst = row.get("first_failure_time") instanceof java.sql.Timestamp timestamp
                    ? timestamp.toLocalDateTime() : null;
            if (storedFirst != null && storedFirst.isAfter(now.minusMinutes(WINDOW_MINUTES))) {
                failures = ((Number) row.get("failure_count")).intValue() + 1;
                firstFailure = storedFirst;
            }
        }
        LocalDateTime lockedUntil = failures >= MAX_FAILURES ? now.plusMinutes(WINDOW_MINUTES) : null;
        jdbcTemplate.update("INSERT INTO auth_login_guard (guard_key, failure_count, first_failure_time, locked_until, update_time) " +
                        "VALUES (?, ?, ?, ?, NOW()) ON DUPLICATE KEY UPDATE failure_count = VALUES(failure_count), " +
                        "first_failure_time = VALUES(first_failure_time), locked_until = VALUES(locked_until), update_time = NOW()",
                key, failures, firstFailure, lockedUntil);
    }

    public void clear(String username, String clientAddress) {
        jdbcTemplate.update("DELETE FROM auth_login_guard WHERE guard_key = ?", guardKey(username, clientAddress));
    }

    private String guardKey(String username, String clientAddress) {
        String user = StringUtils.hasText(username) ? username.trim().toLowerCase() : "-";
        String client = StringUtils.hasText(clientAddress) ? clientAddress.trim() : "-";
        return (user + "|" + client).substring(0, Math.min(220, user.length() + client.length() + 1));
    }
}
