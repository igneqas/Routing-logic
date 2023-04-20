/**
 * Container for routig configs
 *
 * @author ab
 */
package com.routerbackend.routinglogic.mapaccess;

public interface OsmLinkHolder {
  void setNextForLink(OsmLinkHolder holder);

  OsmLinkHolder getNextForLink();
}
