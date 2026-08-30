package com.hospital.pharmacy.controller;

import com.hospital.pharmacy.entity.InventoryCheck;
import com.hospital.pharmacy.entity.InventoryCheckItem;
import com.hospital.pharmacy.security.SecurityUtils;
import com.hospital.pharmacy.service.InventoryCheckService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/inventory")
public class InventoryCheckController {

    @Resource
    private InventoryCheckService inventoryCheckService;

    @GetMapping
    public List<InventoryCheck> list(@RequestParam(value = "limit", defaultValue = "20") int limit) {
        return inventoryCheckService.list(limit);
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody Map<String, String> payload) {
        try {
            return ResponseEntity.ok(inventoryCheckService.create(payload.get("title"), SecurityUtils.currentUser().operatorLabel()));
        } catch (Exception e) {
            return fail(e);
        }
    }

    @PostMapping("/{id}/scan")
    public ResponseEntity<?> scan(@PathVariable("id") Long id, @RequestBody Map<String, String> payload) {
        try {
            return ResponseEntity.ok(inventoryCheckService.scan(id, payload.get("traceCode"), SecurityUtils.currentUser().operatorLabel()));
        } catch (Exception e) {
            return fail(e);
        }
    }

    @GetMapping("/{id}/items")
    public List<InventoryCheckItem> items(@PathVariable("id") Long id) {
        return inventoryCheckService.items(id);
    }

    @PostMapping("/{id}/complete")
    public ResponseEntity<?> complete(@PathVariable("id") Long id) {
        try {
            inventoryCheckService.complete(id, SecurityUtils.currentUser().operatorLabel());
            return ResponseEntity.ok(Map.of("message", "盘点完成"));
        } catch (Exception e) {
            return fail(e);
        }
    }

    private ResponseEntity<String> fail(Exception e) {
        HttpStatus status = e instanceof IllegalArgumentException ? HttpStatus.BAD_REQUEST : HttpStatus.CONFLICT;
        return ResponseEntity.status(status).body(e.getMessage());
    }
}
