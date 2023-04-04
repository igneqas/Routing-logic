/**
 * Information on matched way point
 *
 * @author ab
 */
package com.routerbackend.mapaccess;

import com.routerbackend.core.OsmNode;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
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

  public void writeToStream(DataOutput dos) throws IOException {
    dos.writeInt(node1.latitude);
    dos.writeInt(node1.longitude);
    dos.writeInt(node2.latitude);
    dos.writeInt(node2.longitude);
    dos.writeInt(crosspoint.latitude);
    dos.writeInt(crosspoint.longitude);
    dos.writeInt(waypoint.latitude);
    dos.writeInt(waypoint.longitude);
    dos.writeDouble(radius);
  }

  public static MatchedWaypoint readFromStream(DataInput dis) throws IOException {
    MatchedWaypoint mwp = new MatchedWaypoint();
    mwp.node1 = new OsmNode();
    mwp.node2 = new OsmNode();
    mwp.crosspoint = new OsmNode();
    mwp.waypoint = new OsmNode();

    mwp.node1.latitude = dis.readInt();
    mwp.node1.longitude = dis.readInt();
    mwp.node2.latitude = dis.readInt();
    mwp.node2.longitude = dis.readInt();
    mwp.crosspoint.latitude = dis.readInt();
    mwp.crosspoint.longitude = dis.readInt();
    mwp.waypoint.latitude = dis.readInt();
    mwp.waypoint.longitude = dis.readInt();
    mwp.radius = dis.readDouble();
    return mwp;
  }

}
