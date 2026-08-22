package com.example.demodrug.controller;

import com.example.demodrug.entity.AuditLog;
import com.example.demodrug.service.AuditLogService;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import java.util.List;

@RestController
@RequestMapping("/audit")
public class AuditLogController {

    @Resource
    private AuditLogService auditLogService;

    @GetMapping("/recent")
    public List<AuditLog> recent(@RequestParam(value = "limit", defaultValue = "50") int limit) {
        return auditLogService.recent(limit);
    }
}
