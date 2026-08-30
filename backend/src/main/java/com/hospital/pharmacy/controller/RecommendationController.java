package com.hospital.pharmacy.controller;

import com.hospital.pharmacy.dao.DrugDao;
import com.hospital.pharmacy.entity.DrugStock;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.Resource;
import java.util.List;

@RestController
public class RecommendationController {

    @Resource
    private DrugDao drugDao;

    @GetMapping("/recommend/fifo")
    public List<DrugStock> fifo(@RequestParam("drugName") String drugName,
                                @RequestParam(value = "limit", defaultValue = "10") int limit) {
        if (!StringUtils.hasText(drugName)) {
            throw new IllegalArgumentException("药品名称不能为空");
        }
        return drugDao.findFifoCandidates(drugName.trim(), Math.max(1, Math.min(limit, 50)));
    }
}
