/**
 * Container for a voice hint
 * (both input- and result data for voice hint processing)
 *
 * @author ab
 */
package com.routerbackend.core;

import java.util.ArrayList;
import java.util.List;

public class VoiceHintList {
  private String transportMode;
  int turnInstructionMode;
  List<VoiceHint> list = new ArrayList<VoiceHint>();

  public void setTransportMode(boolean isCar, boolean isBike) {
    transportMode = isCar ? "car" : (isBike ? "bike" : "foot");
  }

  public String getTransportMode() {
    return transportMode;
  }

  public int getLocusRouteType() {
    if ("car".equals(transportMode)) {
      return 0;
    }
    if ("bike".equals(transportMode)) {
      return 5;
    }
    return 3; // foot
  }
}
