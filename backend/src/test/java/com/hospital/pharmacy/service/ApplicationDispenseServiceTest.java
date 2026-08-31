package com.hospital.pharmacy.service;

import com.hospital.pharmacy.constant.HisApplicationItemStatus;
import com.hospital.pharmacy.constant.HisApplicationStatus;
import com.hospital.pharmacy.dao.DrugDao;
import com.hospital.pharmacy.dao.HisIntegrationDao;
import com.hospital.pharmacy.entity.DrugApplication;
import com.hospital.pharmacy.entity.DrugApplicationItem;
import com.hospital.pharmacy.entity.DrugStock;
import com.hospital.pharmacy.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApplicationDispenseServiceTest {

    @Mock private HisIntegrationDao hisIntegrationDao;
    @Mock private DrugDao drugDao;
    @Mock private HisApplicationService hisApplicationService;
    @Mock private HisCallbackService hisCallbackService;
    @Mock private AuditLogService auditLogService;

    @InjectMocks private ApplicationDispenseService service;

    @Test
    void rejectsDispenseBeforePrescriptionReview() {
        DrugApplicationItem item = item(HisApplicationItemStatus.PENDING);
        DrugApplication application = application("PENDING", HisApplicationStatus.REVIEW_PENDING);
        when(hisIntegrationDao.findItem(10L)).thenReturn(item);
        when(hisIntegrationDao.findApplicationById(1L)).thenReturn(application);

        assertThrows(BusinessException.class, () -> service.dispense(10L, "TRACE-1", "药师"));
        verifyNoInteractions(drugDao);
    }

    @Test
    void rejectsReturnWhenTraceDoesNotBelongToApplicationItemAndPatient() {
        DrugApplicationItem item = item(HisApplicationItemStatus.DISPENSED);
        DrugApplication application = application("APPROVED", HisApplicationStatus.DISPENSED);
        DrugStock stock = new DrugStock();
        stock.setCatalogId(2L);
        stock.setTraceCode("TRACE-OTHER");
        stock.setDrugName("测试药品");
        stock.setPackageUnit("盒");
        when(hisIntegrationDao.findItem(10L)).thenReturn(item);
        when(hisIntegrationDao.findApplicationById(1L)).thenReturn(application);
        when(drugDao.getDrugByTraceCode("TRACE-OTHER")).thenReturn(stock);
        when(drugDao.findReturnableDispenseRecord("TRACE-OTHER", 1L, 10L, "P001")).thenReturn(null);

        assertThrows(BusinessException.class, () -> service.returnDrug(10L, "TRACE-OTHER", "药师"));
    }

    private DrugApplicationItem item(String status) {
        DrugApplicationItem item = new DrugApplicationItem();
        item.setId(10L);
        item.setApplicationId(1L);
        item.setLocalCatalogId(2L);
        item.setUnit("盒");
        item.setStatus(status);
        item.setDispensedQuantity(HisApplicationItemStatus.DISPENSED.equals(status) ? 1 : 0);
        return item;
    }

    private DrugApplication application(String reviewStatus, String status) {
        DrugApplication application = new DrugApplication();
        application.setId(1L);
        application.setPatientId("P001");
        application.setReviewStatus(reviewStatus);
        application.setStatus(status);
        return application;
    }
}

