package com.routerbackend.dtos.utils;

public class Coordinates {
    double latitude;
    double longitude;

    public Coordinates(double latitude, double longitude) {
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    @Override
    public String toString() {
        return "{" +
                "\"latitude\"=" + latitude +
                ", \"longitude\"=" + longitude +
                '}';
    }
}
