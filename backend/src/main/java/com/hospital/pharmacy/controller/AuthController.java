package com.hospital.pharmacy.controller;

import com.hospital.pharmacy.security.SecurityUtils;
import com.hospital.pharmacy.service.AuthService;
import com.hospital.pharmacy.security.TokenService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthController {

    @Resource
    private AuthService authService;

    @Resource
    private TokenService tokenService;

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> payload, HttpServletRequest request) {
        try {
            return ResponseEntity.ok(authService.login(payload.get("username"), payload.get("password"), request.getRemoteAddr()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("用户名或密码错误");
        }
    }

    @GetMapping("/me")
    public Map<String, Object> me() {
        return Map.of("user", SecurityUtils.currentUser());
    }

    @PostMapping("/logout")
    public Map<String, String> logout(@RequestHeader("Authorization") String authorization) {
        if (authorization != null && authorization.startsWith("Bearer ")) {
            tokenService.revoke(authorization.substring(7));
        }
        return Map.of("message", "退出成功");
    }
}
