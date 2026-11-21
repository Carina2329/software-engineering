package com.campus.contest.module.dashboard.config;

import com.campus.contest.module.dashboard.service.DashboardOverviewService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
public class DashboardTimeConfig {

    @Bean
    public Clock dashboardClock() {
        return Clock.system(DashboardOverviewService.BUSINESS_ZONE);
    }
}
