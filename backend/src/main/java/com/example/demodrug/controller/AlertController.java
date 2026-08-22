package com.example.demodrug.controller;

import com.example.demodrug.service.AlertService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.Resource;
import java.util.Map;

@RestController
public class AlertController {

    @Resource
    private AlertService alertService;

    @GetMapping("/alerts/enhanced")
    public Map<String, Object> enhancedAlerts() {
        return alertService.enhancedAlerts();
    }
}
