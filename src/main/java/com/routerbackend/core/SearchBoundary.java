/**
 * static helper class for handling datafiles
 *
 * @author ab
 */
package com.routerbackend.core;

public final class SearchBoundary {

  private int minlon0;
  private int minlat0;
  private int maxlon0;
  private int maxlat0;

  private int minlon;
  private int minlat;
  private int maxlon;
  private int maxlat;
  private int radius;
  private OsmNode p;

  int direction;

  /**
   * @param radius Search radius in meters.
   */
  public SearchBoundary(OsmNode n, int radius, int direction) {
    this.radius = radius;
    this.direction = direction;

    p = new OsmNode(n.longitude, n.latitude);

    int lon = (n.longitude / 5000000) * 5000000;
    int lat = (n.latitude / 5000000) * 5000000;

    minlon0 = lon - 5000000;
    minlat0 = lat - 5000000;
    maxlon0 = lon + 10000000;
    maxlat0 = lat + 10000000;

    minlon = lon - 1000000;
    minlat = lat - 1000000;
    maxlon = lon + 6000000;
    maxlat = lat + 6000000;
  }

  public boolean isInBoundary(OsmNode n, int cost) {
    if (radius > 0) {
      return n.calcDistance(p) < radius;
    }
    if (cost == 0) {
      return n.longitude > minlon0 && n.longitude < maxlon0 && n.latitude > minlat0 && n.latitude < maxlat0;
    }
    return n.longitude > minlon && n.longitude < maxlon && n.latitude > minlat && n.latitude < maxlat;
  }

  public int getBoundaryDistance(OsmNode n) {
    switch (direction) {
      case 0:
        return n.calcDistance(new OsmNode(n.longitude, minlat));
      case 1:
        return n.calcDistance(new OsmNode(minlon, n.latitude));
      case 2:
        return n.calcDistance(new OsmNode(n.longitude, maxlat));
      case 3:
        return n.calcDistance(new OsmNode(maxlon, n.latitude));
      default:
        throw new IllegalArgumentException("undefined direction: " + direction);
    }
  }

}
