package com.m1kasa.m1kasavoucherservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling  //定时任务开启
public class M1kasaVoucherServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(M1kasaVoucherServiceApplication.class, args);
    }

}
