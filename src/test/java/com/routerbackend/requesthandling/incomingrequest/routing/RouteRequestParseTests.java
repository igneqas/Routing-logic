package com.routerbackend.requesthandling.incomingrequest.routing;

import com.routerbackend.routinglogic.core.RoutingContext;
import jakarta.annotation.Resource;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;

import static org.mockito.MockitoAnnotations.openMocks;

public class RouteRequestParseTests {

    @InjectMocks
    @Resource
    private RouteRequestParser routeRequestParser;

    @Before
    public void setup() {
        openMocks(this);
    }

    @Test
    public void readRoutingContext_returnsRoutingContext() {
        //TODO: add mocks
        RoutingContext result = routeRequestParser.readRoutingContext("pollution-free", "0");
        Assert.assertEquals("pollution-free", result.getProfileName());
        Assert.assertEquals(0, result.getAlternativeIndex(0, 2));
    }
}
