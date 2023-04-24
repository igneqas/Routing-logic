package com.routerbackend.dtos.utils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class Converter {
    public static List<Coordinates> JsonToCoordinates(JSONArray jsonArray){
        List<Coordinates> coordinates = new ArrayList<>();
        for(int i=0; i<jsonArray.length(); i++){
            JSONObject jsonObject = jsonArray.getJSONObject(i);
            Coordinates coordinatesObject = new Coordinates(jsonObject.getDouble("latitude"), jsonObject.getDouble("longitude"));
            coordinates.add(coordinatesObject);
        }

        return coordinates;
    }
}
