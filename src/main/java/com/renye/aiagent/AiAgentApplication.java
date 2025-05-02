package com.renye.aiagent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class AiAgentApplication {

    public static void main(String[] args) {
        System.out.println("http://localhost:8123/api/doc.html");
        SpringApplication.run(AiAgentApplication.class, args);
    }

}
