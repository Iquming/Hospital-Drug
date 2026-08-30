package com.example.demodrug;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class DemoDrugApplication {

    public static void main(String[] args) {
        SpringApplication.run(DemoDrugApplication.class, args);
    }

}
