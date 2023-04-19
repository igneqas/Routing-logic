/**
 * Container for a track
 *
 * @author ab
 */
package com.routerbackend.core;

import com.routerbackend.mapaccess.MatchedWaypoint;
import com.routerbackend.utils.CompactLongMap;
import com.routerbackend.utils.FrozenLongMap;
import com.routerbackend.utils.StringUtils;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.*;

public final class OsmTrack {
    public int distance;
    public int ascend;
    public int plainAscend;
    public int cost;
    public int energy;
  final public static String version = "1.6.3";

  // csv-header-line
  private static final String MESSAGES_HEADER = "Longitude\tLatitude\tElevation\tDistance\tCostPerKm\tElevCost\tTurnCost\tNodeCost\tInitialCost\tWayTags\tNodeTags\tTime\tEnergy";

  public MatchedWaypoint endPoint;
  public long[] nogoChecksums;
  public long profileTimestamp;
  public boolean isDirty;

  public boolean showspeed;
  public boolean showSpeedProfile;
  public boolean showTime;

  public Map<String, String> params;

  public List<OsmNodeNamed> pois = new ArrayList<OsmNodeNamed>();

  public static class OsmPathElementHolder {
    public OsmPathElement node;
    public OsmPathElementHolder nextHolder;
  }

  public List<OsmPathElement> nodes = new ArrayList<>();

  private CompactLongMap<OsmPathElementHolder> nodesMap;

  private CompactLongMap<OsmPathElementHolder> detourMap;

  public String name = "unset";

  protected List<MatchedWaypoint> matchedWaypoints;
  public boolean exportWaypoints = false;

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
    detourMap = source.detourMap == null ? null : new FrozenLongMap<OsmPathElementHolder>(source.detourMap);
  }

  public void addDetours(OsmTrack source) {
    if (detourMap != null) {
      CompactLongMap<OsmPathElementHolder> tmpDetourMap = new CompactLongMap<OsmPathElementHolder>();

      List oldlist = ((FrozenLongMap) detourMap).getValueList();
      long[] oldidlist = ((FrozenLongMap) detourMap).getKeyArray();
      for (int i = 0; i < oldidlist.length; i++) {
        long id = oldidlist[i];
        OsmPathElementHolder v = detourMap.get(id);

        tmpDetourMap.put(id, v);
      }

      if (source.detourMap != null) {
        long[] idlist = ((FrozenLongMap) source.detourMap).getKeyArray();
        for (int i = 0; i < idlist.length; i++) {
          long id = idlist[i];
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

  private List<String> aggregateSpeedProfile() {
    ArrayList<String> res = new ArrayList<>();
    int vmax = -1;
    int vmaxe = -1;
    int vmin = -1;
    int extraTime = 0;
    for (int i = nodes.size() - 1; i > 0; i--) {
      OsmPathElement n = nodes.get(i);
      MessageData m = n.message;
      int vnode = getVNode(i);
      if (m != null && (vmax != m.vmax || vmin != m.vmin || vmaxe != m.vmaxExplicit || vnode < m.vmax || extraTime != m.extraTime)) {
        vmax = m.vmax;
        vmin = m.vmin;
        vmaxe = m.vmaxExplicit;
        extraTime = m.extraTime;
        res.add(i + "," + vmaxe + "," + vmax + "," + vmin + "," + vnode + "," + extraTime);
      }
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
      OsmPathElement olde = nodes.get(ourSize - 1);
      t.nodes.get(1).origin = olde;
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

    showspeed |= t.showspeed;
    showSpeedProfile |= t.showSpeedProfile;
  }

  public List<String> iternity;

  public String formatAsGeoJson() {
    StringBuilder sb = new StringBuilder(8192);

    sb.append("{\n");
    sb.append("  \"type\": \"FeatureCollection\",\n");
    sb.append("  \"features\": [\n");
    sb.append("    {\n");
    sb.append("      \"type\": \"Feature\",\n");
    sb.append("      \"properties\": {\n");
    sb.append("        \"creator\": \"BRouter-" + version + "\",\n");
    sb.append("        \"name\": \"").append(name).append("\",\n");
    sb.append("        \"track-length\": \"").append(distance).append("\",\n");
    sb.append("        \"filtered ascend\": \"").append(ascend).append("\",\n");
    sb.append("        \"plain-ascend\": \"").append(plainAscend).append("\",\n");
    sb.append("        \"total-time\": \"").append(getTotalSeconds()).append("\",\n");
    sb.append("        \"total-energy\": \"").append(energy).append("\",\n");
    sb.append("        \"cost\": \"").append(cost).append("\",\n");

    if (showSpeedProfile) // set in profile
    {
      List<String> sp = aggregateSpeedProfile();
      if (sp.size() > 0) {
        sb.append("        \"speedprofile\": [\n");
        for (int i = sp.size() - 1; i >= 0; i--) {
          sb.append("          [").append(sp.get(i)).append(i > 0 ? "],\n" : "]\n");
        }
        sb.append("        ],\n");
      }
    }
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

    if (iternity != null) {
      sb.append("      \"iternity\": [\n");
      for (String s : iternity) {
        sb.append("        \"").append(s).append("\",\n");
      }
      sb.deleteCharAt(sb.lastIndexOf(","));
      sb.append("        ],\n");
    }
    sb.append("      \"geometry\": {\n");
    sb.append("        \"type\": \"LineString\",\n");
    sb.append("        \"coordinates\": [\n");

    OsmPathElement nn = null;
    for (OsmPathElement n : nodes) {
      String sele = n.getSElev() == Short.MIN_VALUE ? "" : ", " + n.getElev();
      if (showspeed) // hack: show speed instead of elevation
      {
        double speed = 0;
        if (nn != null) {
          int dist = n.calcDistance(nn);
          float dt = n.getTime() - nn.getTime();
          if (dt != 0.f) {
            speed = ((3.6f * dist) / dt + 0.5);
          }
        }
        sele = ", " + (((int) (speed * 10)) / 10.f);
      }
      sb.append("          [").append(formatILon(n.getILon())).append(", ").append(formatILat(n.getILat()))
        .append(sele).append("],\n");
      nn = n;
    }
    sb.deleteCharAt(sb.lastIndexOf(","));

    sb.append("        ]\n");
    sb.append("      }\n");
    if (exportWaypoints || !pois.isEmpty()) {
      sb.append("    },\n");
      for (int i = 0; i <= pois.size() - 1; i++) {
        OsmNodeNamed poi = pois.get(i);
        addFeature(sb, "poi", poi.name, poi.latitude, poi.longitude);
        if (i < matchedWaypoints.size() - 1) {
          sb.append(",");
        }
        sb.append("    \n");
      }
      if (exportWaypoints) {
        for (int i = 0; i <= matchedWaypoints.size() - 1; i++) {
          String type;
          if (i == 0) {
            type = "from";
          } else if (i == matchedWaypoints.size() - 1) {
            type = "to";
          } else {
            type = "via";
          }

          MatchedWaypoint wp = matchedWaypoints.get(i);
          addFeature(sb, type, wp.name, wp.waypoint.latitude, wp.waypoint.longitude);
          if (i < matchedWaypoints.size() - 1) {
            sb.append(",");
          }
          sb.append("    \n");
        }
      }
    } else {
      sb.append("    }\n");
    }
    sb.append("  ]\n");
    sb.append("}\n");

    return sb.toString();
  }

  private void addFeature(StringBuilder sb, String type, String name, int ilat, int ilon) {
    sb.append("    {\n");
    sb.append("      \"type\": \"Feature\",\n");
    sb.append("      \"properties\": {\n");
    sb.append("        \"name\": \"" + StringUtils.escapeJson(name) + "\",\n");
    sb.append("        \"type\": \"" + type + "\"\n");
    sb.append("      },\n");
    sb.append("      \"geometry\": {\n");
    sb.append("        \"type\": \"Point\",\n");
    sb.append("        \"coordinates\": [\n");
    sb.append("          " + formatILon(ilon) + ",\n");
    sb.append("          " + formatILat(ilat) + "\n");
    sb.append("        ]\n");
    sb.append("      }\n");
    sb.append("    }");
  }

  private int getVNode(int i) {
    MessageData m1 = i + 1 < nodes.size() ? nodes.get(i + 1).message : null;
    MessageData m0 = i < nodes.size() ? nodes.get(i).message : null;
    int vnode0 = m1 == null ? 999 : m1.vnode0;
    int vnode1 = m0 == null ? 999 : m0.vnode1;
    return vnode0 < vnode1 ? vnode0 : vnode1;
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

  public OsmPathElementHolder getFromDetourMap(long id) {
    if (detourMap == null)
      return null;
    return detourMap.get(id);
  }
}
