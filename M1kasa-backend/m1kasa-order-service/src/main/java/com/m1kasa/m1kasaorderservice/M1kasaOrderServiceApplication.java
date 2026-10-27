package com.m1kasa.m1kasaorderservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class M1kasaOrderServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(M1kasaOrderServiceApplication.class, args);
    }

}
