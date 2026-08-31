package com.hospital.pharmacy.service;

import com.hospital.pharmacy.dao.SysUserDao;
import com.hospital.pharmacy.entity.SysUser;
import com.hospital.pharmacy.security.CurrentUser;
import com.hospital.pharmacy.security.TokenService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import jakarta.annotation.Resource;
import java.util.HashMap;
import java.util.Map;

@Service
public class AuthService {

    @Resource
    private SysUserDao sysUserDao;

    @Resource
    private PasswordEncoder passwordEncoder;

    @Resource
    private TokenService tokenService;

    @Resource
    private AuditLogService auditLogService;

    @Resource
    private LoginGuardService loginGuardService;

    public Map<String, Object> login(String username, String password, String clientAddress) {
        String normalizedUsername = requireText(username, "用户名不能为空");
        String normalizedPassword = requireText(password, "密码不能为空");
        loginGuardService.requireAllowed(normalizedUsername, clientAddress);

        SysUser user = sysUserDao.findByUsername(normalizedUsername);
        if (user == null || !"ENABLED".equals(user.getStatus())) {
            loginGuardService.recordFailure(normalizedUsername, clientAddress);
            auditLogService.record("LOGIN_FAILED", "sys_user", normalizedUsername, null, null, "FAILED", "登录失败");
            throw new IllegalArgumentException("用户名或密码错误");
        }
        if (!passwordEncoder.matches(normalizedPassword, user.getPasswordHash())) {
            loginGuardService.recordFailure(normalizedUsername, clientAddress);
            auditLogService.record("LOGIN_FAILED", "sys_user", normalizedUsername, null, null, "FAILED", "登录失败");
            throw new IllegalArgumentException("用户名或密码错误");
        }

        loginGuardService.clear(normalizedUsername, clientAddress);
        sysUserDao.updateLastLogin(user.getId());
        CurrentUser currentUser = toCurrentUser(user);
        auditLogService.recordUser(user.getId(), user.getDisplayName(), user.getRole(), "LOGIN",
                "sys_user", String.valueOf(user.getId()), null, null, "SUCCESS", "用户登录");
        Map<String, Object> result = new HashMap<>();
        result.put("token", tokenService.generate(currentUser));
        result.put("user", currentUser);
        return result;
    }

    public CurrentUser toCurrentUser(SysUser user) {
        return new CurrentUser(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getRole(),
                user.getDepartment()
        );
    }

    private String requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }
}
