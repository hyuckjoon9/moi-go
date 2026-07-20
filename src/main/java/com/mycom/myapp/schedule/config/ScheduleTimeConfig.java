package com.mycom.myapp.schedule.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ScheduleTimeConfig {

    @Bean("scheduleClock")
    public Clock scheduleClock() {
        return Clock.systemDefaultZone();
    }
}
