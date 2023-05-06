package com.routerbackend.routinglogic.core;

import com.routerbackend.controllers.RouteController;
import com.routerbackend.requesthandling.incomingrequest.routing.RouteRequestParser;
import jakarta.annotation.Resource;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.springframework.http.HttpStatus;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static com.routerbackend.Constants.MAX_RUNNING_TIME;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class RoutingEngineTest {

    @InjectMocks
    @Resource
    private RoutingEngine routingEngine;

    @Test
    public void doRun_finishedInTime() {
        // Arrange
        RouteRequestParser routeRequestParser = new RouteRequestParser();
        RoutingContext routingContext = routeRequestParser.readRoutingContext("safety", "2");
        List<OsmNodeNamed> waypointList = routeRequestParser.readWaypointList("25.3095371,54.7248457;23.991767060177082,54.91879816496272");
        RoutingEngine routingEngine = new RoutingEngine(waypointList, routingContext);

        // Act
        long startTime = new Date().getTime();
        routingEngine.doRun();
        long finishTime = new Date().getTime();

        // Assert
        System.out.println(finishTime - startTime);
        Assert.assertTrue(finishTime - startTime <= MAX_RUNNING_TIME);
    }
}
