package com.routerbackend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

@SpringBootApplication
@EnableMongoRepositories
public class RouterBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(RouterBackendApplication.class, args);
    }

}
