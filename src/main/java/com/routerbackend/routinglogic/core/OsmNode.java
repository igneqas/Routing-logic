/**
 * Container for an osm node
 *
 * @author ab
 */
package com.routerbackend.routinglogic.core;

import com.routerbackend.routinglogic.codec.MicroCache;
import com.routerbackend.routinglogic.codec.MicroCache2;
import com.routerbackend.routinglogic.mapaccess.OsmNodesMap;
import com.routerbackend.routinglogic.mapaccess.TurnRestriction;
import com.routerbackend.routinglogic.utils.CheapRuler;
import com.routerbackend.routinglogic.utils.IByteArrayUnifier;
import com.routerbackend.routinglogic.utils.ByteArrayUnifier;

public class OsmNode extends OsmLink implements OsmPos
{
  /**
   * The latitude
   */
  public int latitude;

  /**
   * The longitude
   */
  public int longitude;

  /**
   * The elevation
   */
  public short selev = Short.MIN_VALUE;

  /**
   * The node-tags, if any
   */
  public byte[] nodeDescription;

  public TurnRestriction firstRestriction;

  public int visitID;

  public void addTurnRestriction(TurnRestriction tr) {
    tr.next = firstRestriction;
    firstRestriction = tr;
  }

  /**
   * The links to other nodes
   */
  public OsmLink firstlink;

  public OsmNode() {
  }

  public OsmNode(int longitude, int latitude) {
    this.longitude = longitude;
    this.latitude = latitude;
  }

  public OsmNode(long id) {
    longitude = (int) (id >> 32);
    latitude = (int) (id & 0xffffffff);
  }


  // interface OsmPos
  public final int getILat() {
    return latitude;
  }

  public final int getILon() {
    return longitude;
  }

  public final short getSElev() {
    return selev;
  }

  public final double getElev() {
    return selev / 4.;
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
      tn.firstlink = link;
      firstlink = link;
    } else {
      link.n1 = this;
      link.n2 = tn;
      link.next = firstlink;
      link.previous = tn.firstlink;
      tn.firstlink = link;
      firstlink = link;
    }
  }

  public final int calcDistance(OsmPos p) {
    return (int) Math.max(1.0, Math.round(CheapRuler.distance(longitude, latitude, p.getILon(), p.getILat())));
  }

  public String toString() {
    return "n_" + (longitude - 180000000) + "_" + (latitude - 90000000);
  }

  public final void parseNodeBody(MicroCache mc, OsmNodesMap hollowNodes, IByteArrayUnifier expCtxWay) {
    if (mc instanceof MicroCache2) {
      parseNodeBody2((MicroCache2) mc, hollowNodes, expCtxWay);
    } else
      throw new IllegalArgumentException("unknown cache version: " + mc.getClass());
  }

  public final void parseNodeBody2(MicroCache2 mc, OsmNodesMap hollowNodes, IByteArrayUnifier expCtxWay) {
    ByteArrayUnifier abUnifier = hollowNodes.getByteArrayUnifier();

    // read turn restrictions
    while (mc.readBoolean()) {
      TurnRestriction tr = new TurnRestriction();
      tr.exceptions = mc.readShort();
      tr.isPositive = mc.readBoolean();
      tr.fromLon = mc.readInt();
      tr.fromLat = mc.readInt();
      tr.toLon = mc.readInt();
      tr.toLat = mc.readInt();
      addTurnRestriction(tr);
    }

    selev = mc.readShort();
    int nodeDescSize = mc.readVarLengthUnsigned();
    nodeDescription = nodeDescSize == 0 ? null : mc.readUnified(nodeDescSize, abUnifier);

    while (mc.hasMoreData()) {
      // read link data
      int endPointer = mc.getEndPointer();
      int linklon = longitude + mc.readVarLengthSigned();
      int linklat = latitude + mc.readVarLengthSigned();
      int sizecode = mc.readVarLengthUnsigned();
      boolean isReverse = (sizecode & 1) != 0;
      byte[] description = null;
      int descSize = sizecode >> 1;
      if (descSize > 0) {
        description = mc.readUnified(descSize, expCtxWay);
      }
      byte[] geometry = mc.readDataUntil(endPointer);

      addLink(linklon, linklat, description, geometry, hollowNodes, isReverse);
    }
    hollowNodes.remove(this);
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


  public final boolean isHollow() {
    return selev == -12345;
  }

  public final void setHollow() {
    selev = -12345;
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
      // if ( l.isReverse( this ) )
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
