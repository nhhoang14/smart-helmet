package com.helmet;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class HelmetApplication {
    public static void main(String[] args) {
        SpringApplication.run(HelmetApplication.class, args);
    }
}
