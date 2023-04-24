package com.routerbackend.dtos;

import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "users")
public class UserDTO {
    String username;
    String email;

    @Override
    public String toString() {
        return "UserDTO{" +
                "username='" + username + '\'' +
                ", email='" + email + '\'' +
                '}';
    }
}
