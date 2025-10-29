package com.example.mqlimitdemo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Boot 启动类
 *
 * @author demo
 */
@SpringBootApplication
public class MqLimitDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(MqLimitDemoApplication.class, args);
        System.out.println("========================================");
        System.out.println("   RocketMQ Demo Application Started   ");
        System.out.println("========================================");
    }
}

