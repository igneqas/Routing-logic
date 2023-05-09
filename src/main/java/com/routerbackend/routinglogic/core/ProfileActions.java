/**
 * Container for routig configs
 *
 * @author ab
 */
package com.routerbackend.routinglogic.core;

import com.routerbackend.routinglogic.expressions.BExpressionContextNode;
import com.routerbackend.routinglogic.expressions.BExpressionContextWay;
import com.routerbackend.routinglogic.expressions.BExpressionMetaData;

import java.io.File;
import java.util.Objects;

import static com.routerbackend.Constants.MEMORY_CLASS;

public class ProfileActions {

  private ProfileActions(){}

  public static void parseProfile(RoutingContext routingContext) {
    String profileBaseDir = "src/main/java/com/data/profiles";
    File profileDir = new File(profileBaseDir);
    String profileName = routingContext.getProfileName();
    File profileFile = new File(profileDir, (Objects.equals(profileName, "pollution-free") ? "safety" : profileName) + ".brf");

    BExpressionMetaData meta = new BExpressionMetaData();
    routingContext.expressionContextWay = new BExpressionContextWay(MEMORY_CLASS * 512, meta);
    routingContext.expressionContextNode = new BExpressionContextNode(0, meta);
    routingContext.expressionContextNode.setForeignContext(routingContext.expressionContextWay);

    meta.readMetaData(new File(profileDir, "lookups.dat"));

    routingContext.expressionContextWay.parseFile(profileFile, "global");
    routingContext.expressionContextNode.parseFile(profileFile, "global");

    routingContext.readGlobalConfig();
  }
}
