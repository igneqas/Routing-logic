/**
 * Container for link between two Osm nodes
 *
 * @author ab
 */
package com.routerbackend.core;

import com.routerbackend.expressions.BExpressionContext;
import com.routerbackend.expressions.BExpressionContextNode;
import com.routerbackend.expressions.BExpressionContextWay;

import java.util.Map;


final class StdModel extends OsmPathModel {
  public OsmPrePath createPrePath() {
    return null;
  }

  public OsmPath createPath() {
    return new StdPath();
  }

  protected BExpressionContextWay ctxWay;
  protected BExpressionContextNode ctxNode;


  @Override
  public void init(BExpressionContextWay expctxWay, BExpressionContextNode expctxNode, Map<String, String> keyValues) {
    ctxWay = expctxWay;
    ctxNode = expctxNode;

    BExpressionContext expctxGlobal = expctxWay; // just one of them...

  }
}
