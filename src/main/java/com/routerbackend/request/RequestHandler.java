package com.routerbackend.request;

import com.routerbackend.core.OsmNodeNamed;
import com.routerbackend.core.OsmTrack;
import com.routerbackend.core.RoutingContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
public class RequestHandler extends IRequestHandler {

  @Override
  public RoutingContext readRoutingContext(String profile, String noGos, String alternativeIdx) {
    RoutingContext routingContext = new RoutingContext();
    routingContext.setProfileName(profile);
    routingContext.setAlternativeIdx(alternativeIdx != null ? Integer.parseInt(alternativeIdx) : 0);

    List<OsmNodeNamed> noGoList = readNoGoList(noGos);
//    List<OsmNodeNamed> nogoPolygonsList = readNogoPolygons();

    if (noGoList != null) {
      RoutingContext.prepareNogoPoints(noGoList);
      routingContext.nogopoints = noGoList;
    }

//    if (rc.nogopoints == null) {
//      rc.nogopoints = nogoPolygonsList;
//    } else if (nogoPolygonsList != null) {
//      rc.nogopoints.addAll(nogoPolygonsList);
//    }

    return routingContext;
  }

  @Override
  public List<OsmNodeNamed> readWayPointList(String lonLats) {
    String[] coords = lonLats.split("\\;");
    if (coords.length < 2)
      throw new IllegalArgumentException("we need two lat/lon points at least!");

    List<OsmNodeNamed> wplist = new ArrayList<>();
    for (int i = 0; i < coords.length; i++) {
      String[] lonLat = coords[i].split(",");
      if (lonLat.length < 2)
        throw new IllegalArgumentException("we need two lat/lon points at least!");
      wplist.add(readPosition(lonLat[0], lonLat[1], "via" + i));
      if (lonLat.length > 2) {
        if (lonLat[2].equals("d")) {
          wplist.get(wplist.size()-1).direct = true;
        } else {
          wplist.get(wplist.size()-1).name = lonLat[2];
        }
      }
    }

    wplist.get(0).name = "from";
    wplist.get(wplist.size() - 1).name = "to";

    return wplist;
  }

  @Override
  public String formatTrack(OsmTrack track) {
    String result;
    // optional, may be null
//    String format = params.get("format");
//    String trackName = getTrackName();
//    if (trackName != null) {
//      track.name = trackName;
//    }
//    String exportWaypointsStr = params.get("exportWaypoints");
//    if (exportWaypointsStr != null && Integer.parseInt(exportWaypointsStr) != 0) {
//      track.exportWaypoints = true;
//    }

//    if (format == null || "gpx".equals(format)) {
//      result = track.formatAsGpx();
//    } else if ("kml".equals(format)) {
//      result = track.formatAsKml();
//    } else if ("geojson".equals(format)) {
//      result = track.formatAsGeoJson();
//    } else if ("csv".equals(format)) {
//      try {
//        StringWriter sw = new StringWriter();
//        BufferedWriter bw = new BufferedWriter(sw);
//        track.writeMessages(bw, rc);
//        return sw.toString();
//      } catch (Exception ex) {
//        return "Error: " + ex.getMessage();
//      }
//    } else {
//      System.out.println("unknown track format '" + format + "', using default");
//      result = track.formatAsGpx();
//    }
    result = track.formatAsGeoJson();

    return result;
  }

  @Override
  public String getMimeType() {
    // default
    String result = "text/plain";

    // optional, may be null
    String format = params.get("format");
    if (format != null) {
      if ("gpx".equals(format)) {
        result = "application/gpx+xml";
      } else if ("kml".equals(format)) {
        result = "application/vnd.google-earth.kml+xml";
      } else if ("geojson".equals(format)) {
        result = "application/vnd.geo+json";
      } else if ("csv".equals(format)) {
        result = "text/tab-separated-values";
      }
    }

    return result;
  }

  @Override
  public String getFileName() {
    String fileName = null;
    String format = params.get("format");
    String trackName = getTrackName();

    if (format != null) {
      fileName = (trackName == null ? "brouter" : trackName) + "." + format;
    }

    return fileName;
  }

  private String getTrackName() {
    return params.get("trackname") == null ? null : params.get("trackname").replaceAll("[^a-zA-Z0-9 \\._\\-]+", "");
  }

  private static OsmNodeNamed readPosition(String vlon, String vlat, String name) {
    if (vlon == null) throw new IllegalArgumentException("lon " + name + " not found in input");
    if (vlat == null) throw new IllegalArgumentException("lat " + name + " not found in input");

    return readPosition(Double.parseDouble(vlon), Double.parseDouble(vlat), name);
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
    node.name = "nogo" + radius;
    node.longitude = (int) ((longitude + 180.) * 1000000. + 0.5);
    node.latitude = (int) ((latitude + 90.) * 1000000. + 0.5);
    node.isNoGo = true;
    node.noGoWeight = noGoWeight;
    return node;
  }

//  private List<OsmNodeNamed> readNogoPolygons() {
//    List<OsmNodeNamed> result = new ArrayList<OsmNodeNamed>();
//    parseNogoPolygons(params.get("polylines"), result, false);
//    parseNogoPolygons(params.get("polygons"), result, true);
//    return result.size() > 0 ? result : null;
//  }
//
//  private static void parseNogoPolygons(String polygons, List<OsmNodeNamed> result, boolean closed) {
//    if (polygons != null) {
//      String[] polygonList = polygons.split("\\|");
//      for (int i = 0; i < polygonList.length; i++) {
//        String[] lonLatList = polygonList[i].split(",");
//        if (lonLatList.length > 1) {
//          OsmNogoPolygon polygon = new OsmNogoPolygon(closed);
//          int j;
//          for (j = 0; j < 2 * (lonLatList.length / 2) - 1; ) {
//            String slon = lonLatList[j++];
//            String slat = lonLatList[j++];
//            int lon = (int) ((Double.parseDouble(slon) + 180.) * 1000000. + 0.5);
//            int lat = (int) ((Double.parseDouble(slat) + 90.) * 1000000. + 0.5);
//            polygon.addVertex(lon, lat);
//          }
//
//          String nogoWeight = "NaN";
//          if (j < lonLatList.length) {
//            nogoWeight = lonLatList[j];
//          }
//          polygon.nogoWeight = Double.parseDouble(nogoWeight);
//
//          if (polygon.points.size() > 0) {
//            polygon.calcBoundingCircle();
//            result.add(polygon);
//          }
//        }
//      }
//    }
//  }
}
