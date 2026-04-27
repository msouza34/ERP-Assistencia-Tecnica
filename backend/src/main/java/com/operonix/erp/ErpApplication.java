package com.operonix.erp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import java.util.TimeZone;

@SpringBootApplication
public class ErpApplication {

    public static void main(String[] args) {
        TimeZone.setDefault(TimeZone.getTimeZone(System.getenv().getOrDefault("APP_TIME_ZONE", "America/Sao_Paulo")));
        SpringApplication.run(ErpApplication.class, args);
    }
}
