package com.mycom.myapp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class MoiGoApplication {

    public static void main(String[] args) {
        SpringApplication.run(MoiGoApplication.class, args);
    }
}
