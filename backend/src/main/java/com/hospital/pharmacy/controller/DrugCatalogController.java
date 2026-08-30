package com.hospital.pharmacy.controller;

import com.hospital.pharmacy.entity.DrugCatalog;
import com.hospital.pharmacy.service.DrugCatalogService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import java.util.List;

@RestController
@RequestMapping("/catalog")
public class DrugCatalogController {

    @Resource
    private DrugCatalogService drugCatalogService;

    @GetMapping
    public List<DrugCatalog> list() {
        return drugCatalogService.list();
    }

    @PostMapping
    public ResponseEntity<String> create(@RequestBody DrugCatalog catalog) {
        try {
            drugCatalogService.create(catalog);
            return ResponseEntity.ok("药品档案已创建");
        } catch (Exception e) {
            return fail(e);
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<String> update(@PathVariable Long id, @RequestBody DrugCatalog catalog) {
        try {
            drugCatalogService.update(id, catalog);
            return ResponseEntity.ok("药品档案已更新");
        } catch (Exception e) {
            return fail(e);
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<String> disable(@PathVariable Long id) {
        try {
            drugCatalogService.disable(id);
            return ResponseEntity.ok("药品档案已停用");
        } catch (Exception e) {
            return fail(e);
        }
    }

    private ResponseEntity<String> fail(Exception e) {
        HttpStatus status = e instanceof IllegalArgumentException ? HttpStatus.BAD_REQUEST : HttpStatus.CONFLICT;
        return ResponseEntity.status(status).body(e.getMessage());
    }
}
