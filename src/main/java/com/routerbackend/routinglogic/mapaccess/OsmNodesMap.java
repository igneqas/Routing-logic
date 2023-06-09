/**
 * Container for link between two Osm nodes
 *
 * @author ab
 */
package com.routerbackend.routinglogic.mapaccess;

import com.routerbackend.routinglogic.core.OsmLink;
import com.routerbackend.routinglogic.core.OsmNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.routerbackend.Constants.MEMORY_CLASS;

public final class OsmNodesMap {
  private Map<OsmNode, OsmNode> hmap = new HashMap<OsmNode, OsmNode>(4096);
  private OsmNode testKey = new OsmNode();
  public int nodesCreated;
  public long maxMemory = (2L * (MEMORY_CLASS * 1024L * 1024L)) / 3L;
  private long currentmaxmem = 4000000; // start with 4 MB
  public int lastVisitID = 1000;
  public int baseID = 1000;
  public OsmNode destination;
  public int currentPathCost;
  public int currentMaxCost = 1000000000;
  public OsmNode endNode1;
  public OsmNode endNode2;
  public int cleanupMode = 0;

  public void cleanupAndCount(OsmNode[] nodes) {
    if (cleanupMode == 0) {
      justCount(nodes);
    } else {
      cleanupPeninsulas(nodes);
    }
  }

  private void justCount(OsmNode[] nodes) {
    for (OsmNode n : nodes) {
      if (n.firstlink != null) {
        nodesCreated++;
      }
    }
  }

  private void cleanupPeninsulas(OsmNode[] nodes) {
    baseID = lastVisitID++;
    // loop over nodes again just for housekeeping
    for (OsmNode n : nodes) {
      if (n.firstlink != null && n.visitID == 1) {
        try {
          minVisitIdInSubtree(null, n);
        } catch (StackOverflowError ignored) {
        }
      }
    }
  }

  private int minVisitIdInSubtree(OsmNode source, OsmNode n) {
    if (n.visitID == 1) n.visitID = baseID; // border node
    else n.visitID = lastVisitID++;
    int minId = n.visitID;
    nodesCreated++;

    OsmLink nextLink;
    for (OsmLink l = n.firstlink; l != null; l = nextLink) {
      nextLink = l.getNext(n);

      OsmNode t = l.getTarget(n);
      if (t == source) continue;
      if (t.isHollow()) continue;

      int minIdSub = t.visitID;
      if (minIdSub == 1) {
        minIdSub = baseID;
      } else if (minIdSub == 0) {
        int nodesCreatedUntilHere = nodesCreated;
        minIdSub = minVisitIdInSubtree(n, t);
        if (minIdSub > n.visitID) // peninsula ?
        {
          nodesCreated = nodesCreatedUntilHere;
          n.unlinkLink(l);
          t.unlinkLink(l);
        }
      } else if (minIdSub < baseID) {
        continue;
      } else if (cleanupMode == 2) {
        minIdSub = baseID; // in tree-mode, hitting anything is like a gateway
      }
      if (minIdSub < minId) minId = minIdSub;
    }
    return minId;
  }


  public boolean isInMemoryBounds(int npaths, boolean extend) {
    long total = nodesCreated * 95L + npaths * 200L;

    if (extend) {
      total += 100000;

      // when extending, try to have 1 MB  space
      long delta = total + 1900000 - currentmaxmem;
      if (delta > 0) {
        currentmaxmem += delta;
        if (currentmaxmem > maxMemory) {
          currentmaxmem = maxMemory;
        }
      }
    }
    return total <= currentmaxmem;
  }

  private List<OsmNode> nodes2check;

  public boolean canEscape(OsmNode n0) {
    boolean sawLowIDs = false;
    lastVisitID++;
    nodes2check.clear();
    nodes2check.add(n0);
    while (!nodes2check.isEmpty()) {
      OsmNode n = nodes2check.remove(nodes2check.size() - 1);
      if (n.visitID < baseID) {
        n.visitID = lastVisitID;
        nodesCreated++;
        for (OsmLink l = n.firstlink; l != null; l = l.getNext(n)) {
          OsmNode t = l.getTarget(n);
          nodes2check.add(t);
        }
      } else if (n.visitID < lastVisitID) {
        sawLowIDs = true;
      }
    }
    if (sawLowIDs) {
      return true;
    }

    nodes2check.add(n0);
    while (!nodes2check.isEmpty()) {
      OsmNode n = nodes2check.remove(nodes2check.size() - 1);
      if (n.visitID == lastVisitID) {
        nodesCreated--;
        for (OsmLink l = n.firstlink; l != null; l = l.getNext(n)) {
          OsmNode t = l.getTarget(n);
          nodes2check.add(t);
        }
        n.vanish();
      }
    }

    return false;
  }

  private void addActiveNode(List<OsmNode> nodes2check, OsmNode n) {
    n.visitID = lastVisitID;
    nodesCreated++;
    nodes2check.add(n);
  }

  public void clearTemp() {
    nodes2check = null;
  }

  public void collectOutreachers() {
    nodes2check = new ArrayList<>(nodesCreated);
    nodesCreated = 0;
    for (OsmNode n : hmap.values()) {
      addActiveNode(nodes2check, n);
    }

    lastVisitID++;
    baseID = lastVisitID;

    while (!nodes2check.isEmpty()) {
      OsmNode n = nodes2check.remove(nodes2check.size() - 1);
      n.visitID = lastVisitID;

      for (OsmLink l = n.firstlink; l != null; l = l.getNext(n)) {
        OsmNode t = l.getTarget(n);
        if (t.visitID != lastVisitID) {
          addActiveNode(nodes2check, t);
        }
      }
      if (destination != null && currentMaxCost < 1000000000) {
        int distance = n.calculateDistance(destination);
        if (distance > currentMaxCost - currentPathCost + 100) {
          n.vanish();
        }
      }
      if (n.firstlink == null) {
        nodesCreated--;
      }
    }
  }

  public OsmNode get(int ilon, int ilat) {
    testKey.longitude = ilon;
    testKey.latitude = ilat;
    return hmap.get(testKey);
  }

  public void remove(OsmNode node) {
    if (node != endNode1 && node != endNode2) // keep endnodes in hollow-map even when loaded
    {                                           // (needed for escape analysis)
      hmap.remove(node);
    }
  }

  public OsmNode put(OsmNode node) {
    return hmap.put(node, node);
  }
}
