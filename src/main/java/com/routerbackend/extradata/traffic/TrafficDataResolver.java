package com.routerbackend.extradata.traffic;

import org.json.JSONArray;
import org.json.JSONObject;

public class TrafficDataResolver {

    public static String resolveVilniusTraffic(JSONArray trafficEntries) {
        StringBuilder combinedNoGoData = new StringBuilder();
        for (int i = 0; i < trafficEntries.length(); i++) {
            JSONObject trafficJamCoordinates = trafficEntries.getJSONObject(i).getJSONObject("geometry");
            try {
                String noGoEntry = trafficJamCoordinates.getDouble("x") + "," + trafficJamCoordinates.getDouble("y")
                        + ",100,-0.5;";
                combinedNoGoData.append(noGoEntry);
            } catch (Exception exception) {
                System.out.println("Found null value.");
            }
        }
        return combinedNoGoData.toString();
    }
}
