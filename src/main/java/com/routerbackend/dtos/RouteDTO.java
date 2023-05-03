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
    int ascend;

    public RouteDTO(String name, String userId, Date dateCreated, double length, int duration, String tripType, List<Coordinates> coordinates, int ascend) {
        this.name = name;
        this.userId = userId;
        this.dateCreated = dateCreated;
        this.length = length;
        this.duration = duration;
        this.tripType = tripType;
        this.coordinates = coordinates;
        this.ascend = ascend;
    }

    @Override
    public String toString() {
        return "RouteDTO{" +
                "_id='" + _id + '\'' +
                ", name='" + name + '\'' +
                ", userId='" + userId + '\'' +
                ", dateCreated=" + dateCreated +
                ", length=" + length +
                ", duration=" + duration +
                ", tripType='" + tripType + '\'' +
                ", coordinates=" + coordinates +
                ", ascend=" + ascend +
                '}';
    }

    public String getName() {
        return name;
    }

    public Date getDateCreated() {
        return dateCreated;
    }

    public double getLength() {
        return length;
    }

    public int getDuration() {
        return duration;
    }

    public String getTripType() {
        return tripType;
    }

    public List<Coordinates> getCoordinates() {
        return coordinates;
    }

    public int getAscend() {
        return ascend;
    }
}
