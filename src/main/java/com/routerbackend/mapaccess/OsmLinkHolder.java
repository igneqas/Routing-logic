/**
 * Container for routig configs
 *
 * @author ab
 */
package com.routerbackend.mapaccess;

public interface OsmLinkHolder {
  void setNextForLink(OsmLinkHolder holder);

  OsmLinkHolder getNextForLink();
}
