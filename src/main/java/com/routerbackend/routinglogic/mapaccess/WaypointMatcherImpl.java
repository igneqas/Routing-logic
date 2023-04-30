package com.routerbackend.routinglogic.mapaccess;

import com.routerbackend.routinglogic.codec.WaypointMatcher;
import com.routerbackend.routinglogic.core.OsmNode;
import com.routerbackend.routinglogic.utils.CheapRuler;
import com.routerbackend.routinglogic.utils.CheapAngleMeter;

import java.util.Comparator;
import java.util.List;

/**
 * the WaypointMatcher is feeded by the decoder with geoemtries of ways that are
 * already check for allowed access according to the current routing profile
 * <p>
 * It matches these geometries against the list of waypoints to find the best
 * match for each waypoint
 */
public final class WaypointMatcherImpl implements WaypointMatcher {
  private static final int MAX_POINTS = 5;

  private List<MatchedWaypoint> waypoints;
  private OsmNodePairSet islandPairs;

  private int lonStart;
  private int latStart;
  private int lonTarget;
  private int latTarget;
  private boolean anyUpdate;
  private int lonLast;
  private int latLast;

  public WaypointMatcherImpl(List<MatchedWaypoint> waypoints, OsmNodePairSet islandPairs) {
    this.waypoints = waypoints;
    this.islandPairs = islandPairs;
    MatchedWaypoint last = null;
    for (MatchedWaypoint mwp : waypoints) {
      mwp.radius = 250.0;
      if (last != null && mwp.directionToNext == -1) {
        last.directionToNext = CheapAngleMeter.getDirection(last.waypoint.longitude, last.waypoint.latitude, mwp.waypoint.longitude, mwp.waypoint.latitude);
      }
      last = mwp;
    }
    // last point has no angle so we are looking back
    int lastidx = waypoints.size() - 2;
    if (lastidx < 0) {
      last.directionToNext = -1;
    } else {
      last.directionToNext = CheapAngleMeter.getDirection(last.waypoint.longitude, last.waypoint.latitude, waypoints.get(lastidx).waypoint.longitude, waypoints.get(lastidx).waypoint.latitude);
    }
  }

  private void checkSegment(int lon1, int lat1, int lon2, int lat2) {
    double[] lonlat2m = CheapRuler.getLonLatToMeterScales((lat1 + lat2) >> 1);
    double dlon2m = lonlat2m[0];
    double dlat2m = lonlat2m[1];

    double dx = (lon2 - lon1) * dlon2m;
    double dy = (lat2 - lat1) * dlat2m;
    double d = Math.sqrt(dy * dy + dx * dx);

    if (d == 0.)
      return;

    for (MatchedWaypoint mwp : waypoints) {
      OsmNode wp = mwp.waypoint;
      double x1 = (lon1 - wp.longitude) * dlon2m;
      double y1 = (lat1 - wp.latitude) * dlat2m;
      double x2 = (lon2 - wp.longitude) * dlon2m;
      double y2 = (lat2 - wp.latitude) * dlat2m;
      double r12 = x1 * x1 + y1 * y1;
      double r22 = x2 * x2 + y2 * y2;
      double radius = Math.abs(r12 < r22 ? y1 * dx - x1 * dy : y2 * dx - x2 * dy) / d;

      if (radius <= mwp.radius) {
        double s1 = x1 * dx + y1 * dy;
        double s2 = x2 * dx + y2 * dy;

        if (s1 < 0.) {
          s1 = -s1;
          s2 = -s2;
        }
        if (s2 > 0.) {
          radius = Math.sqrt(s1 < s2 ? r12 : r22);
          if (radius > mwp.radius)
            continue;
        }
        // new match for that waypoint
        mwp.radius = radius; // shortest distance to way
        mwp.hasUpdate = true;
        anyUpdate = true;
        // calculate crosspoint
        if (mwp.crosspoint == null)
          mwp.crosspoint = new OsmNode();
        if (s2 < 0.) {
          double wayfraction = -s2 / (d * d);
          double xm = x2 - wayfraction * dx;
          double ym = y2 - wayfraction * dy;
          mwp.crosspoint.longitude = (int) (xm / dlon2m + wp.longitude);
          mwp.crosspoint.latitude = (int) (ym / dlat2m + wp.latitude);
        } else if (s1 > s2) {
          mwp.crosspoint.longitude = lon2;
          mwp.crosspoint.latitude = lat2;
        } else {
          mwp.crosspoint.longitude = lon1;
          mwp.crosspoint.latitude = lat1;
        }
      }
    }
  }

  @Override
  public boolean start(int ilonStart, int ilatStart, int ilonTarget, int ilatTarget) {
    if (islandPairs.size() > 0) {
      long n1 = ((long) ilonStart) << 32 | ilatStart;
      long n2 = ((long) ilonTarget) << 32 | ilatTarget;
      if (islandPairs.hasPair(n1, n2)) {
        return false;
      }
    }
    lonLast = lonStart = ilonStart;
    latLast = latStart = ilatStart;
    lonTarget = ilonTarget;
    latTarget = ilatTarget;
    anyUpdate = false;
    return true;
  }

  @Override
  public void transferNode(int ilon, int ilat) {
    checkSegment(lonLast, latLast, ilon, ilat);
    lonLast = ilon;
    latLast = ilat;
  }

  @Override
  public void end() {
    checkSegment(lonLast, latLast, lonTarget, latTarget);
    if (anyUpdate) {
      for (MatchedWaypoint mwp : waypoints) {
        if (mwp.hasUpdate) {
          double angle = CheapAngleMeter.getDirection(lonStart, latStart, lonTarget, latTarget);
          double diff = CheapAngleMeter.getDifferenceFromDirection(mwp.directionToNext, angle);

          mwp.hasUpdate = false;

          MatchedWaypoint mw = new MatchedWaypoint();
          mw.waypoint = new OsmNode();
          mw.waypoint.longitude = mwp.waypoint.longitude;
          mw.waypoint.latitude = mwp.waypoint.latitude;
          mw.crosspoint = new OsmNode();
          mw.crosspoint.longitude = mwp.crosspoint.longitude;
          mw.crosspoint.latitude = mwp.crosspoint.latitude;
          mw.node1 = new OsmNode(lonStart, latStart);
          mw.node2 = new OsmNode(lonTarget, latTarget);
          mw.name = mwp.name + "_w_" + mwp.crosspoint.hashCode();
          mw.radius = mwp.radius;
          mw.directionDiff = diff;
          mw.directionToNext = mwp.directionToNext;

          updateWayList(mwp.wayNearest, mw);

          // revers
          angle = CheapAngleMeter.getDirection(lonTarget, latTarget, lonStart, latStart);
          diff = CheapAngleMeter.getDifferenceFromDirection(mwp.directionToNext, angle);
          mw = new MatchedWaypoint();
          mw.waypoint = new OsmNode();
          mw.waypoint.longitude = mwp.waypoint.longitude;
          mw.waypoint.latitude = mwp.waypoint.latitude;
          mw.crosspoint = new OsmNode();
          mw.crosspoint.longitude = mwp.crosspoint.longitude;
          mw.crosspoint.latitude = mwp.crosspoint.latitude;
          mw.node1 = new OsmNode(lonTarget, latTarget);
          mw.node2 = new OsmNode(lonStart, latStart);
          mw.name = mwp.name + "_w2_" + mwp.crosspoint.hashCode();
          mw.radius = mwp.radius;
          mw.directionDiff = diff;
          mw.directionToNext = mwp.directionToNext;

          updateWayList(mwp.wayNearest, mw);

          MatchedWaypoint way = mwp.wayNearest.get(0);
          mwp.crosspoint.longitude = way.crosspoint.longitude;
          mwp.crosspoint.latitude = way.crosspoint.latitude;
          mwp.node1 = new OsmNode(way.node1.longitude, way.node1.latitude);
          mwp.node2 = new OsmNode(way.node2.longitude, way.node2.latitude);
          mwp.directionDiff = way.directionDiff;
          mwp.radius = way.radius;

        }
      }
    }
  }

  // check limit of list size (avoid long runs)
  void updateWayList(List<MatchedWaypoint> ways, MatchedWaypoint mw) {
    ways.add(mw);
    // use only shortest distances by smallest direction difference
    Comparator<MatchedWaypoint> comparator = Comparator.comparingDouble((MatchedWaypoint matchedWaypoint) -> matchedWaypoint.radius).thenComparingDouble(matchedWaypoint -> matchedWaypoint.directionDiff);
    ways.sort(comparator);
    if (ways.size() > MAX_POINTS) ways.remove(MAX_POINTS);
  }
}
