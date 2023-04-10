/**
 * Container for an osm node
 *
 * @author ab
 */
package com.routerbackend.core;

import com.routerbackend.utils.CheapRuler;

public class OsmNodeNamed extends OsmNode {
  public String name;
  public double radius; // radius of nogopoint (in meters)
  public double noGoWeight;  // weight for nogopoint
  public boolean isNoGo = false;
  public boolean direct = false; // mark direct routing

  public OsmNodeNamed() {
  }

  public OsmNodeNamed(OsmNode n) {
    super(n.longitude, n.latitude);
  }

  @Override
  public String toString() {
    if (Double.isNaN(noGoWeight)) {
      return longitude + "," + latitude + "," + name;
    } else {
      return longitude + "," + latitude + "," + name + "," + noGoWeight;
    }
  }

  public double distanceWithinRadius(int lon1, int lat1, int lon2, int lat2, double totalSegmentLength) {
    double[] lonlat2m = CheapRuler.getLonLatToMeterScales((lat1 + lat2) >> 1);

    boolean isFirstPointWithinCircle = CheapRuler.distance(lon1, lat1, longitude, latitude) < radius;
    boolean isLastPointWithinCircle = CheapRuler.distance(lon2, lat2, longitude, latitude) < radius;
    // First point is within the circle
    if (isFirstPointWithinCircle) {
      // Last point is within the circle
      if (isLastPointWithinCircle) {
        return totalSegmentLength;
      }
      // Last point is not within the circle
      // Just swap points and go on with first first point not within the
      // circle now.
      // Swap longitudes
      int tmp = lon2;
      lon2 = lon1;
      lon1 = tmp;
      // Swap latitudes
      tmp = lat2;
      lat2 = lat1;
      lat1 = tmp;
      // Fix boolean values
      isLastPointWithinCircle = isFirstPointWithinCircle;
      isFirstPointWithinCircle = false;
    }
    // Distance between the initial point and projection of center of
    // the circle on the current segment.
    double initialToProject = (
      (lon2 - lon1) * (longitude - lon1) * lonlat2m[0] * lonlat2m[0]
        + (lat2 - lat1) * (latitude - lat1) * lonlat2m[1] * lonlat2m[1]
    ) / totalSegmentLength;
    // Distance between the initial point and the center of the circle.
    double initialToCenter = CheapRuler.distance(longitude, latitude, lon1, lat1);
    // Half length of the segment within the circle
    double halfDistanceWithin = Math.sqrt(
      radius * radius - (
        initialToCenter * initialToCenter -
          initialToProject * initialToProject
      )
    );
    // Last point is within the circle
    if (isLastPointWithinCircle) {
      return halfDistanceWithin + (totalSegmentLength - initialToProject);
    }
    return 2 * halfDistanceWithin;
  }
}
