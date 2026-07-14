package com.lifeagent;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@MapperScan("com.lifeagent.mapper")
@SpringBootApplication
public class LifeAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(LifeAgentApplication.class, args);
    }
}
