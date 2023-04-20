package com.routerbackend.routinglogic.extradata.pollution;

import com.routerbackend.requesthandling.outgoingrequest.HttpRequestHandler;
import org.json.JSONArray;
import org.json.JSONObject;

import java.time.Instant;

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
        long unixTime = Instant.now().getEpochSecond();
        String url = "https://atviras.vplanas.lt/arcgis/rest/services/Aplinkosauga/Oro_tarsa/MapServer/0/query?3D1&outFields=*&returnGeometry=false&outSR=4326&f=json&where=(1%20%3D%201)%20AND%20(" + unixTime + "%3D" + unixTime + ")";
        JSONObject pollutionData = HttpRequestHandler.getHttpRequestResponse(url);
        JSONArray sensorEntries = pollutionData.getJSONArray("features");
        return PollutionDataResolver.resolveVilniusPollution(sensorEntries);
    }
}
