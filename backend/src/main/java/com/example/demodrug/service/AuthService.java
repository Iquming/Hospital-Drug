package com.example.demodrug.service;

import com.example.demodrug.dao.SysUserDao;
import com.example.demodrug.entity.SysUser;
import com.example.demodrug.security.CurrentUser;
import com.example.demodrug.security.TokenService;
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

    public Map<String, Object> login(String username, String password) {
        String normalizedUsername = requireText(username, "用户名不能为空");
        String normalizedPassword = requireText(password, "密码不能为空");

        SysUser user = sysUserDao.findByUsername(normalizedUsername);
        if (user == null || !"ENABLED".equals(user.getStatus())) {
            throw new IllegalArgumentException("用户名或密码错误");
        }
        if (!passwordEncoder.matches(normalizedPassword, user.getPasswordHash())) {
            throw new IllegalArgumentException("用户名或密码错误");
        }

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
