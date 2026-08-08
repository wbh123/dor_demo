package com.wust.dormitory.admin;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class RoomLayoutConfiguration {
    @Bean
    RoomLayoutPlanner roomLayoutPlanner() {
        return new RoomLayoutPlanner();
    }
}
