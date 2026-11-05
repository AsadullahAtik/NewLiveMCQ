package com.livemcq;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point. @SpringBootApplication = "scan this package and everything below it for Spring
 * components, and turn on auto-configuration" (web server, JPA, Flyway, ...).
 */
@SpringBootApplication
public class LiveMcqApplication {

    public static void main(String[] args) {
        SpringApplication.run(LiveMcqApplication.class, args);
    }
}
