package com.routerbackend.dtos.utils;

import com.routerbackend.dtos.RouteDTO;
import org.json.JSONArray;
import org.json.JSONObject;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class Converter {
    public static List<Coordinates> JsonToCoordinates(JSONArray jsonArray){
        List<Coordinates> coordinates = new ArrayList<>();
        for(int i=0; i<jsonArray.length(); i++){
            JSONArray coordinatesArray = jsonArray.getJSONArray(i);
            BigDecimal bd1 = (BigDecimal) coordinatesArray.get(0);
            BigDecimal bd2 = (BigDecimal) coordinatesArray.get(1);
            Coordinates coordinatesObject = new Coordinates(bd2.doubleValue(), bd1.doubleValue());
            coordinates.add(coordinatesObject);
        }

        return coordinates;
    }

    public static JSONArray RoutesToJson(List<RouteDTO> routes) {
        JSONArray ja = new JSONArray();
        routes.forEach(route -> {
            JSONObject jo = new JSONObject();
            jo.put("name", route.getName());
            jo.put("dateCreated", route.getDateCreated());
            jo.put("distance", route.getLength());
            jo.put("time", route.getDuration());
            jo.put("ascend", route.getAscend());
            jo.put("type", route.getTripType());
            JSONArray coordinates = new JSONArray();
            route.getCoordinates().forEach(coords -> {
                JSONArray coordsObject = new JSONArray();
                coordsObject.put(coords.longitude);
                coordsObject.put(coords.latitude);
                coordinates.put(coordsObject);
            });
            jo.put("coordinates", coordinates);
            ja.put(jo);
        });
        return ja;
    }
}