package com.hospital.pharmacy;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "app.his.callback-enabled=false")
class HospitalDrugApplicationTests {

    @Test
    void contextLoads() {
    }

}
