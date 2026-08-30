package com.hospital.pharmacy.controller;

import com.hospital.pharmacy.entity.SysUser;
import com.hospital.pharmacy.security.SecurityUtils;
import com.hospital.pharmacy.service.SysUserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/users")
public class UserController {

    @Resource
    private SysUserService sysUserService;

    @GetMapping
    public List<SysUser> listUsers() {
        return sysUserService.listUsers();
    }

    @PostMapping
    public ResponseEntity<String> createUser(@RequestBody Map<String, String> payload) {
        try {
            SysUser user = toUser(payload);
            sysUserService.createUser(user, payload.get("password"));
            return ResponseEntity.ok("用户创建成功");
        } catch (Exception e) {
            return fail(e);
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<String> updateUser(@PathVariable Long id, @RequestBody Map<String, String> payload) {
        try {
            sysUserService.updateUser(id, toUser(payload));
            return ResponseEntity.ok("用户更新成功");
        } catch (Exception e) {
            return fail(e);
        }
    }

    @PutMapping("/{id}/password")
    public ResponseEntity<String> resetPassword(@PathVariable Long id, @RequestBody Map<String, String> payload) {
        try {
            sysUserService.resetPassword(id, payload.get("password"));
            return ResponseEntity.ok("密码已重置");
        } catch (Exception e) {
            return fail(e);
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<String> disableUser(@PathVariable Long id) {
        try {
            sysUserService.disableUser(id);
            return ResponseEntity.ok("用户已禁用");
        } catch (Exception e) {
            return fail(e);
        }
    }

    @DeleteMapping("/{id}/hard")
    public ResponseEntity<String> deleteUser(@PathVariable Long id) {
        try {
            sysUserService.deleteUser(id, SecurityUtils.currentUser().id());
            return ResponseEntity.ok("用户已删除");
        } catch (Exception e) {
            return fail(e);
        }
    }

    private SysUser toUser(Map<String, String> payload) {
        SysUser user = new SysUser();
        user.setUsername(payload.get("username"));
        user.setDisplayName(payload.get("displayName"));
        user.setRole(payload.get("role"));
        user.setDepartment(payload.get("department"));
        user.setStatus(payload.get("status"));
        return user;
    }

    private ResponseEntity<String> fail(Exception e) {
        HttpStatus status = e instanceof IllegalArgumentException ? HttpStatus.BAD_REQUEST : HttpStatus.CONFLICT;
        return ResponseEntity.status(status).body(e.getMessage());
    }
}
