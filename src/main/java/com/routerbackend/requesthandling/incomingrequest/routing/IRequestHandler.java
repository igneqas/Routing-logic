package com.routerbackend.requesthandling.incomingrequest.routing;

import com.routerbackend.routinglogic.core.OsmNodeNamed;
import com.routerbackend.routinglogic.core.RoutingContext;

import java.util.List;

public interface IRequestHandler {
  RoutingContext readRoutingContext(String profile, String alternativeIdx);

  List<OsmNodeNamed> readWaypointList(String lonLats);
}
