package com.routerbackend.dtos;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("tokens")
public class TokenDTO {

    @Id
    public String _id;

    public String token;

    public boolean revoked;

    public boolean expired;

    public String userId;

    public boolean isRevoked() {
        return revoked;
    }

    public void setRevoked(boolean revoked) {
        this.revoked = revoked;
    }

    public boolean isExpired() {
        return expired;
    }

    public void setExpired(boolean expired) {
        this.expired = expired;
    }

    public TokenDTO(String token, boolean revoked, boolean expired, String userId) {
        this.token = token;
        this.revoked = revoked;
        this.expired = expired;
        this.userId = userId;
    }
}
