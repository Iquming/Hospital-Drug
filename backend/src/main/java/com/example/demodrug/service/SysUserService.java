package com.example.demodrug.service;

import com.example.demodrug.dao.SysUserDao;
import com.example.demodrug.entity.SysUser;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import jakarta.annotation.Resource;
import java.util.List;
import java.util.Set;

@Service
public class SysUserService {

    private static final Set<String> ROLES = Set.of("ADMIN", "PHARMACIST", "NURSE");
    private static final Set<String> STATUSES = Set.of("ENABLED", "DISABLED");

    @Resource
    private SysUserDao sysUserDao;

    @Resource
    private PasswordEncoder passwordEncoder;

    public List<SysUser> listUsers() {
        return sysUserDao.findAll();
    }

    public void createUser(SysUser user, String rawPassword) {
        validateUser(user);
        String password = requireText(rawPassword, "密码不能为空");
        if (password.length() < 6) {
            throw new IllegalArgumentException("密码至少 6 位");
        }
        if (sysUserDao.findByUsername(user.getUsername().trim()) != null) {
            throw new IllegalArgumentException("用户名已存在");
        }
        user.setUsername(user.getUsername().trim());
        user.setPasswordHash(passwordEncoder.encode(password));
        sysUserDao.createUser(user);
    }

    public void updateUser(Long id, SysUser user) {
        if (sysUserDao.findById(id) == null) {
            throw new IllegalArgumentException("用户不存在");
        }
        validateUser(user);
        int updated = sysUserDao.updateUser(id, user);
        if (updated <= 0) {
            throw new IllegalStateException("用户更新失败");
        }
    }

    public void resetPassword(Long id, String rawPassword) {
        if (sysUserDao.findById(id) == null) {
            throw new IllegalArgumentException("用户不存在");
        }
        String password = requireText(rawPassword, "密码不能为空");
        if (password.length() < 6) {
            throw new IllegalArgumentException("密码至少 6 位");
        }
        sysUserDao.updatePassword(id, passwordEncoder.encode(password));
    }

    public void disableUser(Long id) {
        if (sysUserDao.disableUser(id) <= 0) {
            throw new IllegalArgumentException("用户不存在");
        }
    }

    public void deleteUser(Long id, Long currentUserId) {
        if (id != null && id.equals(currentUserId)) {
            throw new IllegalArgumentException("不能删除当前登录用户");
        }
        if (sysUserDao.deleteUser(id) <= 0) {
            throw new IllegalArgumentException("用户不存在");
        }
    }

    private void validateUser(SysUser user) {
        if (user == null) {
            throw new IllegalArgumentException("用户信息不能为空");
        }
        requireText(user.getUsername(), "用户名不能为空");
        user.setDisplayName(requireText(user.getDisplayName(), "姓名不能为空"));
        user.setRole(normalizeRole(user.getRole()));
        user.setStatus(normalizeStatus(user.getStatus()));
        if (StringUtils.hasText(user.getDepartment())) {
            user.setDepartment(user.getDepartment().trim());
        }
    }

    private String normalizeRole(String role) {
        String value = requireText(role, "角色不能为空").toUpperCase();
        if (!ROLES.contains(value)) {
            throw new IllegalArgumentException("角色不合法");
        }
        return value;
    }

    private String normalizeStatus(String status) {
        String value = StringUtils.hasText(status) ? status.trim().toUpperCase() : "ENABLED";
        if (!STATUSES.contains(value)) {
            throw new IllegalArgumentException("状态不合法");
        }
        return value;
    }

    private String requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }
}
