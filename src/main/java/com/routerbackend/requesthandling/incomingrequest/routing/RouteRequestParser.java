package com.routerbackend.requesthandling.incomingrequest.routing;

import com.routerbackend.routinglogic.core.OsmNodeNamed;
import com.routerbackend.routinglogic.core.RoutingContext;
import com.routerbackend.routinglogic.extradata.pollution.PollutionDataHandler;
import com.routerbackend.routinglogic.extradata.traffic.TrafficDataHandler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * URL query parameter handler for web and standalone server. Supports all
 * BRouter features without restrictions.
 * <p>
 * Parameters:
 * <p>
 * lonlats = lon,lat|... (unlimited list of lon,lat waypoints separated by |)
 * nogos = lon,lat,radius|... (optional, radius in meters)
 * profile = profile file name without .brf
 * alternativeidx = [0|1|2|3] (optional, default 0)
 * format = [kml|gpx|geojson] (optional, default gpx)
 * trackname = name used for filename and format specific trackname (optional, default brouter)
 * exportWaypoints = 1 to export them (optional, default is no export)
 * pois = lon,lat,name|... (optional)
 * <p>
 * Example URLs:
 * {@code http://localhost:17777/brouter?lonlats=8.799297,49.565883|8.811764,49.563606&nogos=&profile=trekking&alternativeidx=0&format=gpx}
 * {@code http://localhost:17777/brouter?lonlats=1.1,1.2|2.1,2.2|3.1,3.2|4.1,4.2&nogos=-1.1,-1.2,1|-2.1,-2.2,2&profile=shortest&alternativeidx=1&format=kml&trackname=Ride&pois=1.1,2.1,Barner Bar}
 */

public class RouteRequestParser implements IRouteRequestParser {

  @Override
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

  @Override
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
