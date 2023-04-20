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

public final class ProfileActions {

  public static void parseProfile(RoutingContext routingContext) {
    String profileBaseDir = "src/main/java/com/data/profiles";
    File profileDir = new File(profileBaseDir);
    File profileFile = new File(profileDir, routingContext.getProfileName() + ".brf");
    routingContext.profileTimestamp = profileFile.lastModified() + routingContext.getKeyValueChecksum() << 24;

    BExpressionMetaData meta = new BExpressionMetaData();
    routingContext.expressionContextWay = new BExpressionContextWay(routingContext.memoryClass * 512, meta);
    routingContext.expressionContextNode = new BExpressionContextNode(0, meta);
    routingContext.expressionContextNode.setForeignContext(routingContext.expressionContextWay);

    meta.readMetaData(new File(profileDir, "lookups.dat"));

    routingContext.expressionContextWay.parseFile(profileFile, "global");
    routingContext.expressionContextNode.parseFile(profileFile, "global");

    routingContext.readGlobalConfig();

    if (routingContext.processUnusedTags) {
      routingContext.expressionContextWay.setAllTagsUsed();
    }
  }

}
