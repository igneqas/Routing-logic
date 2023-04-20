/**
 * Information on matched way point
 *
 * @author ab
 */
package com.routerbackend.routinglogic.mapaccess;

import com.routerbackend.routinglogic.core.OsmNode;

import java.util.ArrayList;
import java.util.List;

public final class MatchedWaypoint {
  public OsmNode node1;
  public OsmNode node2;
  public OsmNode crosspoint;
  public OsmNode waypoint;
  public String name;  // waypoint name used in error messages
  public double radius;  // distance in meter between waypoint and crosspoint
  public boolean direct;  // from this point go direct to next = beeline routing
  public int indexInTrack = 0;
  public double directionToNext = -1;
  public double directionDiff = 361;

  public List<MatchedWaypoint> wayNearest = new ArrayList<>();
  public boolean hasUpdate;
}
