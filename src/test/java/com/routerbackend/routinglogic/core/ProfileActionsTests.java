package com.routerbackend.routinglogic.core;

import org.junit.Test;

import static com.routerbackend.routinglogic.core.ProfileActions.parseProfile;

public class ProfileActionsTests {

    @Test
    public void parseProfile_successfullyParses() {
        RoutingContext routingContext = new RoutingContext();
        routingContext.setProfileName("safety");

        parseProfile(routingContext);
    }
}
