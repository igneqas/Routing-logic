/**
 * Container for a track
 *
 * @author ab
 */
package com.routerbackend.routinglogic.core;

import com.routerbackend.routinglogic.utils.CompactLongMap;
import com.routerbackend.routinglogic.utils.FrozenLongMap;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.*;

public final class OsmTrack {
    public int distance;
    public int ascend;
    public int plainAscend;
    public int cost;
    public int energy;

  // csv-header-line
  private static final String MESSAGES_HEADER = "Longitude\tLatitude\tElevation\tDistance\tCostPerKm\tElevCost\tTurnCost\tNodeCost\tInitialCost\tWayTags\tNodeTags\tTime\tEnergy";

  public static class OsmPathElementHolder {
    public OsmPathElement node;
    public OsmPathElementHolder nextHolder;
  }

  public List<OsmPathElement> nodes = new ArrayList<>();

  private CompactLongMap<OsmPathElementHolder> nodesMap;

  private CompactLongMap<OsmPathElementHolder> detourMap;

  public String name = "unset";

  public void addNode(OsmPathElement node) {
    nodes.add(0, node);
  }

  public void registerDetourForId(long id, OsmPathElement detour) {
    if (detourMap == null) {
      detourMap = new CompactLongMap<>();
    }
    OsmPathElementHolder nh = new OsmPathElementHolder();
    nh.node = detour;
    OsmPathElementHolder h = detourMap.get(id);
    if (h != null) {
      while (h.nextHolder != null) {
        h = h.nextHolder;
      }
      h.nextHolder = nh;
    } else {
      detourMap.fastPut(id, nh);
    }
  }

  public void copyDetours(OsmTrack source) {
    detourMap = source.detourMap == null ? null : new FrozenLongMap<>(source.detourMap);
  }

  public void addDetours(OsmTrack source) {
    if (detourMap != null) {
      CompactLongMap<OsmPathElementHolder> tmpDetourMap = new CompactLongMap<>();
      long[] oldIdList = ((FrozenLongMap<?>) detourMap).getKeyArray();
      for (long id : oldIdList) {
        OsmPathElementHolder v = detourMap.get(id);
        tmpDetourMap.put(id, v);
      }

      if (source.detourMap != null) {
        long[] idList = ((FrozenLongMap<?>) source.detourMap).getKeyArray();
        for (long id : idList) {
          OsmPathElementHolder v = source.detourMap.get(id);
          if (!tmpDetourMap.contains(id) && source.nodesMap.contains(id)) {
            tmpDetourMap.put(id, v);
          }
        }
      }
      detourMap = new FrozenLongMap<>(tmpDetourMap);
    }
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

  private List<String> aggregateMessages() {
    ArrayList<String> res = new ArrayList<>();
    MessageData current = null;
    for (OsmPathElement n : nodes) {
      if (n.message != null && n.message.wayKeyValues != null) {
        MessageData md = n.message.copy();
        if (current != null) {
          if (current.nodeKeyValues != null || !current.wayKeyValues.equals(md.wayKeyValues)) {
            res.add(current.toMessage());
          } else {
            md.add(current);
          }
        }
        current = md;
      }
    }
    if (current != null) {
      res.add(current.toMessage());
    }
    return res;
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

    if (detourMap == null) {
      detourMap = t.detourMap;
    } else {
      addDetours(t);
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
    sb.append("  \"type\": \"FeatureCollection\",\n");
    sb.append("  \"features\": [\n");
    sb.append("    {\n");
    sb.append("      \"type\": \"Feature\",\n");
    sb.append("      \"properties\": {\n");
    sb.append("        \"name\": \"").append(name).append("\",\n");
    sb.append("        \"trackLength\": \"").append(distance).append("\",\n");
    sb.append("        \"filteredAscend\": \"").append(ascend).append("\",\n");
    sb.append("        \"plain-ascend\": \"").append(plainAscend).append("\",\n");
    sb.append("        \"totalTime\": \"").append(getTotalSeconds()).append("\",\n");
    sb.append("        \"total-energy\": \"").append(energy).append("\",\n");
    sb.append("        \"cost\": \"").append(cost).append("\",\n");

    //  ... traditional message list
    {
      sb.append("        \"messages\": [\n");
      sb.append("          [\"").append(MESSAGES_HEADER.replaceAll("\t", "\", \"")).append("\"],\n");
      for (String m : aggregateMessages()) {
        sb.append("          [\"").append(m.replaceAll("\t", "\", \"")).append("\"],\n");
      }
      sb.deleteCharAt(sb.lastIndexOf(","));
      sb.append("        ],\n");
    }

    if (getTotalSeconds() > 0) {
      sb.append("        \"times\": [");
      DecimalFormat decimalFormat = (DecimalFormat) NumberFormat.getInstance(Locale.ENGLISH);
      decimalFormat.applyPattern("0.###");
      for (OsmPathElement n : nodes) {
        sb.append(decimalFormat.format(n.getTime())).append(",");
      }
      sb.deleteCharAt(sb.lastIndexOf(","));
      sb.append("]\n");
    } else {
      sb.deleteCharAt(sb.lastIndexOf(","));
    }

    sb.append("      },\n");
    sb.append("      \"geometry\": {\n");
    sb.append("        \"type\": \"LineString\",\n");
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
