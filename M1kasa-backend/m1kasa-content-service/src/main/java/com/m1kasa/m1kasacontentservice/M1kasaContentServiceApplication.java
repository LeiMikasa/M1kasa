package com.m1kasa.m1kasacontentservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class M1kasaContentServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(M1kasaContentServiceApplication.class, args);
    }

}
