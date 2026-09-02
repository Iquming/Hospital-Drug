package com.hospital.pharmacy.service;

import com.hospital.pharmacy.constant.HisApplicationStatus;
import com.hospital.pharmacy.dao.HisIntegrationDao;
import com.hospital.pharmacy.dto.HisDtos;
import com.hospital.pharmacy.entity.DrugApplication;
import com.hospital.pharmacy.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HisApplicationServiceTest {

    @Mock
    private HisIntegrationDao hisIntegrationDao;

    @InjectMocks
    private HisApplicationService service;

    @Test
    void marksApplicationAsMappingRequired() {
        stubApplication(HisApplicationStatus.RECEIVED);
        when(hisIntegrationDao.applicationTotals(1L)).thenReturn(totals(3, 0, 0, 1, 0));

        assertEquals(HisApplicationStatus.MAPPING_REQUIRED, service.refreshStatus(1L));
        verify(hisIntegrationDao).updateApplicationStatus(1L, HisApplicationStatus.MAPPING_REQUIRED);
    }

    @Test
    void marksApplicationAsPartiallyDispensed() {
        stubApplication(HisApplicationStatus.READY);
        when(hisIntegrationDao.applicationTotals(1L)).thenReturn(totals(3, 1, 0, 0, 0));

        assertEquals(HisApplicationStatus.PARTIALLY_DISPENSED, service.refreshStatus(1L));
    }

    @Test
    void marksApplicationAsDispensed() {
        stubApplication(HisApplicationStatus.PARTIALLY_DISPENSED);
        when(hisIntegrationDao.applicationTotals(1L)).thenReturn(totals(3, 3, 0, 0, 0));

        assertEquals(HisApplicationStatus.DISPENSED, service.refreshStatus(1L));
    }

    @Test
    void marksFullyReturnedApplicationAsReturned() {
        stubApplication(HisApplicationStatus.DISPENSED);
        when(hisIntegrationDao.applicationTotals(1L)).thenReturn(totals(3, 0, 3, 0, 0));

        assertEquals(HisApplicationStatus.RETURNED, service.refreshStatus(1L));
    }

    @Test
    void requiresSpecialReviewBeforeControlledApplicationBecomesReady() {
        DrugApplication application = new DrugApplication();
        application.setId(1L);
        application.setStatus(HisApplicationStatus.RECEIVED);
        application.setReviewStatus("PENDING");
        when(hisIntegrationDao.findApplicationById(1L)).thenReturn(application);
        when(hisIntegrationDao.applicationTotals(1L)).thenReturn(totals(3, 0, 0, 0, 1));

        assertEquals(HisApplicationStatus.REVIEW_PENDING, service.refreshStatus(1L));
    }

    @Test
    void generalApplicationCompletesGeneralReviewWithoutSpecialManualGate() {
        DrugApplication application = new DrugApplication();
        application.setId(1L);
        application.setStatus(HisApplicationStatus.RECEIVED);
        application.setReviewStatus("PENDING");
        when(hisIntegrationDao.findApplicationById(1L)).thenReturn(application);
        when(hisIntegrationDao.applicationTotals(1L)).thenReturn(totals(3, 0, 0, 0, 0));
        when(hisIntegrationDao.autoApproveGeneralReview(eq(1L), anyString(), eq("系统规则"))).thenReturn(1);

        assertEquals(HisApplicationStatus.READY, service.refreshStatus(1L));
        verify(hisIntegrationDao).autoApproveGeneralReview(eq(1L), anyString(), eq("系统规则"));
    }

    @Test
    void catalogChangedToControlledResetsOnlySystemCompletedReview() {
        DrugApplication application = new DrugApplication();
        application.setId(1L);
        application.setStatus(HisApplicationStatus.READY);
        application.setReviewStatus("APPROVED");
        application.setReviewedBy("系统规则");
        when(hisIntegrationDao.findApplicationById(1L)).thenReturn(application);
        when(hisIntegrationDao.applicationTotals(1L)).thenReturn(totals(3, 0, 0, 0, 1));
        when(hisIntegrationDao.resetSystemReviewForSpecialDrug(eq(1L), anyString(), eq("系统规则"))).thenReturn(1);

        assertEquals(HisApplicationStatus.REVIEW_PENDING, service.refreshStatus(1L));
        verify(hisIntegrationDao).resetSystemReviewForSpecialDrug(eq(1L), anyString(), eq("系统规则"));
    }

    @Test
    void rejectsSpecialReviewActionForGeneralApplication() {
        DrugApplication application = new DrugApplication();
        application.setId(1L);
        application.setStatus(HisApplicationStatus.READY);
        application.setReviewStatus("APPROVED");
        application.setSpecialReviewRequired(false);
        when(hisIntegrationDao.findApplicationById(1L)).thenReturn(application);
        when(hisIntegrationDao.findItems(1L)).thenReturn(List.of());

        assertThrows(BusinessException.class,
                () -> service.review(1L, new HisDtos.ReviewRequest("APPROVED", ""), "测试药师"));
        verify(hisIntegrationDao, never()).reviewApplication(eq(1L), anyString(), anyString(), anyString());
    }

    @Test
    void marksPartiallyIssuedThenFullyReturnedAsReturned() {
        stubApplication(HisApplicationStatus.PARTIALLY_DISPENSED);
        when(hisIntegrationDao.applicationTotals(1L)).thenReturn(totals(10, 0, 3, 0, 0));

        assertEquals(HisApplicationStatus.RETURNED, service.refreshStatus(1L));
    }

    @Test
    void keepsReturnRequiredWhileDispensedQuantityRemains() {
        stubApplication(HisApplicationStatus.RETURN_REQUIRED);
        when(hisIntegrationDao.applicationTotals(1L)).thenReturn(totals(10, 2, 1, 0, 0));

        assertEquals(HisApplicationStatus.RETURN_REQUIRED, service.refreshStatus(1L));
    }

    private void stubApplication(String status) {
        DrugApplication application = new DrugApplication();
        application.setId(1L);
        application.setStatus(status);
        application.setReviewStatus("APPROVED");
        when(hisIntegrationDao.findApplicationById(1L)).thenReturn(application);
    }

    private Map<String, Object> totals(int requested, int dispensed, int returned, int unmapped, int controlled) {
        return Map.of(
                "requested_quantity", requested,
                "dispensed_quantity", dispensed,
                "returned_quantity", returned,
                "unmapped_count", unmapped,
                "controlled_count", controlled
        );
    }
}
