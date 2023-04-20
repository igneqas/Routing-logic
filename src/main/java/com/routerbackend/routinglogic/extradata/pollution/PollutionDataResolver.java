package com.routerbackend.routinglogic.extradata.pollution;

import org.json.JSONArray;
import org.json.JSONObject;

public class PollutionDataResolver {

    public static String resolveVilniusPollution(JSONArray data) {
        StringBuilder combinedNoGoData = new StringBuilder();
        for (int i = 0; i < data.length(); i++) {
            JSONObject sensorEntry = data.getJSONObject(i).getJSONObject("attributes");
            RadiusAndWeight radiusAndWeight = parseVilniusPollution(sensorEntry);
            try {
                String noGoEntry = sensorEntry.getDouble("longitude") + "," + sensorEntry.getDouble("latitude")
                        + "," + radiusAndWeight.radius + "," + radiusAndWeight.weight + ";";
                combinedNoGoData.append(noGoEntry);
            } catch (Exception exception) {
                System.out.println("Found null value.");
            }
        }
        return combinedNoGoData.toString();
    }

    private static RadiusAndWeight parseVilniusPollution(JSONObject sensorEntry)
    {
        double nh3 = sensorEntry.get("nh3_ug_m3").toString() != "null" ? sensorEntry.getDouble("nh3_ug_m3") : 0;
        double so2 = sensorEntry.get("so2_ug_m3").toString() != "null" ? sensorEntry.getDouble("so2_ug_m3") : 0;
        double o3 = sensorEntry.get("o3_ug_m3").toString() != "null" ? sensorEntry.getDouble("o3_ug_m3") : 0;
        double co = sensorEntry.get("co_mg_m3").toString() != "null" ? sensorEntry.getDouble("co_mg_m3") : 0;
        double no2 = sensorEntry.get("no2_ug_m3").toString() != "null" ? sensorEntry.getDouble("no2_ug_m3") : 0;
        double pm2_5 = sensorEntry.get("pm2_5").toString() != "null" ? sensorEntry.getDouble("pm2_5") : 0;
        double pm10 = sensorEntry.get("pm10").toString() != "null" ? sensorEntry.getDouble("pm10") : 0;
        return PollutionToWeightConverter.convertVilniusDataToWeight(nh3, so2, o3, co, no2, pm2_5, pm10);
    }
}
