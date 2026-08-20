package com.example.demodrug.controller;

import com.example.demodrug.security.SecurityUtils;
import com.example.demodrug.service.AuthService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthController {

    @Resource
    private AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> payload) {
        try {
            return ResponseEntity.ok(authService.login(payload.get("username"), payload.get("password")));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("用户名或密码错误");
        }
    }

    @GetMapping("/me")
    public Map<String, Object> me() {
        return Map.of("user", SecurityUtils.currentUser());
    }

    @PostMapping("/logout")
    public Map<String, String> logout() {
        return Map.of("message", "退出成功");
    }
}
