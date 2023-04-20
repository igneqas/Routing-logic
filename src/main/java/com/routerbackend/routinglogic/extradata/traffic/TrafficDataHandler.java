package com.routerbackend.routinglogic.extradata.traffic;

import com.routerbackend.requesthandling.outgoingrequest.HttpRequestHandler;
import org.json.JSONArray;
import org.json.JSONObject;

public class TrafficDataHandler {
    public static String getTrafficData()
    {
        //TODO: Implement getting different data by city
        if(true)
            return getVilniusTrafficData();
        return "";
    }

    private static String getVilniusTrafficData()
    {
        JSONObject trafficData = HttpRequestHandler.getHttpRequestResponse("https://arcgis.sviesoforai.lt/arcgis/rest/services/VIS/Vilnius_sde_dynamic/MapServer/12/query?where=1%3D1&outFields=*&outSR=4326&f=json");
        JSONArray trafficEntries = trafficData.getJSONArray("features");
        return TrafficDataResolver.resolveVilniusTraffic(trafficEntries);
    }
}
