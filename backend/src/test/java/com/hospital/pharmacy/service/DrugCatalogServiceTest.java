package com.hospital.pharmacy.service;

import com.hospital.pharmacy.dao.DrugCatalogDao;
import com.hospital.pharmacy.entity.DrugCatalog;
import com.hospital.pharmacy.entity.DrugStock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DrugCatalogServiceTest {

    @Mock
    private DrugCatalogDao drugCatalogDao;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private HisApplicationService hisApplicationService;

    @InjectMocks
    private DrugCatalogService service;

    @Test
    void stockInboundInheritsSelectedCatalogRules() {
        DrugCatalog catalog = new DrugCatalog();
        catalog.setId(7L);
        catalog.setDrugName("测试特殊药品");
        catalog.setIsSplitAllowed(true);
        catalog.setPackageUnit("盒");
        catalog.setMinUnit("片");
        catalog.setMinUnitsPerPackage(20);
        when(drugCatalogDao.findById(7L)).thenReturn(catalog);

        DrugStock stock = new DrugStock();
        stock.setCatalogId(7L);
        stock.setDrugName("外部传入名称");
        service.applyCatalogDefaults(stock);

        assertEquals("测试特殊药品", stock.getDrugName());
        assertEquals(true, stock.getIsSplitAllowed());
        assertEquals("盒", stock.getPackageUnit());
        assertEquals("片", stock.getMinUnit());
        assertEquals(20, stock.getMinUnitsPerPackage());
    }

    @Test
    void stockInboundRejectsMissingOrDisabledCatalog() {
        DrugStock stock = new DrugStock();
        stock.setCatalogId(99L);
        when(drugCatalogDao.findById(99L)).thenReturn(null);

        assertThrows(IllegalArgumentException.class, () -> service.applyCatalogDefaults(stock));
    }
}
