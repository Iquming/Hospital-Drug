package com.example.demodrug.controller;

import com.example.demodrug.service.ReportService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.Resource;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/reports")
public class ReportController {

    @Resource
    private ReportService reportService;

    @GetMapping("/dispense.csv")
    public ResponseEntity<byte[]> dispense() {
        return csv("dispense-records.csv", reportService.dispenseCsv());
    }

    @GetMapping("/audit.csv")
    public ResponseEntity<byte[]> audit() {
        return csv("audit-logs.csv", reportService.auditCsv());
    }

    @GetMapping("/inventory/{id}.csv")
    public ResponseEntity<byte[]> inventory(@PathVariable("id") Long id) {
        return csv("inventory-" + id + ".csv", reportService.inventoryCsv(id));
    }

    private ResponseEntity<byte[]> csv(String fileName, String content) {
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .body(content.getBytes(StandardCharsets.UTF_8));
    }
}
