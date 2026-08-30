package com.hospital.pharmacy;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class HospitalDrugApplication {

    public static void main(String[] args) {
        SpringApplication.run(HospitalDrugApplication.class, args);
    }

}
