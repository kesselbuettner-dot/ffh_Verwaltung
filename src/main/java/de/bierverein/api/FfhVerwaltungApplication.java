package de.bierverein.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class FfhVerwaltungApplication {

    public static void main(String[] args) {
        SpringApplication.run(FfhVerwaltungApplication.class, args);
    }
}
