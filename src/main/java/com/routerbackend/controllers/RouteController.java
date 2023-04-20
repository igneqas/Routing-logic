package com.routerbackend.controllers;

import com.routerbackend.routinglogic.core.OsmNodeNamed;
import com.routerbackend.routinglogic.core.OsmTrack;
import com.routerbackend.routinglogic.core.RoutingContext;
import com.routerbackend.routinglogic.core.RoutingEngine;
import com.routerbackend.requesthandling.incomingrequest.routing.IRequestHandler;
import com.routerbackend.requesthandling.incomingrequest.routing.RequestHandler;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class RouteController {
    private RoutingEngine routingEngine = null;

    @GetMapping(value = "/route")
    @CrossOrigin(origins = "http://localhost:3000")
    public ResponseEntity<String> getRoute (@RequestParam(value="profile", required = false) String profile, @RequestParam(value="lonlats", required = false) String lonlats, @RequestParam(value = "alternativeidx", required = false) String alternativeIdx) {
        if(profile == null || profile.isEmpty())
            return new ResponseEntity<>("Provide a profile.", HttpStatus.BAD_REQUEST);
        if(lonlats == null || lonlats.isEmpty())
            return new ResponseEntity<>("Provide coordinates.", HttpStatus.BAD_REQUEST);

        IRequestHandler requestHandler = new RequestHandler();
        RoutingContext routingContext = requestHandler.readRoutingContext(profile, alternativeIdx);
        List<OsmNodeNamed> waypointList = requestHandler.readWaypointList(lonlats);
        routingEngine = new RoutingEngine(waypointList, routingContext);
        routingEngine.doRun();
        if (routingEngine.getErrorMessage() != null) {
            return new ResponseEntity<>(routingEngine.getErrorMessage(), HttpStatus.BAD_REQUEST);
        } else {
            OsmTrack track = routingEngine.getFoundTrack();
            if (track != null) {
                    return new ResponseEntity<>(track.formatAsGeoJson(), HttpStatus.OK);
            }
        }
        return new ResponseEntity<>("", HttpStatus.OK);
    }
}
