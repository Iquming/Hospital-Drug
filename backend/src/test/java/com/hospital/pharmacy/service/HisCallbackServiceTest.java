package com.hospital.pharmacy.service;

import com.hospital.pharmacy.dao.HisIntegrationDao;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HisCallbackServiceTest {

    @Mock private HisIntegrationDao hisIntegrationDao;
    @InjectMocks private HisCallbackService service;

    @Test
    void recoversInterruptedCallbacksBeforePolling() {
        ReflectionTestUtils.setField(service, "callbackEnabled", true);
        ReflectionTestUtils.setField(service, "processingTimeoutSeconds", 120);
        when(hisIntegrationDao.findDueCallbacks(20)).thenReturn(List.of());

        service.deliverDueCallbacks();

        verify(hisIntegrationDao).recoverStaleCallbacks(any());
        verify(hisIntegrationDao).findDueCallbacks(20);
    }
}
