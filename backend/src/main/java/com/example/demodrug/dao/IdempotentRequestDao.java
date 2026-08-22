package com.example.demodrug.dao;

import com.example.demodrug.entity.IdempotentRequest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import jakarta.annotation.Resource;
import java.util.List;

@Repository
public class IdempotentRequestDao {

    @Resource
    private JdbcTemplate jdbcTemplate;

    public boolean createProcessing(String requestId, String action, String targetId, String requestHash, String operatorName) {
        String sql = "INSERT INTO idempotent_request (request_id, action, target_id, request_hash, status, operator_name, create_time, update_time) " +
                "VALUES (?, ?, ?, ?, 'PROCESSING', ?, NOW(), NOW())";
        try {
            return jdbcTemplate.update(sql, requestId, action, targetId, requestHash, operatorName) > 0;
        } catch (DuplicateKeyException e) {
            return false;
        }
    }

    public IdempotentRequest findByRequestId(String requestId) {
        String sql = "SELECT * FROM idempotent_request WHERE request_id = ? LIMIT 1";
        List<IdempotentRequest> list = jdbcTemplate.query(sql, new BeanPropertyRowMapper<>(IdempotentRequest.class), requestId);
        return list.isEmpty() ? null : list.get(0);
    }

    public void markSuccess(String requestId, String responseBody) {
        String sql = "UPDATE idempotent_request SET status = 'SUCCESS', response_body = ?, update_time = NOW() WHERE request_id = ?";
        jdbcTemplate.update(sql, responseBody, requestId);
    }

    public void markFailed(String requestId, String responseBody) {
        String sql = "UPDATE idempotent_request SET status = 'FAILED', response_body = ?, update_time = NOW() WHERE request_id = ?";
        jdbcTemplate.update(sql, responseBody, requestId);
    }
}
