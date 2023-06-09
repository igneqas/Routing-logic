/**
 * Container for a track
 *
 * @author ab
 */
package com.routerbackend.routinglogic.core;

import com.routerbackend.routinglogic.utils.CompactLongMap;
import com.routerbackend.routinglogic.utils.FrozenLongMap;

import java.util.*;

public class OsmTrack {
    public int distance;
    public int ascend;
    public int plainAscend;
    public int cost;
    public int energy;

  public static class OsmPathElementHolder {
    public OsmPathElement node;
    public OsmPathElementHolder nextHolder;
  }

  public List<OsmPathElement> nodes = new ArrayList<>();

  private CompactLongMap<OsmPathElementHolder> nodesMap;

  public String name = "unset";

  public void addNode(OsmPathElement node) {
    nodes.add(0, node);
  }

  public void buildMap() {
    nodesMap = new CompactLongMap<>();
    for (OsmPathElement node : nodes) {
      long id = node.getIdFromPos();
      OsmPathElementHolder nh = new OsmPathElementHolder();
      nh.node = node;
      OsmPathElementHolder h = nodesMap.get(id);
      if (h != null) {
        while (h.nextHolder != null) {
          h = h.nextHolder;
        }
        h.nextHolder = nh;
      } else {
        nodesMap.fastPut(id, nh);
      }
    }
    nodesMap = new FrozenLongMap<>(nodesMap);
  }

  public void addNodes(OsmTrack t) {
    for (OsmPathElement n : t.nodes)
      addNode(n);
    buildMap();
  }

  public boolean containsNode(OsmPos node) {
    return nodesMap.contains(node.getIdFromPos());
  }

  public OsmPathElement getLink(long n1, long n2) {
    OsmPathElementHolder h = nodesMap.get(n2);
    while (h != null) {
      OsmPathElement e1 = h.node.origin;
      if (e1 != null && e1.getIdFromPos() == n1) {
        return h.node;
      }
      h = h.nextHolder;
    }
    return null;
  }

  public void appendTrack(OsmTrack t) {
    int i = 0;

    int ourSize = nodes.size();
    if (ourSize > 0 && t.nodes.size() > 1) {
      t.nodes.get(1).origin = nodes.get(ourSize - 1);
    }
    float t0 = ourSize > 0 ? nodes.get(ourSize - 1).getTime() : 0;
    float e0 = ourSize > 0 ? nodes.get(ourSize - 1).getEnergy() : 0;
    for (i = 0; i < t.nodes.size(); i++) {
      if (i > 0 || ourSize == 0) {
        OsmPathElement e = t.nodes.get(i);
        e.setTime(e.getTime() + t0);
        e.setEnergy(e.getEnergy() + e0);
        nodes.add(e);
      }
    }

    distance += t.distance;
    ascend += t.ascend;
    plainAscend += t.plainAscend;
    cost += t.cost;
    energy = (int) nodes.get(nodes.size() - 1).getEnergy();
  }

  public String formatAsGeoJson() {
    StringBuilder sb = new StringBuilder(8192);

    sb.append("{\n");
    sb.append("  \"features\": [\n");
    sb.append("    {\n");
    sb.append("      \"properties\": {\n");
    sb.append("        \"name\": \"").append(name).append("\",\n");
    sb.append("        \"trackLength\": \"").append(distance).append("\",\n");
    sb.append("        \"filteredAscend\": \"").append(ascend).append("\",\n");
    sb.append("        \"plain-ascend\": \"").append(plainAscend).append("\",\n");
    sb.append("        \"totalTime\": \"").append(getTotalSeconds()).append("\",\n");
    sb.append("        \"total-energy\": \"").append(energy).append("\",\n");
    sb.append("        \"cost\": \"").append(cost).append("\"\n");
    sb.append("      },\n");
    sb.append("      \"geometry\": {\n");
    sb.append("        \"coordinates\": [\n");

    for (OsmPathElement n : nodes) {
      String sele = n.getSElev() == Short.MIN_VALUE ? "" : ", " + n.getElev();
      sb.append("          [").append(formatILon(n.getILon())).append(", ").append(formatILat(n.getILat()))
        .append(sele).append("],\n");
    }

    sb.deleteCharAt(sb.lastIndexOf(","));
    sb.append("        ]\n");
    sb.append("      }\n");
    sb.append("    }\n");
    sb.append("  ]\n");
    sb.append("}\n");

    return sb.toString();
  }

  private int getTotalSeconds() {
    float s = nodes.size() < 2 ? 0 : nodes.get(nodes.size() - 1).getTime() - nodes.get(0).getTime();
    return (int) (s + 0.5);
  }

  private static String formatILon(int ilon) {
    return formatPos(ilon - 180000000);
  }

  private static String formatILat(int ilat) {
    return formatPos(ilat - 90000000);
  }

  private static String formatPos(int p) {
    boolean negative = p < 0;
    if (negative)
      p = -p;
    char[] ac = new char[12];
    int i = 11;
    while (p != 0 || i > 3) {
      ac[i--] = (char) ('0' + (p % 10));
      p /= 10;
      if (i == 5)
        ac[i--] = '.';
    }
    if (negative)
      ac[i--] = '-';
    return new String(ac, i + 1, 11 - i);
  }
}
