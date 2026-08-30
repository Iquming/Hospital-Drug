package com.hospital.pharmacy.dao;

import com.hospital.pharmacy.entity.SysUser;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import jakarta.annotation.Resource;
import java.util.List;

@Repository
public class SysUserDao {

    @Resource
    private JdbcTemplate jdbcTemplate;

    public SysUser findByUsername(String username) {
        try {
            String sql = "SELECT * FROM sys_user WHERE username = ? LIMIT 1";
            return jdbcTemplate.queryForObject(sql, new BeanPropertyRowMapper<>(SysUser.class), username);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    public SysUser findById(Long id) {
        try {
            String sql = "SELECT * FROM sys_user WHERE id = ? LIMIT 1";
            return jdbcTemplate.queryForObject(sql, new BeanPropertyRowMapper<>(SysUser.class), id);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    public List<SysUser> findAll() {
        String sql = "SELECT * FROM sys_user ORDER BY status ASC, id DESC";
        return jdbcTemplate.query(sql, new BeanPropertyRowMapper<>(SysUser.class));
    }

    public void createUser(SysUser user) {
        String sql = "INSERT INTO sys_user (username, password_hash, display_name, role, department, status, create_time, update_time) " +
                "VALUES (?, ?, ?, ?, ?, ?, NOW(), NOW())";
        jdbcTemplate.update(sql,
                user.getUsername(),
                user.getPasswordHash(),
                user.getDisplayName(),
                normalizeRole(user.getRole()),
                user.getDepartment(),
                normalizeStatus(user.getStatus()));
    }

    public int updateUser(Long id, SysUser user) {
        String sql = "UPDATE sys_user SET display_name = ?, role = ?, department = ?, status = ?, update_time = NOW() WHERE id = ?";
        return jdbcTemplate.update(sql,
                user.getDisplayName(),
                normalizeRole(user.getRole()),
                user.getDepartment(),
                normalizeStatus(user.getStatus()),
                id);
    }

    public int updatePassword(Long id, String passwordHash) {
        String sql = "UPDATE sys_user SET password_hash = ?, update_time = NOW() WHERE id = ?";
        return jdbcTemplate.update(sql, passwordHash, id);
    }

    public int disableUser(Long id) {
        String sql = "UPDATE sys_user SET status = 'DISABLED', update_time = NOW() WHERE id = ?";
        return jdbcTemplate.update(sql, id);
    }

    public int deleteUser(Long id) {
        String sql = "DELETE FROM sys_user WHERE id = ?";
        return jdbcTemplate.update(sql, id);
    }

    public void updateLastLogin(Long id) {
        jdbcTemplate.update("UPDATE sys_user SET last_login_time = NOW(), update_time = NOW() WHERE id = ?", id);
    }

    private String normalizeRole(String role) {
        return StringUtils.hasText(role) ? role.trim().toUpperCase() : "NURSE";
    }

    private String normalizeStatus(String status) {
        return StringUtils.hasText(status) ? status.trim().toUpperCase() : "ENABLED";
    }
}
