package com.routerbackend.routinglogic.core;

import com.routerbackend.routinglogic.mapaccess.MatchedWaypoint;
import com.routerbackend.routinglogic.mapaccess.NodesCache;
import com.routerbackend.routinglogic.mapaccess.OsmLinkHolder;
import com.routerbackend.routinglogic.mapaccess.OsmNodePairSet;
import com.routerbackend.routinglogic.utils.SortedHeap;

import java.util.*;

import static com.routerbackend.Constants.*;

public class RoutingEngine {
  private NodesCache nodesCache;
  private SortedHeap<OsmPath> openSet = new SortedHeap<>();
  protected List<OsmNodeNamed> waypoints;
  protected List<MatchedWaypoint> matchedWaypoints;

  private int nodeLimit; // used for target island search
  private OsmNodePairSet islandNodePairs = new OsmNodePairSet();
  protected OsmTrack foundTrack = new OsmTrack();

  protected String errorMessage = null;
  protected RoutingContext routingContext;
  public double airDistanceCostFactor;
  private OsmTrack guideTrack;
  private OsmPathElement matchPath;

  private long startTime;
  private long maxRunningTime;

  public RoutingEngine(List<OsmNodeNamed> waypoints, RoutingContext routingContext) {
    this.waypoints = waypoints;
    this.routingContext = routingContext;
    ProfileActions.parseProfile(routingContext);
  }

  public void doRun() {
    try {
      startTime = System.currentTimeMillis();
      int sectionCount = waypoints.size() - 1;
      OsmTrack[] refTracks = new OsmTrack[sectionCount]; // used ways for alternatives
      OsmTrack[] lastTracks = new OsmTrack[sectionCount];
      OsmTrack track;
      for (int i = 0; ; i++) {
        track = findTrack(refTracks, lastTracks);
        track.name = routingContext.getProfileName();
          if (i == routingContext.getAlternativeIdx(0, 3)) {
            foundTrack = track;
            break;
          }
      }
    } catch (IllegalArgumentException e) {
    } catch (Exception e) {
    } catch (Error e) {
    }
  }

  private void postElevationCheck(OsmTrack track) {
    OsmPathElement lastPt = null;
    OsmPathElement startPt = null;
    short lastElev = Short.MIN_VALUE;
    short startElev = Short.MIN_VALUE;
    short endElev = Short.MIN_VALUE;
    int startIdx = 0;
    int endIdx;
    int dist = 0;
    int ourSize = track.nodes.size();
    for (int idx = 0; idx < ourSize; idx++) {
      OsmPathElement n = track.nodes.get(idx);
      if (n.getSElev() == Short.MIN_VALUE && lastElev != Short.MIN_VALUE && idx < ourSize-1) {
        // start one point before entry point to get better elevation results
        if (idx > 1)
          startElev = track.nodes.get(idx - 2).getSElev();
        if (startElev == Short.MIN_VALUE)
          startElev = lastElev;
        startIdx = idx;
        startPt = lastPt;
        dist = 0;
        if (lastPt != null)
          dist += n.calculateDistance(lastPt);
      } else if (n.getSElev() != Short.MIN_VALUE && lastElev == Short.MIN_VALUE && startElev != Short.MIN_VALUE) {
        // end one point behind exit point to get better elevation results
        if (idx + 1 < track.nodes.size())
          endElev = track.nodes.get(idx + 1).getSElev();
        if (endElev == Short.MIN_VALUE)
          endElev = n.getSElev();
        endIdx = idx;
        OsmPathElement tmpPt = track.nodes.get(startIdx > 1 ? startIdx - 2 : startIdx - 1);
        int diffElev = endElev - startElev;
        dist += tmpPt.calculateDistance(startPt);
        dist += n.calculateDistance(lastPt);
        int distRest = dist;
        double incline = diffElev / (dist / 100.);
        String lastMsg = "";
        double tmpincline = 0;
        double startincline = 0;
        double selev = track.nodes.get(startIdx - 2).getSElev();
        boolean hasInclineTags = false;
        for (int i = startIdx - 1; i < endIdx + 1; i++) {
          OsmPathElement tmp = track.nodes.get(i);
          if (tmp.message != null) {
            MessageData md = tmp.message.copy();
            String msg = md.wayKeyValues;
            if (!msg.equals(lastMsg)) {
              boolean revers = msg.contains("reversedirection=yes");
              int pos = msg.indexOf("incline=");
              if (pos != -1) {
                hasInclineTags = true;
                String s = msg.substring(pos + 8);
                pos = s.indexOf(" ");
                if (pos != -1)
                  s = s.substring(0, pos);

                if (s.length() > 0) {
                  try {
                    int ind = s.indexOf("%");
                    if (ind != -1)
                      s = s.substring(0, ind);
                    ind = s.indexOf("°");
                    if (ind != -1)
                      s = s.substring(0, ind);
                    tmpincline = Double.parseDouble(s.trim());
                    if (revers)
                      tmpincline *= -1;
                  } catch (NumberFormatException e) {
                    tmpincline = 0;
                  }
                }
              } else {
                tmpincline = 0;
              }
              if (startincline == 0) {
                startincline = tmpincline;
              } else if (startincline < 0 && tmpincline > 0) {
                // for the way up find the exit point
                double diff = endElev - selev;
                tmpincline = diff / (distRest / 100.);
              }
            }
            lastMsg = msg;
          }
          int tmpdist = tmp.calculateDistance(tmpPt);
          distRest -= tmpdist;
          if (hasInclineTags)
            incline = tmpincline;
          selev = (selev + (tmpdist / 100. * incline));
          tmp.setSElev((short) selev);
          tmpPt = tmp;
        }
        dist = 0;
      } else if (n.getSElev() != Short.MIN_VALUE && lastElev == Short.MIN_VALUE && startIdx == 0) {
        // fill at start
        for (int i = 0; i < idx; i++) {
          track.nodes.get(i).setSElev(n.getSElev());
        }
      } else if (n.getSElev() == Short.MIN_VALUE && idx == track.nodes.size() - 1) {
        // fill at end
        for (int i = startIdx; i < track.nodes.size(); i++) {
          track.nodes.get(i).setSElev(startElev);
        }
      } else if (n.getSElev() == Short.MIN_VALUE && lastPt != null) {
          dist += n.calculateDistance(lastPt);
      }
      lastElev = n.getSElev();
      lastPt = n;
    }

  }

  private OsmTrack findTrack(OsmTrack[] refTracks, OsmTrack[] lastTracks) {
    for (; ; ) {
      try {
        return tryFindTrack(refTracks, lastTracks);
      } catch (RoutingIslandException rie) {
        islandNodePairs.freezeTempPairs();
        nodesCache.clean(true);
        matchedWaypoints = null;
      }
    }
  }

  private OsmTrack tryFindTrack(OsmTrack[] refTracks, OsmTrack[] lastTracks) {
    OsmTrack fullTrack = new OsmTrack();
    if (matchedWaypoints == null) { // could exist from the previous alternative level
      matchedWaypoints = new ArrayList<>();
      for (OsmNodeNamed waypoint : waypoints) {
        MatchedWaypoint matchedWaypoint = new MatchedWaypoint(waypoint, waypoint.name);
        matchedWaypoints.add(matchedWaypoint);
      }

      matchWaypointsToNodes(matchedWaypoints);
      routingContext.checkMatchedWaypointAgainstNoGos(matchedWaypoints);

      // detect target islands: restricted search in inverse direction
      routingContext.inverseDirection = true;
      airDistanceCostFactor = 0.;
      nodeLimit = MAXNODES_ISLAND_CHECK;
      for (int i = 0; i < matchedWaypoints.size() - 1; i++) {
        OsmTrack segment = findTrack("target-island-check", matchedWaypoints.get(i + 1), matchedWaypoints.get(i), null, null);
        if (segment == null && nodeLimit > 0) {
          throw new IllegalArgumentException("target island detected for section " + i);
        }
      }
      routingContext.inverseDirection = false;
      nodeLimit = 0;
    }

    for (int i = 0; i < matchedWaypoints.size() - 1; i++) {
      if (lastTracks[i] != null) {
        if (refTracks[i] == null) refTracks[i] = new OsmTrack();
        refTracks[i].addNodes(lastTracks[i]);
      }

      OsmTrack segment = searchTrack(matchedWaypoints.get(i), matchedWaypoints.get(i + 1), refTracks[i]);
      fullTrack.appendTrack(segment);
      lastTracks[i] = segment;
    }

    postElevationCheck(fullTrack);
    recalculateTrack(fullTrack);

    return fullTrack;
  }

  private void recalculateTrack(OsmTrack t) {
    int i;
    int totalDistance = 0;
    float lastTime = 0;
    float speed_min = 9999;
    Map<Integer, Integer> directMap = new HashMap<>();
    float tmptime = 1;
    float speed = 1;
    int dist;

    double ascend = 0;
    double ehb = 0.;
    int ourSize = t.nodes.size();

    short ele_start = Short.MIN_VALUE;
    short ele_end = Short.MIN_VALUE;
    double eleFactor = -0.25;

    for (i = 0; i < ourSize; i++) {
      OsmPathElement n = t.nodes.get(i);
      if (n.message == null) n.message = new MessageData();
      OsmPathElement nLast = null;
      if (i == 0) {
        dist = 0;
      } else if (i == 1) {
        nLast = t.nodes.get(0);
        dist = nLast.calculateDistance(n);
      } else {
        nLast = t.nodes.get(i - 1);
        dist = nLast.calculateDistance(n);
      }

      totalDistance += dist;
      tmptime = (n.getTime() - lastTime);
      if (dist > 0) {
        speed = dist / tmptime * 3.6f;
        speed_min = Math.min(speed_min, speed);
      }
      if (tmptime == 1.f) { // no time used here
        directMap.put(i, dist);
      }

      lastTime = n.getTime();

      short ele = n.getSElev();
      if (ele != Short.MIN_VALUE)
        ele_end = ele;
      if (ele_start == Short.MIN_VALUE)
        ele_start = ele;

      if (nLast != null) {
        short ele_last = nLast.getSElev();
        if (ele_last != Short.MIN_VALUE) {
          ehb = ehb + (ele_last - ele) * eleFactor;
        }
        if (ehb > 0) {
          ascend += ehb;
          ehb = 0;
        } else if (ehb < -10) {
          ehb = -10;
        }
      }

    }

    t.ascend = (int) ascend;
    t.plainAscend = (int) ((ele_start - ele_end) * eleFactor + 0.5);
    t.distance = totalDistance;
    SortedSet<Integer> keys = new TreeSet<>(directMap.keySet());
    for (Integer key : keys) {
      int value = directMap.get(key);
      float addTime = (value / (speed_min / 3.6f));

      double addEnergy = 0;
      if (key < ourSize - 1) {
        double incline = (t.nodes.get(key).getElev() - t.nodes.get(key + 1).getElev()) / value;
        double f_roll = routingContext.totalMass * GRAVITY * (ROAD_RESISTANCE + incline);
        double spd = speed_min / 3.6;
        addEnergy = value * (routingContext.S_C_x * spd * spd + f_roll);
      }
      for (int j = key; j < ourSize; j++) {
        OsmPathElement n = t.nodes.get(j);
        n.setTime(n.getTime() + addTime);
        n.setEnergy(n.getEnergy() + (float) addEnergy);
      }
    }
  }

  // geometric position matching finding the nearest routable way-section
  private void matchWaypointsToNodes(List<MatchedWaypoint> matchedWaypoints) {
    resetCache(false);
    nodesCache.matchWaypointsToNodes(matchedWaypoints, islandNodePairs);
  }

  private OsmTrack searchTrack(MatchedWaypoint startWp, MatchedWaypoint endWp, OsmTrack refTrack) {
    return searchRoutedTrack(startWp, endWp, refTrack);
  }

  private OsmTrack searchRoutedTrack(MatchedWaypoint startWp, MatchedWaypoint endWp, OsmTrack refTrack) {
    OsmTrack track = null;
    double[] airDistanceCostFactors = new double[]{
      routingContext.pass1coefficient,
      routingContext.pass2coefficient
    };

    for (int cfi = 0; cfi < airDistanceCostFactors.length; cfi++) {
      airDistanceCostFactor = airDistanceCostFactors[cfi];
      if (airDistanceCostFactor < 0.) {
        continue;
      }

      OsmTrack t;
      t = findTrack(cfi == 0 ? "pass0" : "pass1", startWp, endWp, track, refTrack);
      if (t == null && track != null && matchPath != null) {
          // ups, didn't find it, use a merge
          t = mergeTrack(matchPath, track);
        }
        if (t != null) {
          track = t;
        } else {
          throw new IllegalArgumentException("no track found at pass=" + cfi);
        }
      }

    if (track == null) throw new IllegalArgumentException("no track found");

    // final run for verbose log info and detail nodes
    airDistanceCostFactor = 0.;
    guideTrack = track;
    startTime = System.currentTimeMillis(); // reset timeout...
    try {
      OsmTrack tt = findTrack("re-tracking", startWp, endWp, null, refTrack);
      if (tt == null) throw new IllegalArgumentException("error re-tracking track");
      return tt;
    } finally {
      guideTrack = null;
    }
  }

  private void resetCache(boolean detailed) {
    nodesCache = new NodesCache(routingContext.expressionContextWay, nodesCache, detailed);
    islandNodePairs.clearTempPairs();
  }

  private OsmPath getStartPath(OsmNode n1, OsmNode n2, MatchedWaypoint mwp, OsmNodeNamed endPos, boolean sameSegmentSearch) {
    if (endPos != null) {
      endPos.radius = 1.5;
    }
    OsmPath p = getStartPath(n1, n2, new OsmNodeNamed(mwp.crosspoint), endPos, sameSegmentSearch);

    // special case: start+end on same segment
    if (p.cost >= 0 && sameSegmentSearch && endPos != null && endPos.radius < 1.5) {
      p.treedepth = 0; // hack: mark for the final-check
    }
    return p;
  }


  private OsmPath getStartPath(OsmNode n1, OsmNode n2, OsmNodeNamed wp, OsmNodeNamed endPos, boolean sameSegmentSearch) {
    try {
      routingContext.setWaypoint(wp, sameSegmentSearch ? endPos : null, false);
      OsmPath bestPath = null;
      OsmLink bestLink = null;
      OsmLink startLink = new OsmLink(null, n1);
      OsmPath startPath = routingContext.createPath(startLink);
      startLink.addLinkHolder(startPath, null);
      double minradius = 1e10;
      for (OsmLink link = n1.firstlink; link != null; link = link.getNext(n1)) {
        OsmNode nextNode = link.getTarget(n1);
        if (nextNode.isHollow())
          continue; // border node?
        if (nextNode.firstlink == null)
          continue; // don't care about dead ends
        if (nextNode == n1)
          continue; // ?
        if (nextNode != n2)
          continue; // just that link

        wp.radius = 1.5;
        OsmPath testPath = routingContext.createPath(startPath, link, null, guideTrack != null);
        testPath.airdistance = endPos == null ? 0 : nextNode.calculateDistance(endPos);
        if (wp.radius < minradius) {
          bestPath = testPath;
          minradius = wp.radius;
          bestLink = link;
        }
      }
      if (bestLink != null) {
        bestLink.addLinkHolder(bestPath, n1);
      }
      bestPath.treedepth = 1;

      return bestPath;
    } finally {
      routingContext.unsetWaypoint();
    }
  }

  private OsmTrack findTrack(String operationName, MatchedWaypoint startWp, MatchedWaypoint endWp, OsmTrack costCuttingTrack, OsmTrack refTrack) {
    try {
      boolean detailed = guideTrack != null;
      resetCache(detailed);
      nodesCache.nodesMap.cleanupMode = detailed ? 0 : (routingContext.considerTurnRestrictions ? 2 : 1);
      return _findTrack(operationName, startWp, endWp, costCuttingTrack, refTrack);
    } finally {
      nodesCache.clean(false); // clean only non-virgin caches
    }
  }


  private OsmTrack _findTrack(String operationName, MatchedWaypoint startWp, MatchedWaypoint endWp, OsmTrack costCuttingTrack, OsmTrack refTrack) {
    int maxTotalCost = guideTrack != null ? guideTrack.cost + 5000 : 1000000000;
    int firstMatchCost = 1000000000;

    matchPath = null;
    int nodesVisited = 0;

    long startNodeId1 = startWp.node1.getIdFromPos();
    long startNodeId2 = startWp.node2.getIdFromPos();
    long endNodeId1 = endWp == null ? -1L : endWp.node1.getIdFromPos();
    long endNodeId2 = endWp == null ? -1L : endWp.node2.getIdFromPos();
    OsmNode end1;
    OsmNode end2;
    OsmNodeNamed endPos = null;

    boolean sameSegmentSearch = false;
    OsmNode start1 = nodesCache.getGraphNode(startWp.node1);
    OsmNode start2 = nodesCache.getGraphNode(startWp.node2);
    if (endWp != null) {
      end1 = nodesCache.getGraphNode(endWp.node1);
      end2 = nodesCache.getGraphNode(endWp.node2);
      nodesCache.nodesMap.endNode1 = end1;
      nodesCache.nodesMap.endNode2 = end2;
      endPos = new OsmNodeNamed(endWp.crosspoint);
      sameSegmentSearch = (start1 == end1 && start2 == end2) || (start1 == end2 && start2 == end1);
    }
    if (!nodesCache.obtainNonHollowNode(start1)) {
      return null;
    }
    nodesCache.expandHollowLinkTargets(start1);
    if (!nodesCache.obtainNonHollowNode(start2)) {
      return null;
    }

    nodesCache.expandHollowLinkTargets(start2);
    routingContext.startDirectionValid = false;

    OsmPath startPath1 = getStartPath(start1, start2, startWp, endPos, sameSegmentSearch);
    OsmPath startPath2 = getStartPath(start2, start1, startWp, endPos, sameSegmentSearch);

    // check for an INITIAL match with the cost-cutting-track
    if (costCuttingTrack != null) {
      OsmPathElement pe1 = costCuttingTrack.getLink(startNodeId1, startNodeId2);
      if (pe1 != null) {
        int c = startPath1.cost - pe1.cost;
        if (c < 0) c = 0;
        if (c < firstMatchCost) firstMatchCost = c;
      }

      OsmPathElement pe2 = costCuttingTrack.getLink(startNodeId2, startNodeId1);
      if (pe2 != null) {
        int c = startPath2.cost - pe2.cost;
        if (c < 0) c = 0;
        if (c < firstMatchCost) firstMatchCost = c;
      }
    }

    synchronized (openSet) {
      openSet.clear();
      addToOpenset(startPath1);
      addToOpenset(startPath2);
    }
    ArrayList<OsmPath> openBorderList = new ArrayList<>(4096);
    boolean memoryPanicMode = false;
    boolean needNonPanicProcessing = false;

    for (; ; ) {
      if (maxRunningTime > 0) {
        long timeout = maxRunningTime;
        if (System.currentTimeMillis() - startTime > timeout) {
          throw new IllegalArgumentException(operationName + " timeout after " + (timeout / 1000) + " seconds");
        }
      }

      synchronized (openSet) {

        OsmPath path = openSet.popLowestKeyValue();
        if (path == null) {
          if (openBorderList.isEmpty()) {
            break;
          }
          for (OsmPath p : openBorderList) {
            openSet.add(p.cost + (int) (p.airdistance * airDistanceCostFactor), p);
          }
          openBorderList.clear();
          memoryPanicMode = false;
          needNonPanicProcessing = true;
          continue;
        }

        if (path.airdistance == -1) {
          continue;
        }

        if (nodesCache.hasHollowLinkTargets(path.getTargetNode())) {
          if (!memoryPanicMode) {
            if (!nodesCache.nodesMap.isInMemoryBounds(openSet.getSize(), false)) {
              nodesCache.nodesMap.collectOutreachers();
              for (; ; ) {
                OsmPath p3 = openSet.popLowestKeyValue();
                if (p3 == null) break;
                if (p3.airdistance != -1 && nodesCache.nodesMap.canEscape(p3.getTargetNode())) {
                  openBorderList.add(p3);
                }
              }
              nodesCache.nodesMap.clearTemp();
              for (OsmPath p : openBorderList) {
                openSet.add(p.cost + (int) (p.airdistance * airDistanceCostFactor), p);
              }
              openBorderList.clear();
              if (!nodesCache.nodesMap.isInMemoryBounds(openSet.getSize(), true)) {
                if (maxTotalCost < 1000000000 || needNonPanicProcessing) {
                  throw new IllegalArgumentException("memory limit reached");
                }
                memoryPanicMode = true;
              }
            }
          }
          if (memoryPanicMode) {
            openBorderList.add(path);
            continue;
          }
        }
        needNonPanicProcessing = false;
        if (nodeLimit > 0 && --nodeLimit == 0) // check node-limit for target island search
        {
          return null;
        }

        nodesVisited++;
        OsmLink currentLink = path.getLink();
        OsmNode sourceNode = path.getSourceNode();
        OsmNode currentNode = path.getTargetNode();

        if (currentLink.isLinkUnused()) {
          continue;
        }

        long currentNodeId = currentNode.getIdFromPos();
        long sourceNodeId = sourceNode.getIdFromPos();

        if (!path.didEnterDestinationArea()) {
          islandNodePairs.addTempPair(sourceNodeId, currentNodeId);
        }

        if (path.treedepth != 1) {
          if (path.treedepth == 0) // hack: sameSegment Paths marked treedepth=0 to pass above check
          {
            path.treedepth = 1;
          }

          if ((sourceNodeId == endNodeId1 && currentNodeId == endNodeId2)
            || (sourceNodeId == endNodeId2 && currentNodeId == endNodeId1)) {
            // track found, compile
            return compileTrack(path);
          }

          // check for a match with the cost-cutting-track
          if (costCuttingTrack != null) {
            OsmPathElement pe = costCuttingTrack.getLink(sourceNodeId, currentNodeId);
            if (pe != null) {
              // remember first match cost for fast termination of partial recalcs
              int parentcost = path.originElement == null ? 0 : path.originElement.cost;

              // hitting start-element of costCuttingTrack?
              int c = path.cost - parentcost - pe.cost;
              if (c > 0) parentcost += c;

              if (parentcost < firstMatchCost) firstMatchCost = parentcost;

              int costEstimate = path.cost
                + path.elevationCorrection(routingContext)
                + (costCuttingTrack.cost - pe.cost);
              if (costEstimate <= maxTotalCost) {
                matchPath = OsmPathElement.create(path);
              }
              if (costEstimate < maxTotalCost) {
                maxTotalCost = costEstimate;
              }
            }
          }
        }

        int keepPathAirdistance = path.airdistance;
        OsmLinkHolder firstLinkHolder = currentLink.getFirstLinkHolder(sourceNode);
        for (OsmLinkHolder linkHolder = firstLinkHolder; linkHolder != null; linkHolder = linkHolder.getNextForLink()) {
          ((OsmPath) linkHolder).airdistance = -1; // invalidate the entry in the open set;
        }

        if (path.treedepth > 1) {
          boolean isBidir = currentLink.isBidirectional();
          sourceNode.unlinkLink(currentLink);

          // if the counterlink is alive and does not yet have a path, remove it
          if (isBidir && currentLink.getFirstLinkHolder(currentNode) == null && !routingContext.considerTurnRestrictions) {
            currentNode.unlinkLink(currentLink);
          }
        }

        // recheck cutoff before doing expensive stuff
        if (path.cost + path.airdistance > maxTotalCost + 100) {
          continue;
        }

        nodesCache.nodesMap.currentMaxCost = maxTotalCost;
        nodesCache.nodesMap.currentPathCost = path.cost;
        nodesCache.nodesMap.destination = endPos;

        for (OsmLink link = currentNode.firstlink; link != null; link = link.getNext(currentNode)) {
          OsmNode nextNode = link.getTarget(currentNode);

          if (!nodesCache.obtainNonHollowNode(nextNode)) {
            continue; // border node?
          }
          if (nextNode.firstlink == null) {
            continue; // don't care about dead ends
          }
          if (nextNode == sourceNode) {
            continue; // border node?
          }

          if (guideTrack != null) {
            int gidx = path.treedepth + 1;
            if (gidx >= guideTrack.nodes.size()) {
              continue;
            }
            OsmPathElement guideNode = guideTrack.nodes.get(gidx);
            long nextId = nextNode.getIdFromPos();
            if (nextId != guideNode.getIdFromPos()) {
              // not along the guide-track, discard, but register for voice-hint processing
              if (routingContext.turnInstructionMode > 0) {
                OsmPath detour = routingContext.createPath(path, link, refTrack, true);
                if (detour.cost >= 0. && nextId != startNodeId1 && nextId != startNodeId2) {
                  guideTrack.registerDetourForId(currentNode.getIdFromPos(), OsmPathElement.create(detour));
                }
              }
              continue;
            }
          }

          OsmPath bestPath = null;
          boolean isFinalLink = currentNodeId == endNodeId1 || currentNodeId == endNodeId2;

          for (OsmLinkHolder linkHolder = firstLinkHolder; linkHolder != null; linkHolder = linkHolder.getNextForLink()) {
            OsmPath otherPath = (OsmPath) linkHolder;
            try {
              if (isFinalLink) {
                endPos.radius = 1.5; // 1.5 meters is the upper limit that will not change the unit-test result..
                routingContext.setWaypoint(endPos, true);
              }
              OsmPath testPath = routingContext.createPath(otherPath, link, refTrack, guideTrack != null);
              if (testPath.cost >= 0 && (bestPath == null || testPath.cost < bestPath.cost) &&
                (testPath.sourceNode.getIdFromPos() != testPath.targetNode.getIdFromPos())) {
                bestPath = testPath;
              }
            } finally {
              if (isFinalLink) {
                routingContext.unsetWaypoint();
              }
            }
          }
          if (bestPath != null) {
            boolean trafficSim = endPos == null;

            bestPath.airdistance = trafficSim ? keepPathAirdistance : (isFinalLink ? 0 : nextNode.calculateDistance(endPos));
            if (isFinalLink || bestPath.cost + bestPath.airdistance <= maxTotalCost + 100) {
              // add only if this may beat an existing path for that link
              OsmLinkHolder dominator = link.getFirstLinkHolder(currentNode);
              while (!trafficSim && dominator != null) {
                OsmPath dp = (OsmPath) dominator;
                if (dp.airdistance != -1 && bestPath.definitlyWorseThan(dp, routingContext)) {
                  break;
                }
                dominator = dominator.getNextForLink();
              }

              if (dominator == null) {
                bestPath.treedepth = path.treedepth + 1;
                link.addLinkHolder(bestPath, currentNode);
                addToOpenset(bestPath);
              }
            }
          }
        }
      }
    }

    if (nodesVisited < MAXNODES_ISLAND_CHECK && islandNodePairs.getFreezeCount() < 5) {
      throw new RoutingIslandException();
    }

    return null;
  }

  private void addToOpenset(OsmPath path) {
    if (path.cost >= 0) {
      openSet.add(path.cost + (int) (path.airdistance * airDistanceCostFactor), path);
    }
  }

  private OsmTrack compileTrack(OsmPath path) {
    OsmPathElement element = OsmPathElement.create(path);

    // for final track, cut endnode
    if (guideTrack != null && element.origin != null) {
      element = element.origin;
    }

    OsmTrack track = new OsmTrack();
    track.cost = path.cost;
    track.energy = (int) path.getTotalEnergy();
    int distance = 0;
    while (element != null) {
      if (guideTrack != null && element.message == null) {
        element.message = new MessageData();
      }
      OsmPathElement nextElement = element.origin;
      // ignore double element
      if (nextElement != null && nextElement.positionEquals(element)) {
        element = nextElement;
        continue;
      }

      track.nodes.add(0, element);
      if (nextElement != null) {
        distance += element.calculateDistance(nextElement);
      }
      element = nextElement;
    }
    track.distance = distance;
    track.buildMap();

    // for final track..
    if (guideTrack != null) {
      track.copyDetours(guideTrack);
    }
    return track;
  }

  private OsmTrack mergeTrack(OsmPathElement match, OsmTrack oldTrack) {
    OsmPathElement element = match;
    OsmTrack track = new OsmTrack();
    track.cost = oldTrack.cost;

    while (element != null) {
      track.addNode(element);
      element = element.origin;
    }
    long lastId = 0;
    long id1 = match.getIdFromPos();
    long id0 = match.origin == null ? 0 : match.origin.getIdFromPos();
    boolean appending = false;
    for (OsmPathElement n : oldTrack.nodes) {
      if (appending) {
        track.nodes.add(n);
      }

      long id = n.getIdFromPos();
      if (id == id1 && lastId == id0) {
        appending = true;
      }
      lastId = id;
    }


    track.buildMap();
    return track;
  }

  public OsmTrack getFoundTrack() {
    return foundTrack;
  }

  public String getErrorMessage() {
    return errorMessage;
  }
}
