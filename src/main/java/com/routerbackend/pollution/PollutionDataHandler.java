package com.routerbackend.pollution;

import org.json.JSONArray;
import org.json.JSONObject;

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
        JSONObject pollutionData = PollutionRequestHandler.getPollutionRequestResponse();
        JSONArray sensorData = pollutionData.getJSONArray("features");
        return PollutionDataResolver.resolveVilniusPollution(sensorData);
    }
}
