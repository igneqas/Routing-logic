package com.routerbackend.controllers.utils;

import com.routerbackend.dtos.utils.Coordinates;

public class DistanceCalculator {
    private static final int EARTH_RADIUS = 6371; // Radius of the Earth in kilometers

    public static boolean isWithin300Meters(Coordinates coordinates, String[] startNode){
        double lat1Rad = Math.toRadians(coordinates.getLatitude());
        double lon1Rad = Math.toRadians(coordinates.getLongitude());
        double lat2Rad = Math.toRadians(Double.parseDouble(startNode[1]));
        double lon2Rad = Math.toRadians(Double.parseDouble(startNode[0]));
        double dLat = lat2Rad - lat1Rad;
        double dLon = lon2Rad - lon1Rad;
        double a = Math.pow(Math.sin(dLat / 2), 2) + Math.cos(lat1Rad) * Math.cos(lat2Rad) * Math.pow(Math.sin(dLon / 2), 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        double distance = EARTH_RADIUS * c;

        return distance <= 0.5;
    }
}
