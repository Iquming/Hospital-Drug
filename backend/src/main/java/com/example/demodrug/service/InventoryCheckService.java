package com.example.demodrug.service;

import com.example.demodrug.dao.DrugDao;
import com.example.demodrug.dao.InventoryCheckDao;
import com.example.demodrug.constant.SplitCodeStatus;
import com.example.demodrug.constant.StockStatus;
import com.example.demodrug.entity.DrugSplitCode;
import com.example.demodrug.entity.DrugStock;
import com.example.demodrug.entity.InventoryCheck;
import com.example.demodrug.entity.InventoryCheckItem;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class InventoryCheckService {

    private static final DateTimeFormatter CHECK_NO_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    @Resource
    private InventoryCheckDao inventoryCheckDao;

    @Resource
    private DrugDao drugDao;

    @Resource
    private AuditLogService auditLogService;

    public List<InventoryCheck> list(int limit) {
        return inventoryCheckDao.findRecent(Math.max(1, Math.min(limit, 100)));
    }

    @Transactional(rollbackFor = Exception.class)
    public InventoryCheck create(String title, String operator) {
        InventoryCheck check = new InventoryCheck();
        check.setCheckNo("CHK-" + LocalDateTime.now().format(CHECK_NO_FORMAT));
        check.setTitle(StringUtils.hasText(title) ? title.trim() : "库存盘点");
        check.setCreatedBy(operator);
        inventoryCheckDao.create(check);
        auditLogService.record("INVENTORY_CREATE", "inventory_check", check.getCheckNo(), null, check.getTitle(), "SUCCESS", "创建盘点单");
        return check;
    }

    @Transactional(rollbackFor = Exception.class)
    public InventoryCheckItem scan(Long checkId, String traceCode, String operator) {
        InventoryCheck check = requireOpenCheck(checkId);
        String code = requireText(traceCode, "追溯码不能为空");

        InventoryCheckItem item = new InventoryCheckItem();
        item.setCheckId(check.getId());
        item.setTraceCode(code);
        item.setScannedBy(operator);

        DrugSplitCode splitCode = drugDao.getSplitByChildTraceCode(code);
        if (splitCode != null) {
            item.setCodeType("CHILD");
            item.setDrugName(splitCode.getDrugName());
            item.setExpectedStatus("SPLIT_" + splitCode.getStatus());
            item.setActualStatus("FOUND");
            item.setDifferenceType(SplitCodeStatus.AVAILABLE.equals(splitCode.getStatus()) ? "MATCH" : "ABNORMAL");
            inventoryCheckDao.upsertItem(item);
            auditLogService.record("INVENTORY_SCAN", "drug_split_code", code, null, item.getDifferenceType(), "SUCCESS", "盘点扫描子码");
            return item;
        }

        DrugStock drug = drugDao.getDrugByTraceCode(code);
        if (drug == null) {
            item.setCodeType("UNKNOWN");
            item.setExpectedStatus("NOT_IN_SYSTEM");
            item.setActualStatus("FOUND");
            item.setDifferenceType("SURPLUS");
        } else {
            item.setCodeType("PARENT");
            item.setDrugName(drug.getDrugName());
            item.setExpectedStatus(drug.getQuantity() != null && drug.getQuantity() > 0 ? StockStatus.IN_STOCK : "OUT_STOCK");
            item.setActualStatus("FOUND");
            item.setDifferenceType(drug.getQuantity() != null && drug.getQuantity() > 0 ? "MATCH" : "SURPLUS");
        }

        inventoryCheckDao.upsertItem(item);
        auditLogService.record("INVENTORY_SCAN", "drug_stock", code, null, item.getDifferenceType(), "SUCCESS", "盘点扫描母码");
        return item;
    }

    public List<InventoryCheckItem> items(Long checkId) {
        return inventoryCheckDao.findItems(checkId);
    }

    @Transactional(rollbackFor = Exception.class)
    public void complete(Long checkId, String operator) {
        requireOpenCheck(checkId);
        int rows = inventoryCheckDao.complete(checkId, operator);
        if (rows <= 0) {
            throw new IllegalStateException("盘点单已完成或状态异常");
        }
        auditLogService.record("INVENTORY_COMPLETE", "inventory_check", String.valueOf(checkId), "OPEN", "COMPLETED", "SUCCESS", "完成盘点");
    }

    private InventoryCheck requireOpenCheck(Long checkId) {
        if (checkId == null) {
            throw new IllegalArgumentException("盘点单编号不能为空");
        }
        InventoryCheck check = inventoryCheckDao.findById(checkId);
        if (check == null) {
            throw new IllegalArgumentException("盘点单不存在");
        }
        if (!"OPEN".equals(check.getStatus())) {
            throw new IllegalStateException("盘点单已完成，不能继续扫描");
        }
        return check;
    }

    private String requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }
}
