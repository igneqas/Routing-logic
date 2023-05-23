/**
 * Container for an osm node
 *
 * @author ab
 */
package com.routerbackend.routinglogic.core;

import com.routerbackend.routinglogic.mapaccess.OsmNodesMap;
import com.routerbackend.routinglogic.mapaccess.TurnRestriction;
import com.routerbackend.routinglogic.utils.CheapRuler;

public class OsmNode extends OsmLink implements OsmPos
{
  public int latitude;
  public int longitude;
  public short elevation = Short.MIN_VALUE;
  public byte[] nodeDescription;
  public TurnRestriction firstRestriction;
  public int visitID;

  public void addTurnRestriction(TurnRestriction tr) {
    tr.next = firstRestriction;
    firstRestriction = tr;
  }

  public OsmLink firstlink;

  public OsmNode() {
  }

  public OsmNode(int longitude, int latitude) {
    this.longitude = longitude;
    this.latitude = latitude;
  }

  // interface OsmPos
  public final int getILat() {
    return latitude;
  }

  public final int getILon() {
    return longitude;
  }

  public final short getSElev() {
    return elevation;
  }

  public final double getElev() {
    return elevation / 4.;
  }

  public final void addLink(OsmLink link, boolean isReverse, OsmNode tn) {
    if (link == firstlink) {
      throw new IllegalArgumentException("UUUUPS");
    }

    if (isReverse) {
      link.n1 = tn;
      link.n2 = this;
      link.next = tn.firstlink;
      link.previous = firstlink;
    } else {
      link.n1 = this;
      link.n2 = tn;
      link.next = firstlink;
      link.previous = tn.firstlink;
    }

    tn.firstlink = link;
    firstlink = link;
  }

  public final int calculateDistance(OsmPos p) {
    return (int) Math.max(1.0, Math.round(CheapRuler.distance(longitude, latitude, p.getILon(), p.getILat())));
  }

  public String toString() {
    return "n_" + (longitude - 180000000) + "_" + (latitude - 90000000);
  }

  public void addLink(int linklon, int linklat, byte[] description, byte[] geometry, OsmNodesMap hollowNodes, boolean isReverse) {
    if (linklon == longitude && linklat == latitude) {
      return; // skip self-ref
    }

    OsmNode tn = null; // find the target node
    OsmLink link = null;

    // ...in our known links
    for (OsmLink l = firstlink; l != null; l = l.getNext(this)) {
      OsmNode t = l.getTarget(this);
      if (t.longitude == linklon && t.latitude == linklat) {
        tn = t;
        if (isReverse || (l.descriptionBitmap == null && !l.isReverse(this))) {
          link = l; // the correct one that needs our data
          break;
        }
      }
    }
    if (tn == null) // .. not found, then check the hollow nodes
    {
      tn = hollowNodes.get(linklon, linklat); // target node
      if (tn == null) // node not yet known, create a new hollow proxy
      {
        tn = new OsmNode(linklon, linklat);
        tn.setHollow();
        hollowNodes.put(tn);
        addLink(link = tn, isReverse, tn); // technical inheritance: link instance in node
      }
    }
    if (link == null) {
      addLink(link = new OsmLink(), isReverse, tn);
    }
    if (!isReverse) {
      link.descriptionBitmap = description;
      link.geometry = geometry;
    }
  }

  public final int calcDistance(OsmPos p) {
    return (int) Math.max(1.0, Math.round(CheapRuler.distance(longitude, latitude, p.getILon(), p.getILat())));
  }


  public final boolean isHollow() {
    return elevation == -12345;
  }

  public final void setHollow() {
    elevation = -12345;
  }

  public final long getIdFromPos() {
    return ((long) longitude) << 32 | latitude;
  }

  public void vanish() {
    if (!isHollow()) {
      OsmLink l = firstlink;
      while (l != null) {
        OsmNode target = l.getTarget(this);
        OsmLink nextLink = l.getNext(this);
        if (!target.isHollow()) {
          unlinkLink(l);
          if (!l.isLinkUnused()) {
            target.unlinkLink(l);
          }
        }
        l = nextLink;
      }
    }
  }

  public final void unlinkLink(OsmLink link) {
    OsmLink n = link.clear(this);

    if (link == firstlink) {
      firstlink = n;
      return;
    }
    OsmLink l = firstlink;
    while (l != null) {
      if (l.n1 != this && l.n1 != null) // isReverse inline
      {
        OsmLink nl = l.previous;
        if (nl == link) {
          l.previous = n;
          return;
        }
        l = nl;
      } else if (l.n2 != this && l.n2 != null) {
        OsmLink nl = l.next;
        if (nl == link) {
          l.next = n;
          return;
        }
        l = nl;
      } else {
        throw new IllegalArgumentException("unlinkLink: unknown source");
      }
    }
  }


  @Override
  public final boolean equals(Object o) {
    return ((OsmNode) o).longitude == longitude && ((OsmNode) o).latitude == latitude;
  }

  @Override
  public final int hashCode() {
    return longitude + latitude;
  }
}
