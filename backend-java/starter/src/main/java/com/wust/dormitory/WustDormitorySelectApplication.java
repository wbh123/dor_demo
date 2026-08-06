package com.wust.dormitory;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class WustDormitorySelectApplication {
    public static void main(String[] args) {
        SpringApplication.run(WustDormitorySelectApplication.class, args);
    }
}
