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

import java.io.*;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
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

  private VoiceHintList voiceHints;

  public String message = null;
  public List<String> messageList = null;

  public String name = "unset";

  protected List<MatchedWaypoint> matchedWaypoints;
  public boolean exportWaypoints = false;

  public void addNode(OsmPathElement node) {
    nodes.add(0, node);
  }

  public void registerDetourForId(long id, OsmPathElement detour) {
    if (detourMap == null) {
      detourMap = new CompactLongMap<OsmPathElementHolder>();
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
      detourMap = new FrozenLongMap<OsmPathElementHolder>(tmpDetourMap);
    }
  }

  OsmPathElement lastorigin = null;

  public void appendDetours(OsmTrack source) {
    if (detourMap == null) {
      detourMap = source.detourMap == null ? null : new CompactLongMap<OsmPathElementHolder>();
    }
    if (source.detourMap != null) {
      int pos = nodes.size() - source.nodes.size() + 1;
      OsmPathElement origin = null;
      if (pos > 0)
        origin = nodes.get(pos);
      for (OsmPathElement node : source.nodes) {
        long id = node.getIdFromPos();
        OsmPathElementHolder nh = new OsmPathElementHolder();
        if (node.origin == null && lastorigin != null)
          node.origin = lastorigin;
        nh.node = node;
        lastorigin = node;
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
    }
  }

  public void buildMap() {
    nodesMap = new CompactLongMap<OsmPathElementHolder>();
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
    nodesMap = new FrozenLongMap<OsmPathElementHolder>(nodesMap);
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
    ArrayList<String> res = new ArrayList<String>();
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

  public static OsmTrack readBinary(String filename, OsmNodeNamed newEp, long[] nogoChecksums, long profileChecksum, StringBuilder debugInfo) {
    OsmTrack t = null;
    if (filename != null) {
      File f = new File(filename);
      if (f.exists()) {
        try {
          DataInputStream dis = new DataInputStream(new BufferedInputStream(new FileInputStream(f)));
          MatchedWaypoint ep = MatchedWaypoint.readFromStream(dis);
          int dlon = ep.waypoint.longitude - newEp.longitude;
          int dlat = ep.waypoint.latitude - newEp.latitude;
          boolean targetMatch = dlon < 20 && dlon > -20 && dlat < 20 && dlat > -20;
          if (debugInfo != null) {
            debugInfo.append("target-delta = " + dlon + "/" + dlat + " targetMatch=" + targetMatch);
          }
          if (targetMatch) {
            t = new OsmTrack();
            t.endPoint = ep;
            int n = dis.readInt();
            OsmPathElement last_pe = null;
            for (int i = 0; i < n; i++) {
              OsmPathElement pe = OsmPathElement.readFromStream(dis);
              pe.origin = last_pe;
              last_pe = pe;
              t.nodes.add(pe);
            }
            t.cost = last_pe.cost;
            t.buildMap();

            // check cheecksums, too
            long[] al = new long[3];
            long pchecksum = 0;
            try {
              al[0] = dis.readLong();
              al[1] = dis.readLong();
              al[2] = dis.readLong();
            } catch (EOFException eof) { /* kind of expected */ }
            try {
              t.isDirty = dis.readBoolean();
            } catch (EOFException eof) { /* kind of expected */ }
            try {
              pchecksum = dis.readLong();
            } catch (EOFException eof) { /* kind of expected */ }
            boolean nogoCheckOk = Math.abs(al[0] - nogoChecksums[0]) <= 20
              && Math.abs(al[1] - nogoChecksums[1]) <= 20
              && Math.abs(al[2] - nogoChecksums[2]) <= 20;
            boolean profileCheckOk = pchecksum == profileChecksum;

            if (debugInfo != null) {
              debugInfo.append(" nogoCheckOk=" + nogoCheckOk + " profileCheckOk=" + profileCheckOk);
              debugInfo.append(" al=" + formatLongs(al) + " nogoChecksums=" + formatLongs(nogoChecksums));
            }
            if (!(nogoCheckOk && profileCheckOk)) return null;
          }
          dis.close();
        } catch (Exception e) {
          throw new RuntimeException("Exception reading rawTrack: " + e);
        }
      }
    }
    return t;
  }

  private static String formatLongs(long[] al) {
    StringBuilder sb = new StringBuilder();
    sb.append('{');
    for (long l : al) {
      sb.append(l);
      sb.append(' ');
    }
    sb.append('}');
    return sb.toString();
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

    if (t.voiceHints != null) {
      if (ourSize > 0) {
        for (VoiceHint hint : t.voiceHints.list) {
          hint.indexInTrack = hint.indexInTrack + ourSize - 1;
        }
      }
      if (voiceHints == null) {
        voiceHints = t.voiceHints;
      } else {
        voiceHints.list.addAll(t.voiceHints.list);
      }
    } else {
      if (detourMap == null) {
        //copyDetours( t );
        detourMap = t.detourMap;
      } else {
        addDetours(t);
      }
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
    int turnInstructionMode = voiceHints != null ? voiceHints.turnInstructionMode : 0;

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
    if (voiceHints != null && !voiceHints.list.isEmpty()) {
      sb.append("        \"voicehints\": [\n");
      for (VoiceHint hint : voiceHints.list) {
        sb.append("          [");
        sb.append(hint.indexInTrack);
        sb.append(',').append(hint.getCommand());
        sb.append(',').append(hint.getExitNumber());
        sb.append(',').append(hint.distanceToNext);
        sb.append(',').append((int) hint.angle);

        // not always include geometry because longer and only needed for comment style
        if (turnInstructionMode == 4) // comment style
        {
          sb.append(",\"").append(hint.formatGeometry()).append("\"");
        }

        sb.append("],\n");
      }
      sb.deleteCharAt(sb.lastIndexOf(","));
      sb.append("        ],\n");
    }
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

  private MatchedWaypoint getMatchedWaypoint(int idx) {
    if (matchedWaypoints == null) return null;
    for (MatchedWaypoint wp : matchedWaypoints) {
      if (idx == wp.indexInTrack) {
        return wp;
      }
    }
    return null;
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

  public String getFormattedTime2() {
    int seconds = (int) (getTotalSeconds() + 0.5);
    int hours = seconds / 3600;
    int minutes = (seconds - hours * 3600) / 60;
    seconds = seconds - hours * 3600 - minutes * 60;
    String time = "";
    if (hours != 0)
      time = "" + hours + "h ";
    if (minutes != 0)
      time = time + minutes + "m ";
    if (seconds != 0)
      time = time + seconds + "s";
    return time;
  }

  SimpleDateFormat TIMESTAMP_FORMAT;

  public String getFormattedEnergy() {
    return format1(energy / 3600000.) + "kwh";
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

  private String format1(double n) {
    String s = "" + (long) (n * 10 + 0.5);
    int len = s.length();
    return s.substring(0, len - 1) + "." + s.charAt(len - 1);
  }

  public void dumpMessages(String filename, RoutingContext rc) throws Exception {
    BufferedWriter bw = filename == null ? null : new BufferedWriter(new FileWriter(filename));
    writeMessages(bw, rc);
  }

  public void writeMessages(BufferedWriter bw, RoutingContext rc) throws Exception {
    dumpLine(bw, MESSAGES_HEADER);
    for (String m : aggregateMessages()) {
      dumpLine(bw, m);
    }
    if (bw != null)
      bw.close();
  }

  private void dumpLine(BufferedWriter bw, String s) throws Exception {
    if (bw == null) {
      System.out.println(s);
    } else {
      bw.write(s);
      bw.write("\n");
    }
  }

  public OsmPathElementHolder getFromDetourMap(long id) {
    if (detourMap == null)
      return null;
    return detourMap.get(id);
  }

  public void prepareSpeedProfile(RoutingContext rc) {
    // sendSpeedProfile = rc.keyValues != null && rc.keyValues.containsKey( "vmax" );
  }

  public void processVoiceHints(RoutingContext rc) {
    voiceHints = new VoiceHintList();
    voiceHints.setTransportMode(rc.carMode, rc.bikeMode);
    voiceHints.turnInstructionMode = rc.turnInstructionMode;

    if (detourMap == null) {
      return;
    }
    int nodeNr = nodes.size() - 1;
    int i = nodeNr;
    OsmPathElement node = nodes.get(nodeNr);
    while (node != null) {
      if (node.origin != null) {
      }
      node = node.origin;
    }

    i = 0;

    node = nodes.get(nodeNr);
    List<VoiceHint> inputs = new ArrayList<VoiceHint>();
    while (node != null) {
      if (node.origin != null) {
        VoiceHint input = new VoiceHint();
        inputs.add(input);
        input.ilat = node.origin.getILat();
        input.ilon = node.origin.getILon();
        input.selev = node.origin.getSElev();
        input.indexInTrack = --nodeNr;
        input.goodWay = node.message;
        input.oldWay = node.origin.message == null ? node.message : node.origin.message;
        if (rc.turnInstructionMode == 8 ||
          rc.turnInstructionMode == 4 ||
          rc.turnInstructionMode == 2 ||
          rc.turnInstructionMode == 9) {
          MatchedWaypoint mwpt = getMatchedWaypoint(nodeNr);
          if (mwpt != null && mwpt.direct) {
            input.cmd = VoiceHint.BL;
            input.angle = (float) (nodeNr == 0 ? node.origin.message.turnangle : node.message.turnangle);
            input.distanceToNext = node.calcDistance(node.origin);
          }
        }
        OsmPathElementHolder detours = detourMap.get(node.origin.getIdFromPos());
        if (nodeNr >= 0 && detours != null) {
          OsmPathElementHolder h = detours;
          while (h != null) {
            OsmPathElement e = h.node;
            input.addBadWay(startSection(e, node.origin));
            h = h.nextHolder;
          }
        } else if (nodeNr == 0 && detours != null) {
          OsmPathElementHolder h = detours;
          OsmPathElement e = h.node;
          input.addBadWay(startSection(e, e));
        }
      }
      node = node.origin;
    }

    VoiceHintProcessor vproc = new VoiceHintProcessor(rc.turnInstructionCatchingRange, rc.turnInstructionRoundabouts);
    List<VoiceHint> results = vproc.process(inputs);

    double minDistance = getMinDistance();
    List<VoiceHint> resultsLast = vproc.postProcess(results, rc.turnInstructionCatchingRange, minDistance);
    for (VoiceHint hint : resultsLast) {
      voiceHints.list.add(hint);
    }

  }

  int getMinDistance() {
    if (voiceHints != null) {
      switch (voiceHints.getTransportMode()) {
        case "car":
          return 20;
        case "bike":
          return 5;
        case "foot":
          return 3;
        default:
          return 5;
      }
    }
    return 2;
  }

  public void removeVoiceHint(int i) {
    if (voiceHints != null) {
      VoiceHint remove = null;
      for (VoiceHint vh : voiceHints.list) {
        if (vh.indexInTrack == i)
          remove = vh;
      }
      if (remove != null)
        voiceHints.list.remove(remove);
    }
  }

  private MessageData startSection(OsmPathElement element, OsmPathElement root) {
    OsmPathElement e = element;
    int cnt = 0;
    while (e != null && e.origin != null) {
      if (e.origin.getILat() == root.getILat() && e.origin.getILon() == root.getILon()) {
        return e.message;
      }
      e = e.origin;
      if (cnt++ == 1000000) {
        throw new IllegalArgumentException("ups: " + root + "->" + element);
      }
    }
    return null;
  }
}
