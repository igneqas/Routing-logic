package com.routerbackend.requesthandling.outgoingrequest;

import org.json.JSONObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.ExecutionException;

public class HttpRequestHandler {
    public static JSONObject getHttpRequestResponse(String url)
    {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .build();
        var response = client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body);
        JSONObject responseJson = null;
        try {
            responseJson = new JSONObject(response.get());
        } catch (InterruptedException | ExecutionException e) {
            System.out.println("HTTP request failed.");
        }
        return responseJson;
    }
}
