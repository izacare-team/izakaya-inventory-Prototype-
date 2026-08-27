package com.izacare;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class IzaCareApplication {

    public static void main(String[] args) {
        SpringApplication.run(IzaCareApplication.class, args);
    }
}
