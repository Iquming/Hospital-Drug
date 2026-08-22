package com.example.demodrug.dao;

import com.example.demodrug.entity.AuditLog;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import jakarta.annotation.Resource;
import java.util.List;

@Repository
public class AuditLogDao {

    @Resource
    private JdbcTemplate jdbcTemplate;

    public void save(AuditLog log) {
        String sql = "INSERT INTO audit_log (operator_id, operator_name, operator_role, action, target_type, target_id, " +
                "request_path, client_ip, before_state, after_state, result, message, create_time) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW())";
        jdbcTemplate.update(sql,
                log.getOperatorId(),
                log.getOperatorName(),
                log.getOperatorRole(),
                log.getAction(),
                log.getTargetType(),
                log.getTargetId(),
                log.getRequestPath(),
                log.getClientIp(),
                log.getBeforeState(),
                log.getAfterState(),
                log.getResult(),
                log.getMessage());
    }

    public List<AuditLog> findRecent(int limit) {
        String sql = "SELECT * FROM audit_log ORDER BY create_time DESC LIMIT ?";
        return jdbcTemplate.query(sql, new BeanPropertyRowMapper<>(AuditLog.class), limit);
    }
}
