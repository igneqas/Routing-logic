package com.routerbackend.dtos;

import com.routerbackend.dtos.utils.Coordinates;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;
import java.util.List;

@Document(collection = "routes")
public class RouteDTO {
    @Id
    String _id;
    String name;
    String userId;
    Date dateCreated;
    double length;
    int duration;
    String tripType;
    List<Coordinates> coordinates;

    public RouteDTO(String name, String userId, Date dateCreated, double length, int duration, String tripType, List<Coordinates> coordinates) {
        this.name = name;
        this.userId = userId;
        this.dateCreated = dateCreated;
        this.length = length;
        this.duration = duration;
        this.tripType = tripType;
        this.coordinates = coordinates;
    }

    @Override
    public String toString() {
        return "RouteDTO{" +
                "name='" + name + '\'' +
                ", userId='" + userId + '\'' +
                ", dateCreated=" + dateCreated +
                ", length=" + length +
                ", duration=" + duration +
                ", tripType='" + tripType + '\'' +
                ", coordinates=" + coordinates +
                '}';
    }
}
