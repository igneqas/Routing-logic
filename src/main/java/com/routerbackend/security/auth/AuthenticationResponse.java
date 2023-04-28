package com.routerbackend.security.auth;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.json.JSONObject;

public class AuthenticationResponse {

    private String accessToken;
    private String refreshToken;

    public AuthenticationResponse(String accessToken, String refreshToken) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
    }

    @Override
    public String toString() {
        return new JSONObject()
                .put("accessToken", accessToken)
                .put("refreshToken", refreshToken)
                .toString();
    }
}
