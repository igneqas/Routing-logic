package com.routerbackend.incomingrequest;

import com.routerbackend.core.OsmNodeNamed;
import com.routerbackend.core.OsmTrack;
import com.routerbackend.core.RoutingContext;

import java.util.List;

public interface IRequestHandler {
  RoutingContext readRoutingContext(String profile, String alternativeIdx);

  List<OsmNodeNamed> readWayPointList(String lonLats);

  String formatTrack(OsmTrack track);
}
