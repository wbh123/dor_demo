package com.wust.dormitory.waitlist;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class WaitlistScheduler {
    private final WaitlistService service;

    public WaitlistScheduler(WaitlistService service) {
        this.service = service;
    }

    @Scheduled(fixedDelayString = "${wust.dormitory.waitlist.scan-interval-ms:60000}")
    public void scan() {
        service.expireOffers();
        service.scanAvailableResources(null);
    }
}
