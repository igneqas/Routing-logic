package com.routerbackend.pollution;

import org.json.JSONObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.ExecutionException;

public class PollutionRequestHandler {
    public static JSONObject getPollutionRequestResponse()
    {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://atviras.vplanas.lt/arcgis/rest/services/Aplinkosauga/Oro_tarsa/MapServer/3/query?where=1%3D1&outFields=latitude,longitude,pm1,pm2_5,pm10,co2_ppm,co_mg_m3,nh3_ug_m3,no2_ug_m3,no_ug_m3,o3_ug_m3,so2_ug_m3,name&returnGeometry=false&outSR=4326&f=json"))
                .build();
        var response = client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body);
        JSONObject responseJson = null;
        try {
            responseJson = new JSONObject(response.get());
        } catch (InterruptedException | ExecutionException e) {
            System.out.println("Request to pollution data source failed.");
        }
        return responseJson;
    }
}
