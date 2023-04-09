package com.routerbackend.pollution;

public class PollutionToWeightConverter {
    public static RadiusAndWeight convertVilniusDataToWeight(double nh3, double so2, double o3, double co, double no2, double pm2_5, double pm10)
    {
        if(nh3 > 15000 || so2 > 500 || o3 > 240 || co > 13 || no2 > 400 || pm2_5 > 100 || pm10 > 100)
            return new RadiusAndWeight(600, -1);
        else if(nh3 > 38 || so2 > 300 || o3 > 180 || co > 10 || no2 > 200 || pm2_5 > 55 || pm10 > 50)
            return new RadiusAndWeight(400, -0.5);
        else if(nh3 > 8 || so2 > 100 || o3 > 120 || co > 7 || no2 > 100 || pm2_5 > 30 || pm10 > 30)
            return new RadiusAndWeight(200, 0);
        return new RadiusAndWeight(0, 1);
    }
}
