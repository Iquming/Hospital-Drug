package com.hospital.pharmacy.service;

import com.hospital.pharmacy.dao.AuditLogDao;
import com.hospital.pharmacy.entity.AuditLog;
import com.hospital.pharmacy.security.CurrentUser;
import com.hospital.pharmacy.security.SecurityUtils;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.List;

@Service
public class AuditLogService {

    @Resource
    private AuditLogDao auditLogDao;

    public void record(String action, String targetType, String targetId, String beforeState, String afterState, String result, String message) {
        AuditLog log = new AuditLog();
        try {
            CurrentUser user = SecurityUtils.currentUser();
            log.setOperatorId(user.id());
            log.setOperatorName(user.displayName());
            log.setOperatorRole(user.role());
        } catch (Exception e) {
            log.setOperatorName("anonymous");
        }
        log.setAction(action);
        log.setTargetType(targetType);
        log.setTargetId(targetId);
        log.setBeforeState(beforeState);
        log.setAfterState(afterState);
        log.setResult(result == null ? "SUCCESS" : result);
        log.setMessage(message);
        auditLogDao.save(log);
    }

    public List<AuditLog> recent(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 200));
        return auditLogDao.findRecent(safeLimit);
    }

    public void recordUser(Long operatorId, String operatorName, String operatorRole, String action, String targetType,
                           String targetId, String beforeState, String afterState, String result, String message) {
        AuditLog log = new AuditLog();
        log.setOperatorId(operatorId);
        log.setOperatorName(operatorName);
        log.setOperatorRole(operatorRole);
        log.setAction(action);
        log.setTargetType(targetType);
        log.setTargetId(targetId);
        log.setBeforeState(beforeState);
        log.setAfterState(afterState);
        log.setResult(result == null ? "SUCCESS" : result);
        log.setMessage(message);
        auditLogDao.save(log);
    }
}
