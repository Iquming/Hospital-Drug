package com.hospital.pharmacy.dao;

import com.hospital.pharmacy.dto.HisDtos;
import com.hospital.pharmacy.entity.DrugApplication;
import com.hospital.pharmacy.entity.DrugApplicationItem;
import com.hospital.pharmacy.entity.HisCallbackEvent;
import com.hospital.pharmacy.entity.HisDrugMapping;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import jakarta.annotation.Resource;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Repository
public class HisIntegrationDao {

    @Resource
    private JdbcTemplate jdbcTemplate;

    public String findInboundResponse(String eventId) {
        List<String> rows = jdbcTemplate.query(
                "SELECT response_json FROM his_inbound_event WHERE event_id = ? LIMIT 1",
                (rs, rowNum) -> rs.getString(1), eventId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public void saveInboundEvent(String eventId, Long applicationId, String eventType,
                                 String resultStatus, String responseJson) {
        jdbcTemplate.update("UPDATE his_inbound_event SET application_id = ?, event_type = ?, result_status = ?, " +
                        "response_json = ? WHERE event_id = ?",
                applicationId, eventType, resultStatus, responseJson, eventId);
    }

    public boolean reserveInboundEvent(String eventId, String eventType) {
        try {
            return jdbcTemplate.update("INSERT INTO his_inbound_event " +
                            "(event_id, application_id, event_type, result_status, response_json, create_time) " +
                            "VALUES (?, NULL, ?, 'PROCESSING', NULL, NOW())",
                    eventId, eventType) > 0;
        } catch (DuplicateKeyException e) {
            return false;
        }
    }

    public DrugApplication findApplication(String sourceSystem, String applicationNo) {
        List<DrugApplication> rows = jdbcTemplate.query(applicationSelect() +
                        " WHERE a.source_system = ? AND a.his_application_no = ? LIMIT 1",
                new BeanPropertyRowMapper<>(DrugApplication.class), sourceSystem, applicationNo);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public DrugApplication findApplicationById(Long id) {
        List<DrugApplication> rows = jdbcTemplate.query(applicationSelect() + " WHERE a.id = ? LIMIT 1",
                new BeanPropertyRowMapper<>(DrugApplication.class), id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public Long createApplication(HisDtos.ApplicationRequest request, String status) {
        String sql = "INSERT INTO drug_application (source_system, his_application_no, revision_no, patient_id, " +
                "patient_name, patient_gender, patient_age, encounter_no, department_code, department_name, priority, status, prescribed_at, " +
                "prescriber_id, prescriber_name, diagnosis, allergy_info, review_status, received_at, update_time) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'PENDING', NOW(), NOW())";
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, request.sourceSystem());
            statement.setString(2, request.applicationNo());
            statement.setInt(3, request.revision());
            statement.setString(4, request.patientId());
            statement.setString(5, request.patientName());
            statement.setString(6, request.patientGender());
            if (request.patientAge() == null) statement.setNull(7, java.sql.Types.INTEGER); else statement.setInt(7, request.patientAge());
            statement.setString(8, request.encounterNo());
            statement.setString(9, request.departmentCode());
            statement.setString(10, request.departmentName());
            statement.setString(11, request.priority());
            statement.setString(12, status);
            if (request.prescribedAt() == null) {
                statement.setTimestamp(13, null);
            } else {
                statement.setTimestamp(13, Timestamp.valueOf(request.prescribedAt()));
            }
            statement.setString(14, request.prescriberId());
            statement.setString(15, request.prescriberName());
            statement.setString(16, request.diagnosis());
            statement.setString(17, request.allergyInfo());
            return statement;
        }, keyHolder);
        if (keyHolder.getKey() == null) {
            throw new IllegalStateException("HIS申请单主键生成失败");
        }
        return keyHolder.getKey().longValue();
    }

    public void updateApplication(Long id, HisDtos.ApplicationRequest request) {
        jdbcTemplate.update("UPDATE drug_application SET revision_no = ?, patient_id = ?, patient_name = ?, " +
                        "patient_gender = ?, patient_age = ?, encounter_no = ?, department_code = ?, department_name = ?, priority = ?, prescribed_at = ?, " +
                        "prescriber_id = ?, prescriber_name = ?, diagnosis = ?, allergy_info = ?, status = ?, " +
                        "review_status = 'PENDING', review_comment = NULL, reviewed_by = NULL, reviewed_at = NULL, " +
                        "cancel_reason = NULL, update_time = NOW() WHERE id = ?",
                request.revision(), request.patientId(), request.patientName(), request.patientGender(), request.patientAge(), request.encounterNo(),
                request.departmentCode(), request.departmentName(), request.priority(), request.prescribedAt(),
                request.prescriberId(), request.prescriberName(), request.diagnosis(), request.allergyInfo(),
                "RECEIVED", id);
    }

    public void deleteApplicationItems(Long applicationId) {
        jdbcTemplate.update("DELETE FROM drug_application_item WHERE application_id = ?", applicationId);
    }

    public Long findMappedCatalogId(String sourceSystem, String hisDrugCode) {
        List<Long> rows = jdbcTemplate.query(
                "SELECT local_catalog_id FROM his_drug_mapping WHERE source_system = ? AND his_drug_code = ? LIMIT 1",
                (rs, rowNum) -> rs.getLong(1), sourceSystem, hisDrugCode);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public void createApplicationItem(Long applicationId, HisDtos.ApplicationItemRequest item,
                                      Long catalogId, String status) {
        jdbcTemplate.update("INSERT INTO drug_application_item (application_id, his_item_no, his_drug_code, " +
                        "local_catalog_id, drug_name, specification, requested_quantity, dispensed_quantity, " +
                        "returned_quantity, unit, dosage, frequency, administration_route, usage_instruction, " +
                        "status, create_time, update_time) VALUES (?, ?, ?, ?, ?, ?, ?, 0, 0, ?, ?, ?, ?, ?, ?, NOW(), NOW())",
                applicationId, item.itemNo(), item.hisDrugCode(), catalogId, item.drugName(),
                item.specification(), item.quantity(), item.unit(), item.dosage(), item.frequency(),
                item.administrationRoute(), item.usageInstruction(), status);
    }

    public List<DrugApplication> listApplications(String status, String keyword, String priority) {
        StringBuilder sql = new StringBuilder(applicationSelect()).append(" WHERE 1 = 1");
        List<Object> args = new ArrayList<>();
        if (hasText(status)) {
            sql.append(" AND a.status = ?");
            args.add(status.trim());
        }
        if (hasText(priority)) {
            sql.append(" AND a.priority = ?");
            args.add(priority.trim());
        }
        if (hasText(keyword)) {
            sql.append(" AND (a.his_application_no LIKE ? OR a.patient_id LIKE ? OR a.patient_name LIKE ?)");
            String like = "%" + keyword.trim() + "%";
            args.add(like);
            args.add(like);
            args.add(like);
        }
        sql.append(" ORDER BY CASE WHEN a.priority = 'URGENT' THEN 0 ELSE 1 END, a.received_at DESC LIMIT 300");
        return jdbcTemplate.query(sql.toString(), new BeanPropertyRowMapper<>(DrugApplication.class), args.toArray());
    }

    public List<DrugApplicationItem> findItems(Long applicationId) {
        String sql = "SELECT i.*, c.drug_name AS local_drug_name, " +
                "COALESCE(c.control_category, 'GENERAL') AS control_category FROM drug_application_item i " +
                "LEFT JOIN drug_catalog c ON c.id = i.local_catalog_id " +
                "WHERE i.application_id = ? ORDER BY i.id";
        return jdbcTemplate.query(sql, new BeanPropertyRowMapper<>(DrugApplicationItem.class), applicationId);
    }

    public DrugApplicationItem findItem(Long itemId) {
        String sql = "SELECT i.*, c.drug_name AS local_drug_name, " +
                "COALESCE(c.control_category, 'GENERAL') AS control_category FROM drug_application_item i " +
                "LEFT JOIN drug_catalog c ON c.id = i.local_catalog_id WHERE i.id = ? LIMIT 1";
        List<DrugApplicationItem> rows = jdbcTemplate.query(sql,
                new BeanPropertyRowMapper<>(DrugApplicationItem.class), itemId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public List<HisDrugMapping> listMappings() {
        String sql = "SELECT m.*, c.drug_name AS local_drug_name, c.specification " +
                "FROM his_drug_mapping m JOIN drug_catalog c ON c.id = m.local_catalog_id " +
                "ORDER BY m.source_system, m.his_drug_code";
        return jdbcTemplate.query(sql, new BeanPropertyRowMapper<>(HisDrugMapping.class));
    }

    public List<Long> findUnmappedApplicationIds(String sourceSystem, String hisDrugCode) {
        String sql = "SELECT DISTINCT i.application_id FROM drug_application_item i " +
                "JOIN drug_application a ON a.id = i.application_id " +
                "WHERE a.source_system = ? AND i.his_drug_code = ? AND i.local_catalog_id IS NULL";
        return jdbcTemplate.query(sql, (rs, rowNum) -> rs.getLong(1), sourceSystem, hisDrugCode);
    }

    public void saveMapping(String sourceSystem, String hisDrugCode, Long catalogId, String operator) {
        jdbcTemplate.update("INSERT INTO his_drug_mapping (source_system, his_drug_code, local_catalog_id, created_by, " +
                        "create_time, update_time) VALUES (?, ?, ?, ?, NOW(), NOW()) " +
                        "ON DUPLICATE KEY UPDATE local_catalog_id = VALUES(local_catalog_id), created_by = VALUES(created_by), " +
                        "update_time = NOW()",
                sourceSystem, hisDrugCode, catalogId, operator);
        jdbcTemplate.update("UPDATE drug_application_item i JOIN drug_application a ON a.id = i.application_id " +
                        "SET i.local_catalog_id = ?, i.status = CASE WHEN i.dispensed_quantity > 0 THEN i.status ELSE 'PENDING' END, " +
                        "i.update_time = NOW() WHERE a.source_system = ? AND i.his_drug_code = ? " +
                        "AND (i.local_catalog_id IS NULL OR i.status = 'UNMAPPED')",
                catalogId, sourceSystem, hisDrugCode);
    }

    public boolean hasDispensedQuantity(Long applicationId) {
        Integer value = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(dispensed_quantity + returned_quantity), 0) FROM drug_application_item WHERE application_id = ?",
                Integer.class, applicationId);
        return value != null && value > 0;
    }

    public boolean hasCurrentDispensedQuantity(Long applicationId) {
        Integer value = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(dispensed_quantity), 0) FROM drug_application_item WHERE application_id = ?",
                Integer.class, applicationId);
        return value != null && value > 0;
    }

    public void markReturnRequired(Long applicationId, String reason) {
        jdbcTemplate.update("UPDATE drug_application SET status = 'RETURN_REQUIRED', cancel_reason = ?, " +
                "update_time = NOW() WHERE id = ?", reason, applicationId);
    }

    public int reviewApplication(Long applicationId, String reviewStatus, String comment, String reviewer) {
        return jdbcTemplate.update("UPDATE drug_application SET review_status = ?, review_comment = ?, reviewed_by = ?, " +
                        "reviewed_at = NOW(), status = ?, update_time = NOW() WHERE id = ? " +
                        "AND status NOT IN ('PARTIALLY_DISPENSED', 'DISPENSED', 'RETURN_REQUIRED', 'RETURNED', 'CANCELLED')",
                reviewStatus, comment, reviewer,
                "APPROVED".equals(reviewStatus) ? "READY" : "REVIEW_REJECTED", applicationId);
    }

    public int autoApproveGeneralReview(Long applicationId, String comment, String reviewer) {
        return jdbcTemplate.update("UPDATE drug_application SET review_status = 'APPROVED', review_comment = ?, " +
                        "reviewed_by = ?, reviewed_at = NOW(), update_time = NOW() WHERE id = ? " +
                        "AND review_status = 'PENDING' " +
                        "AND status NOT IN ('PARTIALLY_DISPENSED', 'DISPENSED', 'RETURN_REQUIRED', 'RETURNED', 'CANCELLED')",
                comment, reviewer, applicationId);
    }

    public int resetSystemReviewForSpecialDrug(Long applicationId, String comment, String reviewer) {
        return jdbcTemplate.update("UPDATE drug_application SET review_status = 'PENDING', review_comment = ?, " +
                        "reviewed_by = NULL, reviewed_at = NULL, update_time = NOW() WHERE id = ? " +
                        "AND review_status = 'APPROVED' AND reviewed_by = ? " +
                        "AND status NOT IN ('PARTIALLY_DISPENSED', 'DISPENSED', 'RETURN_REQUIRED', 'RETURNED', 'CANCELLED')",
                comment, applicationId, reviewer);
    }

    public List<Long> findUnstartedApplicationIdsByCatalog(Long catalogId) {
        String sql = "SELECT DISTINCT i.application_id FROM drug_application_item i " +
                "JOIN drug_application a ON a.id = i.application_id " +
                "WHERE i.local_catalog_id = ? " +
                "AND a.status IN ('MAPPING_REQUIRED', 'REVIEW_PENDING', 'READY') " +
                "AND NOT EXISTS (SELECT 1 FROM drug_application_item issued " +
                "WHERE issued.application_id = a.id AND (issued.dispensed_quantity > 0 OR issued.returned_quantity > 0))";
        return jdbcTemplate.query(sql, (rs, rowNum) -> rs.getLong(1), catalogId);
    }

    public int cancelApplication(Long applicationId, String reason) {
        jdbcTemplate.update("UPDATE drug_application_item SET status = 'CANCELLED', update_time = NOW() " +
                "WHERE application_id = ? AND dispensed_quantity = 0", applicationId);
        return jdbcTemplate.update("UPDATE drug_application SET status = 'CANCELLED', cancel_reason = ?, update_time = NOW() " +
                "WHERE id = ?", reason, applicationId);
    }

    public int addDispensedQuantity(Long itemId, int quantity) {
        String sql = "UPDATE drug_application_item SET dispensed_quantity = dispensed_quantity + ?, " +
                "status = CASE WHEN dispensed_quantity + ? >= requested_quantity THEN 'DISPENSED' ELSE 'PARTIAL' END, " +
                "update_time = NOW() WHERE id = ? AND local_catalog_id IS NOT NULL " +
                "AND status IN ('PENDING', 'PARTIAL') AND requested_quantity - dispensed_quantity >= ?";
        return jdbcTemplate.update(sql, quantity, quantity, itemId, quantity);
    }

    public int addReturnedQuantity(Long itemId, int quantity) {
        String sql = "UPDATE drug_application_item SET dispensed_quantity = dispensed_quantity - ?, " +
                "returned_quantity = returned_quantity + ?, " +
                "status = CASE " +
                "WHEN dispensed_quantity - ? = 0 AND returned_quantity + ? >= requested_quantity THEN 'RETURNED' " +
                "WHEN dispensed_quantity - ? = 0 THEN 'PENDING' ELSE 'PARTIAL' END, update_time = NOW() " +
                "WHERE id = ? AND status IN ('PARTIAL', 'DISPENSED') AND dispensed_quantity >= ?";
        return jdbcTemplate.update(sql, quantity, quantity, quantity, quantity, quantity, itemId, quantity);
    }

    public Map<String, Object> applicationTotals(Long applicationId) {
        return jdbcTemplate.queryForMap("SELECT COUNT(*) AS item_count, " +
                        "COALESCE(SUM(i.requested_quantity), 0) AS requested_quantity, " +
                        "COALESCE(SUM(i.dispensed_quantity), 0) AS dispensed_quantity, " +
                        "COALESCE(SUM(i.returned_quantity), 0) AS returned_quantity, " +
                        "COALESCE(SUM(CASE WHEN i.local_catalog_id IS NULL THEN 1 ELSE 0 END), 0) AS unmapped_count, " +
                        "COALESCE(SUM(CASE WHEN i.local_catalog_id IS NOT NULL " +
                        "AND COALESCE(c.control_category, 'GENERAL') <> 'GENERAL' THEN 1 ELSE 0 END), 0) AS controlled_count " +
                        "FROM drug_application_item i LEFT JOIN drug_catalog c ON c.id = i.local_catalog_id " +
                        "WHERE i.application_id = ?",
                applicationId);
    }

    public void updateApplicationStatus(Long applicationId, String status) {
        jdbcTemplate.update("UPDATE drug_application SET status = ?, update_time = NOW() WHERE id = ?", status, applicationId);
    }

    public void createCallbackEvent(String eventId, Long applicationId, String eventType,
                                    String applicationStatus, String payloadJson, String operator) {
        jdbcTemplate.update("INSERT INTO his_callback_event (event_id, application_id, event_type, application_status, " +
                        "payload_json, status, attempt_count, next_retry_time, operator_name, create_time, update_time) " +
                        "VALUES (?, ?, ?, ?, ?, 'PENDING', 0, NOW(), ?, NOW(), NOW())",
                eventId, applicationId, eventType, applicationStatus, payloadJson, operator);
    }

    public List<HisCallbackEvent> findDueCallbacks(int limit) {
        String sql = callbackSelect() + " WHERE e.status = 'PENDING' AND e.next_retry_time <= NOW() " +
                "ORDER BY e.next_retry_time, e.id LIMIT ?";
        return jdbcTemplate.query(sql, new BeanPropertyRowMapper<>(HisCallbackEvent.class), limit);
    }

    public List<HisCallbackEvent> listCallbacks(int limit) {
        return jdbcTemplate.query(callbackSelect() + " ORDER BY e.id DESC LIMIT ?",
                new BeanPropertyRowMapper<>(HisCallbackEvent.class), limit);
    }

    public int markCallbackProcessing(Long id) {
        return jdbcTemplate.update("UPDATE his_callback_event SET status = 'PROCESSING', update_time = NOW() " +
                "WHERE id = ? AND status = 'PENDING'", id);
    }

    public int recoverStaleCallbacks(LocalDateTime staleBefore) {
        return jdbcTemplate.update("UPDATE his_callback_event SET status = 'PENDING', next_retry_time = NOW(), " +
                        "last_error = '发送进程中断，系统已自动恢复', update_time = NOW() " +
                        "WHERE status = 'PROCESSING' AND update_time < ?",
                Timestamp.valueOf(staleBefore));
    }

    public void markCallbackSent(Long id, String responseBody) {
        jdbcTemplate.update("UPDATE his_callback_event SET status = 'SENT', attempt_count = attempt_count + 1, " +
                "response_body = ?, last_error = NULL, sent_time = NOW(), update_time = NOW() WHERE id = ?",
                responseBody, id);
    }

    public void markCallbackRetry(Long id, int attemptCount, LocalDateTime nextRetry, String error, boolean failed) {
        jdbcTemplate.update("UPDATE his_callback_event SET status = ?, attempt_count = ?, next_retry_time = ?, " +
                        "last_error = ?, update_time = NOW() WHERE id = ?",
                failed ? "FAILED" : "PENDING", attemptCount, nextRetry, truncate(error, 500), id);
    }

    public int retryCallback(String eventId) {
        return jdbcTemplate.update("UPDATE his_callback_event SET status = 'PENDING', attempt_count = 0, " +
                "next_retry_time = NOW(), last_error = NULL, update_time = NOW() " +
                "WHERE event_id = ? AND status = 'FAILED'", eventId);
    }

    private String applicationSelect() {
        return "SELECT a.*, EXISTS (SELECT 1 FROM drug_application_item review_item " +
                "JOIN drug_catalog review_catalog ON review_catalog.id = review_item.local_catalog_id " +
                "WHERE review_item.application_id = a.id " +
                "AND COALESCE(review_catalog.control_category, 'GENERAL') <> 'GENERAL') AS special_review_required, " +
                "(SELECT e.status FROM his_callback_event e WHERE e.application_id = a.id " +
                "ORDER BY e.id DESC LIMIT 1) AS callback_status FROM drug_application a";
    }

    private String callbackSelect() {
        return "SELECT e.*, a.his_application_no, a.patient_name FROM his_callback_event e " +
                "JOIN drug_application a ON a.id = e.application_id";
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String truncate(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max);
    }
}
