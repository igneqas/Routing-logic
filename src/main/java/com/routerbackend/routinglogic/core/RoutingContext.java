/**
 * Container for routig configs
 *
 * @author ab
 */
package com.routerbackend.routinglogic.core;

import com.routerbackend.routinglogic.expressions.BExpressionContext;
import com.routerbackend.routinglogic.expressions.BExpressionContextNode;
import com.routerbackend.routinglogic.expressions.BExpressionContextWay;
import com.routerbackend.routinglogic.mapaccess.GeometryDecoder;
import com.routerbackend.routinglogic.mapaccess.MatchedWaypoint;
import com.routerbackend.routinglogic.utils.CheapAngleMeter;
import com.routerbackend.routinglogic.utils.CheapRuler;

import java.io.DataOutput;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.routerbackend.Constants.MEMORY_CLASS;

public final class RoutingContext {
  private int alternativeIdx;
  private String profileName;
  public long profileTimestamp;

  public Map<String, String> keyValues;

  public BExpressionContextWay expressionContextWay;
  public BExpressionContextNode expressionContextNode;

  public GeometryDecoder geometryDecoder = new GeometryDecoder();

  public int memoryClass = MEMORY_CLASS;

  public int downhillcostdiv;
  public int downhillcutoff;
  public int uphillcostdiv;
  public int uphillcutoff;
  public boolean carMode;
  public boolean bikeMode;
  public boolean footMode;
  public boolean considerTurnRestrictions;
  public boolean processUnusedTags;
  public boolean forceSecondaryData;
  public double pass1coefficient;
  public double pass2coefficient;
  public int elevationpenaltybuffer;
  public int elevationmaxbuffer;
  public int elevationbufferreduce;

  public double cost1speed;
  public double additionalcostfactor;
  public double changetime;
  public double buffertime;
  public double waittimeadjustment;
  public double inittimeadjustment;
  public double starttimeoffset;
  public boolean transitonly;

  public double waypointCatchingRange;
  public boolean correctMisplacedViaPoints;
  public double correctMisplacedViaPointsDistance;

  private void setModel(String className) {
    if (className == null) {
      pm = new StdModel();
    } else {
      try {
        Class<?> clazz = Class.forName(className);
        pm = (OsmPathModel) clazz.getDeclaredConstructor().newInstance();
      } catch (Exception e) {
        throw new RuntimeException("Cannot create path-model: " + e);
      }
    }
    initModel();
  }

  public void initModel() {
    pm.init(expressionContextWay, expressionContextNode, keyValues);
  }

  public long getKeyValueChecksum() {
    long s = 0L;
    if (keyValues != null) {
      for (Map.Entry<String, String> e : keyValues.entrySet()) {
        s += e.getKey().hashCode() + e.getValue().hashCode();
      }
    }
    return s;
  }

  public void readGlobalConfig() {
    BExpressionContext expressionContextGlobal = expressionContextWay;

    if (keyValues != null) {
      // add parameter to context
      for (Map.Entry<String, String> e : keyValues.entrySet()) {
        float f = Float.parseFloat(e.getValue());
        expressionContextWay.setVariableValue(e.getKey(), f, true);
        expressionContextNode.setVariableValue(e.getKey(), f, true);
      }
    }

    setModel(expressionContextGlobal._modelClass);

    downhillcostdiv = (int) expressionContextGlobal.getVariableValue("downhillcost", 0.f);
    downhillcutoff = (int) (expressionContextGlobal.getVariableValue("downhillcutoff", 0.f) * 10000);
    uphillcostdiv = (int) expressionContextGlobal.getVariableValue("uphillcost", 0.f);
    uphillcutoff = (int) (expressionContextGlobal.getVariableValue("uphillcutoff", 0.f) * 10000);
    if (downhillcostdiv != 0) downhillcostdiv = 1000000 / downhillcostdiv;
    if (uphillcostdiv != 0) uphillcostdiv = 1000000 / uphillcostdiv;
    carMode = 0.f != expressionContextGlobal.getVariableValue("validForCars", 0.f);
    bikeMode = 0.f != expressionContextGlobal.getVariableValue("validForBikes", 0.f);
    footMode = 0.f != expressionContextGlobal.getVariableValue("validForFoot", 0.f);

    waypointCatchingRange = expressionContextGlobal.getVariableValue("waypointCatchingRange", 250.f);

    // turn-restrictions not used per default for foot profiles
    considerTurnRestrictions = 0.f != expressionContextGlobal.getVariableValue("considerTurnRestrictions", footMode ? 0.f : 1.f);

    correctMisplacedViaPoints = 0.f != expressionContextGlobal.getVariableValue("correctMisplacedViaPoints", 1.f);
    correctMisplacedViaPointsDistance = expressionContextGlobal.getVariableValue("correctMisplacedViaPointsDistance", 40.f);

    // process tags not used in the profile (to have them in the data-tab)
    processUnusedTags = 0.f != expressionContextGlobal.getVariableValue("processUnusedTags", 0.f);

    forceSecondaryData = 0.f != expressionContextGlobal.getVariableValue("forceSecondaryData", 0.f);
    pass1coefficient = expressionContextGlobal.getVariableValue("pass1coefficient", 1.5f);
    pass2coefficient = expressionContextGlobal.getVariableValue("pass2coefficient", 0.f);
    elevationpenaltybuffer = (int) (expressionContextGlobal.getVariableValue("elevationpenaltybuffer", 5.f) * 1000000);
    elevationmaxbuffer = (int) (expressionContextGlobal.getVariableValue("elevationmaxbuffer", 10.f) * 1000000);
    elevationbufferreduce = (int) (expressionContextGlobal.getVariableValue("elevationbufferreduce", 0.f) * 10000);

    cost1speed = expressionContextGlobal.getVariableValue("cost1speed", 22.f);
    additionalcostfactor = expressionContextGlobal.getVariableValue("additionalcostfactor", 1.5f);
    changetime = expressionContextGlobal.getVariableValue("changetime", 180.f);
    buffertime = expressionContextGlobal.getVariableValue("buffertime", 120.f);
    waittimeadjustment = expressionContextGlobal.getVariableValue("waittimeadjustment", 0.9f);
    inittimeadjustment = expressionContextGlobal.getVariableValue("inittimeadjustment", 0.2f);
    starttimeoffset = expressionContextGlobal.getVariableValue("starttimeoffset", 0.f);
    transitonly = expressionContextGlobal.getVariableValue("transitonly", 0.f) != 0.f;

    farTrafficWeight = expressionContextGlobal.getVariableValue("farTrafficWeight", 2.f);
    nearTrafficWeight = expressionContextGlobal.getVariableValue("nearTrafficWeight", 2.f);
    farTrafficDecayLength = expressionContextGlobal.getVariableValue("farTrafficDecayLength", 30000.f);
    nearTrafficDecayLength = expressionContextGlobal.getVariableValue("nearTrafficDecayLength", 3000.f);
    trafficDirectionFactor = expressionContextGlobal.getVariableValue("trafficDirectionFactor", 0.9f);
    trafficSourceExponent = expressionContextGlobal.getVariableValue("trafficSourceExponent", -0.7f);
    trafficSourceMinDist = expressionContextGlobal.getVariableValue("trafficSourceMinDist", 3000.f);

    showspeed = 0.f != expressionContextGlobal.getVariableValue("showspeed", 0.f);
    showSpeedProfile = 0.f != expressionContextGlobal.getVariableValue("showSpeedProfile", 0.f);
    inverseRouting = 0.f != expressionContextGlobal.getVariableValue("inverseRouting", 0.f);
    showTime = 0.f != expressionContextGlobal.getVariableValue("showtime", 0.f);

    int tiMode = (int) expressionContextGlobal.getVariableValue("turnInstructionMode", 0.f);
    if (tiMode != 1) // automatic selection from coordinate source
    {
      turnInstructionMode = tiMode;
    }
    turnInstructionCatchingRange = expressionContextGlobal.getVariableValue("turnInstructionCatchingRange", 40.f);
    turnInstructionRoundabouts = expressionContextGlobal.getVariableValue("turnInstructionRoundabouts", 1.f) != 0.f;

    // Speed computation model (for bikes)
    // Total mass (biker + bike + luggages or hiker), in kg
    totalMass = expressionContextGlobal.getVariableValue("totalMass", 90.f);
    // Max speed (before braking), in km/h in profile and m/s in code
    if (footMode) {
      maxSpeed = expressionContextGlobal.getVariableValue("maxSpeed", 6.f) / 3.6;
    } else {
      maxSpeed = expressionContextGlobal.getVariableValue("maxSpeed", 45.f) / 3.6;
    }
    // Equivalent surface for wind, S * C_x, F = -1/2 * S * C_x * v^2 = - S_C_x * v^2
    S_C_x = expressionContextGlobal.getVariableValue("S_C_x", 0.5f * 0.45f);
    // Default resistance of the road, F = - m * g * C_r (for good quality road)
    defaultC_r = expressionContextGlobal.getVariableValue("C_r", 0.01f);
    // Constant power of the biker (in W)
    bikerPower = expressionContextGlobal.getVariableValue("bikerPower", 100.f);
  }

  public List<OsmNodeNamed> poipoints;
  public List<OsmNodeNamed> noGoPoints = null;
  private List<OsmNodeNamed> nogopoints_all = null; // full list not filtered for wayoints-in-nogos
  private List<OsmNodeNamed> keepnogopoints = null;
  private OsmNodeNamed pendingEndpoint = null;

  public Integer startDirection;
  public boolean startDirectionValid;
  public boolean forceUseStartDirection;

  public CheapAngleMeter anglemeter = new CheapAngleMeter();

  public double nogoCost = 0.;
  public boolean isEndpoint = false;

  public boolean shortestmatch = false;
  public double wayfraction;
  public int ilatshortest;
  public int ilonshortest;

  public boolean countTraffic;
  public boolean inverseDirection;
  public DataOutput trafficOutputStream;

  public double farTrafficWeight;
  public double nearTrafficWeight;
  public double farTrafficDecayLength;
  public double nearTrafficDecayLength;
  public double trafficDirectionFactor;
  public double trafficSourceExponent;
  public double trafficSourceMinDist;

  public boolean showspeed;
  public boolean showSpeedProfile;
  public boolean inverseRouting;
  public boolean showTime;

  public OsmPrePath firstPrePath;

  public int turnInstructionMode; // 0=none, 1=auto, 2=locus, 3=osmand, 4=comment-style, 5=gpsies-style
  public double turnInstructionCatchingRange;
  public boolean turnInstructionRoundabouts;

  // Speed computation model (for bikes)
  public double totalMass;
  public double maxSpeed;
  public double S_C_x;
  public double defaultC_r;
  public double bikerPower;

  /**
   * restore the full nogolist previously saved by cleanNogoList
   */
  public void restoreNoGoList() {
    noGoPoints = nogopoints_all;
  }

  /**
   * clean the nogolist (previoulsy saved by saveFullNogolist())
   * by removing nogos with waypoints within
   *
   * @return true if all wayoints are all in the same (full-weigth) nogo area (triggering bee-line-mode)
   */
  public void cleanNoGoList(List<OsmNode> waypoints) {
    nogopoints_all = noGoPoints;
    if (noGoPoints == null) return;
    List<OsmNodeNamed> nogos = new ArrayList<OsmNodeNamed>();
    for (OsmNodeNamed nogo : noGoPoints) {
      boolean goodGuy = true;
      for (OsmNode wp : waypoints) {
        if (wp.calcDistance(nogo) < nogo.radius
          && (!(nogo instanceof OsmNogoPolygon)
          || (((OsmNogoPolygon) nogo).isClosed
          ? ((OsmNogoPolygon) nogo).isWithin(wp.longitude, wp.latitude)
          : ((OsmNogoPolygon) nogo).isOnPolyline(wp.longitude, wp.latitude)))) {
          goodGuy = false;
        }
      }
      if (goodGuy) nogos.add(nogo);
    }
    noGoPoints = nogos.isEmpty() ? null : nogos;
  }

  public void checkMatchedWaypointAgainstNoGos(List<MatchedWaypoint> matchedWaypoints) {
    if (noGoPoints == null) return;
    List<MatchedWaypoint> newMatchedWaypoints = new ArrayList<>();
    int theSize = matchedWaypoints.size();
    int removed = 0;
    MatchedWaypoint previousMatchedWaypoint = null;
    boolean previousMatchedWaypointIsInside = false;
    for (int i = 0; i < theSize; i++) {
      MatchedWaypoint matchedWaypoint = matchedWaypoints.get(i);
      boolean isInsideNoGo = false;
      OsmNode wp = matchedWaypoint.crosspoint;
      for (OsmNodeNamed noGoPoint : noGoPoints) {
        if (wp.calcDistance(noGoPoint) < noGoPoint.radius
          && (!(noGoPoint instanceof OsmNogoPolygon)
          || (((OsmNogoPolygon) noGoPoint).isClosed
          ? ((OsmNogoPolygon) noGoPoint).isWithin(wp.longitude, wp.latitude)
          : ((OsmNogoPolygon) noGoPoint).isOnPolyline(wp.longitude, wp.latitude)))) {
          isInsideNoGo = true;
          break;
        }
      }
      if (isInsideNoGo) {
        boolean useAnyway = false;
        if (previousMatchedWaypoint == null) useAnyway = true;
        else if (matchedWaypoint.direct) useAnyway = true;
        else if (previousMatchedWaypoint.direct) useAnyway = true;
        else if (previousMatchedWaypointIsInside) useAnyway = true;
        else if (i == theSize-1) {
          throw new IllegalArgumentException("last wpt in restricted area ");
        }
        if (useAnyway) {
          previousMatchedWaypointIsInside = true;
          newMatchedWaypoints.add(matchedWaypoint);
        } else {
          removed++;
          previousMatchedWaypointIsInside = false;
        }

      } else {
        previousMatchedWaypointIsInside = false;
        newMatchedWaypoints.add(matchedWaypoint);
      }
      previousMatchedWaypoint = matchedWaypoint;
    }
    if (newMatchedWaypoints.size() < 2) {
      throw new IllegalArgumentException("a wpt in restricted area ");
    }
    if (removed > 0) {
      matchedWaypoints.clear();
      matchedWaypoints.addAll(newMatchedWaypoints);
    }
  }

  public long[] getNogoChecksums() {
    long[] cs = new long[3];
    int n = noGoPoints == null ? 0 : noGoPoints.size();
    for (int i = 0; i < n; i++) {
      OsmNodeNamed nogo = noGoPoints.get(i);
      cs[0] += nogo.longitude;
      cs[1] += nogo.latitude;
      // 10 is an arbitrary constant to get sub-integer precision in the checksum
      cs[2] += (long) (nogo.radius * 10.);
    }
    return cs;
  }

  public void setWaypoint(OsmNodeNamed wp, boolean endpoint) {
    setWaypoint(wp, null, endpoint);
  }

  public void setWaypoint(OsmNodeNamed wp, OsmNodeNamed pendingEndpoint, boolean endpoint) {
    keepnogopoints = noGoPoints;
    noGoPoints = new ArrayList<>();
    noGoPoints.add(wp);
    if (keepnogopoints != null) noGoPoints.addAll(keepnogopoints);
    isEndpoint = endpoint;
    this.pendingEndpoint = pendingEndpoint;
  }

  public boolean checkPendingEndpoint() {
    if (pendingEndpoint != null) {
      isEndpoint = true;
      noGoPoints.set(0, pendingEndpoint);
      pendingEndpoint = null;
      return true;
    }
    return false;
  }

  public void unsetWaypoint() {
    noGoPoints = keepnogopoints;
    pendingEndpoint = null;
    isEndpoint = false;
  }

  public int calcDistance(int lon1, int lat1, int lon2, int lat2) {
    double[] lonlat2m = CheapRuler.getLonLatToMeterScales((lat1 + lat2) >> 1);
    double dlon2m = lonlat2m[0];
    double dlat2m = lonlat2m[1];
    double dx = (lon2 - lon1) * dlon2m;
    double dy = (lat2 - lat1) * dlat2m;
    double d = Math.sqrt(dy * dy + dx * dx);

    shortestmatch = false;

    if (noGoPoints != null && !noGoPoints.isEmpty() && d > 0.) {
      for (int ngidx = 0; ngidx < noGoPoints.size(); ngidx++) {
        OsmNodeNamed nogo = noGoPoints.get(ngidx);
        double x1 = (lon1 - nogo.longitude) * dlon2m;
        double y1 = (lat1 - nogo.latitude) * dlat2m;
        double x2 = (lon2 - nogo.longitude) * dlon2m;
        double y2 = (lat2 - nogo.latitude) * dlat2m;
        double r12 = x1 * x1 + y1 * y1;
        double r22 = x2 * x2 + y2 * y2;
        double radius = Math.abs(r12 < r22 ? y1 * dx - x1 * dy : y2 * dx - x2 * dy) / d;

        if (radius < nogo.radius) // 20m
        {
          double s1 = x1 * dx + y1 * dy;
          double s2 = x2 * dx + y2 * dy;


          if (s1 < 0.) {
            s1 = -s1;
            s2 = -s2;
          }
          if (s2 > 0.) {
            radius = Math.sqrt(s1 < s2 ? r12 : r22);
            if (radius > nogo.radius) continue;
          }
          if (nogo.isNoGo) {
            if (!(nogo instanceof OsmNogoPolygon)) {  // nogo is a circle
              if (Double.isNaN(nogo.noGoWeight)) {
                // default nogo behaviour (ignore completely)
                nogoCost = -1;
              } else {
                // nogo weight, compute distance within the circle
                nogoCost = nogo.distanceWithinRadius(lon1, lat1, lon2, lat2, d) * nogo.noGoWeight;
              }
            } else if (((OsmNogoPolygon) nogo).intersects(lon1, lat1, lon2, lat2)) {
              // nogo is a polyline/polygon, we have to check there is indeed
              // an intersection in this case (radius check is not enough).
              if (Double.isNaN(nogo.noGoWeight)) {
                // default nogo behaviour (ignore completely)
                nogoCost = -1;
              } else {
                if (((OsmNogoPolygon) nogo).isClosed) {
                  // compute distance within the polygon
                  nogoCost = ((OsmNogoPolygon) nogo).distanceWithinPolygon(lon1, lat1, lon2, lat2) * nogo.noGoWeight;
                } else {
                  // for a polyline, just add a constant penalty
                  nogoCost = nogo.noGoWeight;
                }
              }
            }
          } else {
            shortestmatch = true;
            nogo.radius = radius; // shortest distance to way
            // calculate remaining distance
            if (s2 < 0.) {
              wayfraction = -s2 / (d * d);
              double xm = x2 - wayfraction * dx;
              double ym = y2 - wayfraction * dy;
              ilonshortest = (int) (xm / dlon2m + nogo.longitude);
              ilatshortest = (int) (ym / dlat2m + nogo.latitude);
            } else if (s1 > s2) {
              wayfraction = 0.;
              ilonshortest = lon2;
              ilatshortest = lat2;
            } else {
              wayfraction = 1.;
              ilonshortest = lon1;
              ilatshortest = lat1;
            }

            // here it gets nasty: there can be nogo-points in the list
            // *after* the shortest distance point. In case of a shortest-match
            // we use the reduced way segment for nogo-matching, in order not
            // to cut our escape-way if we placed a nogo just in front of where we are
            if (isEndpoint) {
              wayfraction = 1. - wayfraction;
              lon2 = ilonshortest;
              lat2 = ilatshortest;
            } else {
              nogoCost = 0.;
              lon1 = ilonshortest;
              lat1 = ilatshortest;
            }
            dx = (lon2 - lon1) * dlon2m;
            dy = (lat2 - lat1) * dlat2m;
            d = Math.sqrt(dy * dy + dx * dx);
          }
        }
      }
    }
    return (int) Math.max(1.0, Math.round(d));
  }

  public OsmPathModel pm;

  public OsmPrePath createPrePath(OsmPath origin, OsmLink link) {
    OsmPrePath p = pm.createPrePath();
    if (p != null) {
      p.init(origin, link, this);
    }
    return p;
  }

  public OsmPath createPath(OsmLink link) {
    OsmPath p = pm.createPath();
    p.init(link);
    return p;
  }

  public OsmPath createPath(OsmPath origin, OsmLink link, OsmTrack refTrack, boolean detailMode) {
    OsmPath p = pm.createPath();
    p.init(origin, link, refTrack, detailMode, this);
    return p;
  }

  public void setAlternativeIdx(int idx) {
    alternativeIdx = idx;
  }

  public int getAlternativeIdx(int min, int max) {
    return alternativeIdx < min ? min : (alternativeIdx > max ? max : alternativeIdx);
  }

  public String getProfileName() {
    return profileName;
  }

  public void setProfileName(String profileName) {
    this.profileName = profileName;
  }
}