package com.formulaquery.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/**
 * Main Spring Boot Application class
 * - Entry point for 4mulaQuery API
 * - Initializes EngineService Bean for C++ database engine bridge
 */
@SpringBootApplication
public class ApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiApplication.class, args);

        // Startup log
        System.out.println("==========================================");
        System.out.println("  4mulaQuery API Bridge: ONLINE & READY   ");
        System.out.println("==========================================");
    }

    /**
     * EngineService Bean
     * - Provides C++ engine binary path to the service
     * - In Docker container, binary path: './core/4mulaQuery'
     */
    @Bean
    public EngineService engineService() {
        String binaryPath = System.getProperty("user.home") + "/Desktop/workSpace/project/4mulaQuery/core/4mulaQuery";
        return new EngineService(binaryPath);
    }
}