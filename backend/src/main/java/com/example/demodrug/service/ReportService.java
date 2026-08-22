package com.example.demodrug.service;

import com.example.demodrug.dao.AuditLogDao;
import com.example.demodrug.dao.DrugDao;
import com.example.demodrug.dao.InventoryCheckDao;
import com.example.demodrug.entity.AuditLog;
import com.example.demodrug.entity.DispenseRecord;
import com.example.demodrug.entity.InventoryCheckItem;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.List;

@Service
public class ReportService {

    @Resource
    private DrugDao drugDao;

    @Resource
    private AuditLogDao auditLogDao;

    @Resource
    private InventoryCheckDao inventoryCheckDao;

    public String dispenseCsv() {
        StringBuilder csv = new StringBuilder("\uFEFF时间,药品,追溯码,患者/业务,患者ID,母码,子码,数量,单位,类型\n");
        List<DispenseRecord> records = drugDao.getAllRecords();
        for (DispenseRecord r : records) {
            csv.append(row(r.getDispenseTime(), r.getDrugName(), r.getTraceCode(), r.getPatientName(), r.getPatientId(),
                    r.getParentTraceCode(), r.getChildTraceCode(), r.getDispenseUnits(), r.getDispenseUnit(), r.getDispenseType()));
        }
        return csv.toString();
    }

    public String auditCsv() {
        StringBuilder csv = new StringBuilder("\uFEFF时间,操作人,角色,动作,对象类型,对象编号,结果,说明\n");
        List<AuditLog> logs = auditLogDao.findRecent(1000);
        for (AuditLog log : logs) {
            csv.append(row(log.getCreateTime(), log.getOperatorName(), log.getOperatorRole(), log.getAction(),
                    log.getTargetType(), log.getTargetId(), log.getResult(), log.getMessage()));
        }
        return csv.toString();
    }

    public String inventoryCsv(Long checkId) {
        StringBuilder csv = new StringBuilder("\uFEFF追溯码,码类型,药品,系统状态,实际状态,差异类型,扫描人,扫描时间\n");
        List<InventoryCheckItem> items = inventoryCheckDao.findItems(checkId);
        for (InventoryCheckItem item : items) {
            csv.append(row(item.getTraceCode(), item.getCodeType(), item.getDrugName(), item.getExpectedStatus(),
                    item.getActualStatus(), item.getDifferenceType(), item.getScannedBy(), item.getScanTime()));
        }
        return csv.toString();
    }

    private String row(Object... values) {
        StringBuilder row = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                row.append(',');
            }
            row.append(cell(values[i]));
        }
        return row.append('\n').toString();
    }

    private String cell(Object value) {
        String text = value == null ? "" : String.valueOf(value);
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }
}
