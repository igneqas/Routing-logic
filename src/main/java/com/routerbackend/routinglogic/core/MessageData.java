/**
 * Information on matched way point
 *
 * @author ab
 */
package com.routerbackend.routinglogic.core;


final class MessageData implements Cloneable {
  String wayKeyValues;
  float time;
  float energy;

  MessageData copy() {
    try {
      return (MessageData) clone();
    } catch (CloneNotSupportedException e) {
      throw new RuntimeException(e);
    }
  }
}
