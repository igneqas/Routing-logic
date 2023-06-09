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

import java.util.ArrayList;
import java.util.List;

public class RoutingContext {
  private int alternativeIdx;
  private String profileName;
  public BExpressionContextWay expressionContextWay;
  public BExpressionContextNode expressionContextNode;

  public GeometryDecoder geometryDecoder = new GeometryDecoder();
  public int downhillcostdiv;
  public int downhillcutoff;
  public int uphillcostdiv;
  public int uphillcutoff;
  public boolean carMode;
  public boolean bikeMode;
  public boolean footMode;
  public boolean considerTurnRestrictions;
  public double pass1coefficient;
  public double pass2coefficient;
  public int elevationpenaltybuffer;
  public int elevationmaxbuffer;
  public int elevationbufferreduce;

  public void readGlobalConfig() {
    BExpressionContext expressionContextGlobal = expressionContextWay;
    downhillcostdiv = (int) expressionContextGlobal.getVariableValue("downhillcost", 0.f);
    downhillcutoff = (int) (expressionContextGlobal.getVariableValue("downhillcutoff", 0.f) * 10000);
    uphillcostdiv = (int) expressionContextGlobal.getVariableValue("uphillcost", 0.f);
    uphillcutoff = (int) (expressionContextGlobal.getVariableValue("uphillcutoff", 0.f) * 10000);
    if (downhillcostdiv != 0) downhillcostdiv = 1000000 / downhillcostdiv;
    if (uphillcostdiv != 0) uphillcostdiv = 1000000 / uphillcostdiv;
    carMode = 0.f != expressionContextGlobal.getVariableValue("validForCars", 0.f);
    bikeMode = 0.f != expressionContextGlobal.getVariableValue("validForBikes", 0.f);
    footMode = 0.f != expressionContextGlobal.getVariableValue("validForFoot", 0.f);

    // turn-restrictions not used per default for foot profiles
    considerTurnRestrictions = 0.f != expressionContextGlobal.getVariableValue("considerTurnRestrictions", footMode ? 0.f : 1.f);

    pass1coefficient = expressionContextGlobal.getVariableValue("pass1coefficient", 1.5f);
    pass2coefficient = expressionContextGlobal.getVariableValue("pass2coefficient", 0.f);
    elevationpenaltybuffer = (int) (expressionContextGlobal.getVariableValue("elevationpenaltybuffer", 5.f) * 1000000);
    elevationmaxbuffer = (int) (expressionContextGlobal.getVariableValue("elevationmaxbuffer", 10.f) * 1000000);
    elevationbufferreduce = (int) (expressionContextGlobal.getVariableValue("elevationbufferreduce", 0.f) * 10000);

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
  }

  public List<OsmNodeNamed> noGoPoints = null;
  private List<OsmNodeNamed> keepnogopoints = null;
  private OsmNodeNamed pendingEndpoint = null;
  public boolean startDirectionValid;
  public CheapAngleMeter anglemeter = new CheapAngleMeter();
  public double nogoCost = 0.;
  public boolean isEndpoint = false;
  public boolean shortestmatch = false;
  public double wayfraction;
  public int ilatshortest;
  public int ilonshortest;
  public boolean inverseDirection;

  // Speed computation model (for bikes)
  public double totalMass;
  public double maxSpeed;
  public double S_C_x;

  public void checkMatchedWaypointAgainstNoGos(List<MatchedWaypoint> matchedWaypoints) {
    if (noGoPoints == null) return;
    for (MatchedWaypoint mwp : matchedWaypoints) {
      OsmNode wp = mwp.crosspoint;
      noGoPoints.removeIf(noGo -> wp.calcDistance(noGo) < noGo.radius);
    }
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
            if (Double.isNaN(nogo.noGoWeight)) {
              // default nogo behaviour (ignore completely)
              nogoCost = -1;
            } else {
              // nogo weight, compute distance within the circle
              nogoCost = nogo.distanceWithinRadius(lon1, lat1, lon2, lat2, d) * nogo.noGoWeight;
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

  public OsmPath createPath(OsmLink link) {
    OsmPath p = new StdPath();
    p.init(link);
    return p;
  }

  public OsmPath createPath(OsmPath origin, OsmLink link, OsmTrack refTrack, boolean detailMode) {
    OsmPath p = new StdPath();
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
