package com.example.monivobe;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@EnableAsync
@SpringBootApplication
public class MonivoBeApplication {

    public static void main(String[] args) {
        SpringApplication.run(MonivoBeApplication.class, args);
    }

}
