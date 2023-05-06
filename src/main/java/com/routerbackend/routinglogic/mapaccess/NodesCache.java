/**
 * Efficient cache or osmnodes
 *
 * @author ab
 */
package com.routerbackend.routinglogic.mapaccess;

import com.routerbackend.routinglogic.codec.MicroCache;
import com.routerbackend.routinglogic.codec.WaypointMatcher;
import com.routerbackend.routinglogic.core.OsmLink;
import com.routerbackend.routinglogic.core.OsmNode;
import com.routerbackend.routinglogic.expressions.BExpressionContextWay;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class NodesCache {
  private File segmentDir = new File("src/main/java/com/data/segments");
  public OsmNodesMap nodesMap;
  private BExpressionContextWay expCtxWay;
  private int lookupVersion;
  private String currentFileName;
  private Map<String, PhysicalFile> fileCache;
  private OsmFile[][] mapFiles;
  public WaypointMatcher waypointMatcher;
  public boolean first_file_access_failed;
  public String first_file_access_name;
  private boolean detailed; // NOPMD used in constructor

  public NodesCache(BExpressionContextWay ctxWay, NodesCache oldCache, boolean detailed) {
    this.nodesMap = new OsmNodesMap();
    this.expCtxWay = ctxWay;
    this.lookupVersion = ctxWay.meta.lookupVersion;
    this.detailed = detailed;

    ctxWay.setDecodeForbidden(detailed);
    first_file_access_failed = false;
    first_file_access_name = null;

    if (!this.segmentDir.isDirectory())
      throw new RuntimeException("segment directory " + segmentDir.getAbsolutePath() + " does not exist");

    if (oldCache != null) {
      fileCache = oldCache.fileCache;

      // re-use old, virgin caches (if same detail-mode)
      if (oldCache.detailed == detailed) {
        mapFiles = oldCache.mapFiles;
        for (OsmFile[] fileRow : mapFiles) {
          if (fileRow == null)
            continue;
          for (OsmFile osmf : fileRow) {
            osmf.setGhostState();
          }
        }
      } else {
        mapFiles = new OsmFile[180][];
      }
    } else {
      fileCache = new HashMap<>(4);
      mapFiles = new OsmFile[180][];
    }
  }

  public void clean(boolean all) {
    for (OsmFile[] mapFile : mapFiles) {
      if (mapFile == null)
        continue;
      for (OsmFile osmFile : mapFile) {
        osmFile.clean(all);
      }
    }
  }

  public void loadSegmentFor(int ilon, int ilat) {
    getSegmentFor(ilon, ilat);
  }

  public MicroCache getSegmentFor(int ilon, int ilat) {
    try {
      int lonDegree = ilon / 1000000;
      int latDegree = ilat / 1000000;
      OsmFile osmf = null;
      OsmFile[] fileRow = mapFiles[latDegree];
      int ndegrees = fileRow == null ? 0 : fileRow.length;
      for (int i = 0; i < ndegrees; i++) {
        if (fileRow[i].lonDegree == lonDegree) {
          osmf = fileRow[i];
          break;
        }
      }
      if (osmf == null) {
        osmf = fileForSegment(lonDegree, latDegree);
        OsmFile[] newFileRow = new OsmFile[ndegrees + 1];
        for (int i = 0; i < ndegrees; i++) {
          newFileRow[i] = fileRow[i];
        }
        newFileRow[ndegrees] = osmf;
        mapFiles[latDegree] = newFileRow;
      }
      currentFileName = osmf.filename;

      if (!osmf.hasData()) {
        return null;
      }

      MicroCache segment = osmf.getMicroCache(ilon, ilat);
      if (segment == null) {
        segment = osmf.createMicroCache(ilon, ilat, expCtxWay, waypointMatcher, nodesMap);
      }
      return segment;
    } catch (IOException re) {
      throw new RuntimeException(re.getMessage());
    } catch (RuntimeException re) {
      throw re;
    } catch (Exception e) {
      throw new RuntimeException("error reading datafile " + currentFileName + ": " + e, e);
    }
  }

  /**
   * make sure the given node is non-hollow,
   * which means it contains not just the id,
   * but also the actual data
   *
   * @return true if successfull, false if node is still hollow
   */
  public boolean obtainNonHollowNode(OsmNode node) {
    if (!node.isHollow())
      return true;

    MicroCache segment = getSegmentFor(node.longitude, node.latitude);
    if (segment == null) {
      return false;
    }
    if (!node.isHollow()) {
      return true; // direct weaving...
    }

    return !node.isHollow();
  }


  /**
   * make sure all link targets of the given node are non-hollow
   */
  public void expandHollowLinkTargets(OsmNode n) {
    for (OsmLink link = n.firstlink; link != null; link = link.getNext(n)) {
      obtainNonHollowNode(link.getTarget(n));
    }
  }

  /**
   * make sure all link targets of the given node are non-hollow
   */
  public boolean hasHollowLinkTargets(OsmNode n) {
    for (OsmLink link = n.firstlink; link != null; link = link.getNext(n)) {
      if (link.getTarget(n).isHollow()) {
        return true;
      }
    }
    return false;
  }

  public OsmNode getGraphNode(OsmNode template) {
    OsmNode graphNode = new OsmNode(template.longitude, template.latitude);
    graphNode.setHollow();
    OsmNode existing = nodesMap.put(graphNode);
    if (existing == null) {
      return graphNode;
    }
    nodesMap.put(existing);
    return existing;
  }

  public void matchWaypointsToNodes(List<MatchedWaypoint> matchedWaypoints, OsmNodePairSet islandNodePairs) {
    waypointMatcher = new WaypointMatcherImpl(matchedWaypoints, islandNodePairs);
    for (MatchedWaypoint mwp : matchedWaypoints) {
      preloadPosition(mwp.waypoint);
    }

    if (first_file_access_failed) {
      throw new IllegalArgumentException("datafile " + first_file_access_name + " not found");
    }

    for (MatchedWaypoint mwp : matchedWaypoints) {
      if (mwp.crosspoint == null) {
        throw new IllegalArgumentException(mwp.name + "-position not mapped in existing datafile");
      }
    }
  }

  private void preloadPosition(OsmNode node) {
    int d = 12500;
    first_file_access_failed = false;
    first_file_access_name = null;
    loadSegmentFor(node.longitude, node.latitude);
    for (int idxLat = -1; idxLat <= 1; idxLat++)
      for (int idxLon = -1; idxLon <= 1; idxLon++) {
        if (idxLon != 0 || idxLat != 0) {
          loadSegmentFor(node.longitude + d * idxLon, node.latitude + d * idxLat);
        }
      }
  }

  private OsmFile fileForSegment(int lonDegree, int latDegree) throws Exception {
    int lonMod5 = lonDegree % 5;
    int latMod5 = latDegree % 5;

    int lon = lonDegree - 180 - lonMod5;
    String slon = lon < 0 ? "W" + (-lon) : "E" + lon;
    int lat = latDegree - 90 - latMod5;

    String slat = lat < 0 ? "S" + (-lat) : "N" + lat;
    String filenameBase = slon + "_" + slat;

    currentFileName = filenameBase + ".rd5";

    PhysicalFile ra = null;
    if (!fileCache.containsKey(filenameBase)) {
      File f = null;
        File primary = new File(segmentDir, filenameBase + ".rd5");
        if (primary.exists()) {
          f = primary;
        }
      if (f != null) {
        currentFileName = f.getName();
        ra = new PhysicalFile(f, lookupVersion);
      }
      fileCache.put(filenameBase, ra);
    }
    ra = fileCache.get(filenameBase);
    OsmFile osmf = new OsmFile(ra, lonDegree, latDegree);

    if (first_file_access_name == null) {
      first_file_access_name = currentFileName;
      first_file_access_failed = osmf.filename == null;
    }

    return osmf;
  }
}
