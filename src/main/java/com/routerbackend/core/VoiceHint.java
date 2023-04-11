/**
 * Container for a voice hint
 * (both input- and result data for voice hint processing)
 *
 * @author ab
 */
package com.routerbackend.core;

import java.util.ArrayList;
import java.util.List;

public class VoiceHint {
  static final int C = 1; // continue (go straight)
  static final int TL = 2; // turn left
  static final int TSLL = 3; // turn slightly left
  static final int TSHL = 4; // turn sharply left
  static final int TR = 5; // turn right
  static final int TSLR = 6; // turn slightly right
  static final int TSHR = 7; // turn sharply right
  static final int KL = 8; // keep left
  static final int KR = 9; // keep right
  static final int TLU = 10; // U-turn
  static final int TU = 11; // 180 degree u-turn
  static final int TRU = 12; // Right U-turn
  static final int OFFR = 13; // Off route
  static final int RNDB = 14; // Roundabout
  static final int RNLB = 15; // Roundabout left
  static final int BL = 16; // Beeline routing

  int ilon;
  int ilat;
  short selev;
  int cmd;
  MessageData oldWay;
  MessageData goodWay;
  List<MessageData> badWays;
  double distanceToNext;
  int indexInTrack;

  public float getTime() {
    return oldWay == null ? 0.f : oldWay.time;
  }

  float angle = Float.MAX_VALUE;
  boolean turnAngleConsumed;
  boolean needsRealTurn;

  int roundaboutExit;

  boolean isRoundabout() {
    return roundaboutExit != 0;
  }

  public void addBadWay(MessageData badWay) {
    if (badWay == null) {
      return;
    }
    if (badWays == null) {
      badWays = new ArrayList<>();
    }
    badWays.add(badWay);
  }

  public int getCommand() {
    return cmd;
  }

  public int getExitNumber() {
    return roundaboutExit;
  }

  public void calcCommand() {
    float lowerBadWayAngle = -181;
    float higherBadWayAngle = 181;
    if (badWays != null) {
      for (MessageData badWay : badWays) {
        if (badWay.isBadOneway()) {
          continue;
        }
        if (lowerBadWayAngle < badWay.turnangle && badWay.turnangle < goodWay.turnangle) {
          lowerBadWayAngle = badWay.turnangle;
        }
        if (higherBadWayAngle > badWay.turnangle && badWay.turnangle > goodWay.turnangle) {
          higherBadWayAngle = badWay.turnangle;
        }
      }
    }

    float cmdAngle = angle;

    // fall back to local angle if otherwise inconsistent
    //if ( lowerBadWayAngle > angle || higherBadWayAngle < angle )
    //{
    //cmdAngle = goodWay.turnangle;
    //}
    if (angle == Float.MAX_VALUE) {
      cmdAngle = goodWay.turnangle;
    }
    if (cmd == BL) return;

    if (roundaboutExit > 0) {
      cmd = RNDB;
    } else if (roundaboutExit < 0) {
      cmd = RNLB;
    } else if (is180DegAngle(cmdAngle) && cmdAngle <= -179.f && higherBadWayAngle == 181.f && lowerBadWayAngle == -181.f) {
      cmd = TU;
    } else if (cmdAngle < -159.f) {
      cmd = TLU;
    } else if (cmdAngle < -135.f) {
      cmd = TSHL;
    } else if (cmdAngle < -45.f) {
      // a TL can be pushed in either direction by a close-by alternative
      if (cmdAngle < -95.f && higherBadWayAngle < -30.f && lowerBadWayAngle < -180.f) {
        cmd = TSHL;
      } else if (cmdAngle > -85.f && lowerBadWayAngle > -180.f && higherBadWayAngle > -10.f) {
        cmd = TSLL;
      } else {
        if (cmdAngle < -110.f) {
          cmd = TSHL;
        } else if (cmdAngle > -60.f) {
          cmd = TSLL;
        } else {
          cmd = TL;
        }
      }
    } else if (cmdAngle < -21.f) {
      if (cmd != KR) { // don't overwrite KR with TSLL
        cmd = TSLL;
      }
    } else if (cmdAngle < -5.f) {
      if (lowerBadWayAngle < -100.f && higherBadWayAngle < 45.f) {
        cmd = TSLL;
      } else if (lowerBadWayAngle >= -100.f && higherBadWayAngle < 45.f) {
        cmd = KL;
      } else {
        cmd = C;
      }
    } else if (cmdAngle < 5.f) {
      if (lowerBadWayAngle > -30.f) {
        cmd = KR;
      } else if (higherBadWayAngle < 30.f) {
        cmd = KL;
      } else {
        cmd = C;
      }
    } else if (cmdAngle < 21.f) {
      // a TR can be pushed in either direction by a close-by alternative
      if (lowerBadWayAngle > -45.f && higherBadWayAngle > 100.f) {
        cmd = TSLR;
      } else if (lowerBadWayAngle > -45.f && higherBadWayAngle <= 100.f) {
        cmd = KR;
      } else {
        cmd = C;
      }
    } else if (cmdAngle < 45.f) {
      cmd = TSLR;
    } else if (cmdAngle < 135.f) {
      if (cmdAngle < 85.f && higherBadWayAngle < 180.f && lowerBadWayAngle < 10.f) {
        cmd = TSLR;
      } else if (cmdAngle > 95.f && lowerBadWayAngle > 30.f && higherBadWayAngle > 180.f) {
        cmd = TSHR;
      } else {
        if (cmdAngle > 110.) {
          cmd = TSHR;
        } else if (cmdAngle < 60.) {
          cmd = TSLR;
        } else {
          cmd = TR;
        }
      }
    } else if (cmdAngle < 159.f) {
      cmd = TSHR;
    } else if (is180DegAngle(cmdAngle) && cmdAngle >= 179.f && higherBadWayAngle == 181.f && lowerBadWayAngle == -181.f) {
      cmd = TU;
    } else {
      cmd = TRU;
    }
  }

  static boolean is180DegAngle(float angle) {
    return (Math.abs(angle) <= 180.f && Math.abs(angle) >= 179.f);
  }

  public String formatGeometry() {
    float oldPrio = oldWay == null ? 0.f : oldWay.priorityclassifier;
    StringBuilder sb = new StringBuilder(30);
    sb.append(' ').append((int) oldPrio);
    appendTurnGeometry(sb, goodWay);
    if (badWays != null) {
      for (MessageData badWay : badWays) {
        sb.append(" ");
        appendTurnGeometry(sb, badWay);
      }
    }
    return sb.toString();
  }

  private void appendTurnGeometry(StringBuilder sb, MessageData msg) {
    sb.append("(").append((int) (msg.turnangle + 0.5)).append(")").append((int) (msg.priorityclassifier));
  }

}
