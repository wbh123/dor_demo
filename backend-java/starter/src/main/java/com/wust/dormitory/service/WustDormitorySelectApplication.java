package com.wust.dormitory;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
//import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication
@MapperScan("com.wust.dormitory.mapper")
//@EnableDiscoveryClient
public class WustDormitorySelectApplication {
    public static void main(String[] args) {
        SpringApplication.run(WustDormitorySelectApplication.class, args);
    }
}
