package com.pe.assistant;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class PeTeacherAssistantApplication {
    public static void main(String[] args) {
        SpringApplication.run(PeTeacherAssistantApplication.class, args);
    }
}
