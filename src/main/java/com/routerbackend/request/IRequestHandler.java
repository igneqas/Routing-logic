package com.routerbackend.request;

import com.routerbackend.core.OsmNodeNamed;
import com.routerbackend.core.OsmTrack;
import com.routerbackend.core.RoutingContext;

import java.util.List;
import java.util.Map;

public abstract class IRequestHandler {
  protected Map<String, String> params;

  public abstract RoutingContext readRoutingContext(String profile, String noGos, String alternativeIdx);

  public abstract List<OsmNodeNamed> readWayPointList(String lonLats);

  public abstract String formatTrack(OsmTrack track);

  public abstract String getMimeType();

  public abstract String getFileName();
}
