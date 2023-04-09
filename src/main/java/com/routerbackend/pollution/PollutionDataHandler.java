package com.routerbackend.pollution;

import org.json.JSONArray;
import org.json.JSONObject;

import static com.routerbackend.pollution.PollutionDataResolver.resolveVilniusPollution;
import static com.routerbackend.pollution.PollutionRequestHandler.getPollutionRequestResponse;

public class PollutionDataHandler {
    public static String getPollutionData()
    {
        //TODO: Implement getting different data by city
        if(true)
            return getVilniusPollutionData();
        return "";
    }

    private static String getVilniusPollutionData()
    {
        JSONObject pollutionData = getPollutionRequestResponse();
        JSONArray sensorData = pollutionData.getJSONArray("features");
        return resolveVilniusPollution(sensorData);
    }
}
