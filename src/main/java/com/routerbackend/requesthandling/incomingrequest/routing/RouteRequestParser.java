package com.routerbackend.requesthandling.incomingrequest.routing;

import com.routerbackend.routinglogic.core.OsmNodeNamed;
import com.routerbackend.routinglogic.core.RoutingContext;
import com.routerbackend.routinglogic.extradata.pollution.PollutionDataHandler;
import com.routerbackend.routinglogic.extradata.traffic.TrafficDataHandler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class RouteRequestParser {

  public RoutingContext readRoutingContext(String profile, String alternativeIdx) {
    RoutingContext routingContext = new RoutingContext();
    routingContext.setProfileName(profile);
    routingContext.setAlternativeIdx(alternativeIdx != null ? Integer.parseInt(alternativeIdx) : 0);

    if(Objects.equals(profile, "pollution-free")) {
      routingContext.setProfileName("safety");
      String rawNoGoData = PollutionDataHandler.getPollutionData();
      rawNoGoData += TrafficDataHandler.getTrafficData();
//      System.out.println(rawNoGoData);
      routingContext.noGoPoints = readNoGoList(rawNoGoData);
    }

    return routingContext;
  }

  public List<OsmNodeNamed> readWaypointList(String lonLats) {
    String[] coordinates = lonLats.split("\\;");
    if (coordinates.length < 2)
      throw new IllegalArgumentException("At least two lat/lon points are required.");

    List<OsmNodeNamed> waypointList = new ArrayList<>();
    for (int i = 0; i < coordinates.length; i++) {
      String[] lonLat = coordinates[i].split(",");
      if (lonLat.length < 2)
        throw new IllegalArgumentException("Longitude and latitude are required.");
      waypointList.add(readPosition(lonLat[0], lonLat[1], "via" + i));
    }

    waypointList.get(0).name = "from";
    waypointList.get(waypointList.size() - 1).name = "to";

    return waypointList;
  }

  private static OsmNodeNamed readPosition(String longitude, String latitude, String name) {
    if (longitude == null) throw new IllegalArgumentException("lon " + name + " not found in input");
    if (latitude == null) throw new IllegalArgumentException("lat " + name + " not found in input");

    return readPosition(Double.parseDouble(longitude), Double.parseDouble(latitude), name);
  }

  private static OsmNodeNamed readPosition(double lon, double lat, String name) {
    OsmNodeNamed n = new OsmNodeNamed();
    n.name = name;
    n.longitude = (int) ((lon + 180.) * 1000000. + 0.5);
    n.latitude = (int) ((lat + 90.) * 1000000. + 0.5);
    return n;
  }

  private List<OsmNodeNamed> readNoGoList(String noGos) {
    if(noGos.isEmpty())
      return Collections.emptyList();
    String[] lonLatRadList = noGos.split(";");

    List<OsmNodeNamed> noGoList = new ArrayList<>();
    for (String s : lonLatRadList) {
      String[] lonLatRad = s.split(",");
      String noGoWeight = "NaN";
//      String noGoWeight = "0";
      if (lonLatRad.length > 3) {
        noGoWeight = lonLatRad[3];
      }
      noGoList.add(readNoGo(lonLatRad[0], lonLatRad[1], lonLatRad[2], noGoWeight));
    }

    return noGoList;
  }

  private static OsmNodeNamed readNoGo(String longitude, String latitude, String radius, String noGoWeight) {
    double weight = "undefined".equals(noGoWeight) ? Double.NaN : Double.parseDouble(noGoWeight);
    return readNoGo(Double.parseDouble(longitude), Double.parseDouble(latitude), Integer.parseInt(radius), weight);
//    return readNoGo(Double.parseDouble(lon), Double.parseDouble(lat), Integer.parseInt(radius), Double.parseDouble(noGoWeight));
  }

  private static OsmNodeNamed readNoGo(double longitude, double latitude, int radius, double noGoWeight) {
    OsmNodeNamed node = new OsmNodeNamed();
    node.name = "nogo";
    node.longitude = (int) ((longitude + 180.) * 1000000. + 0.5);
    node.latitude = (int) ((latitude + 90.) * 1000000. + 0.5);
    node.isNoGo = true;
    node.noGoWeight = noGoWeight;
    node.radius = radius;
    return node;
  }
}
