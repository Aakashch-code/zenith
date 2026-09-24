package com.example.zenith;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ZenithApplication {

    public static void main(String[] args) {
        SpringApplication.run(ZenithApplication.class, args);
    }

}
