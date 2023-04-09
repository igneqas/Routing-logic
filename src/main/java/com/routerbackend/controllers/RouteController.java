package com.routerbackend.controllers;

import com.routerbackend.core.OsmNodeNamed;
import com.routerbackend.core.OsmTrack;
import com.routerbackend.core.RoutingContext;
import com.routerbackend.core.RoutingEngine;
import com.routerbackend.request.IRequestHandler;
import com.routerbackend.request.RequestHandler;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class RouteController {
    private RoutingEngine routingEngine = null;


    @GetMapping(value = "/route")
    @CrossOrigin(origins = "http://localhost:3000")
    public ResponseEntity<String> getRoute (@RequestParam(value="profile", required = false) String profile, @RequestParam(value="lonlats", required = false) String lonlats, @RequestParam(value="format", required = false) String format, @RequestParam(value = "alternativeidx", required = false) String alternativeIdx) {
        if(profile == null || profile.isEmpty())
            return new ResponseEntity<>("Provide a profile.", HttpStatus.BAD_REQUEST);
        if(lonlats == null || lonlats.isEmpty())
            return new ResponseEntity<>("Provide a lonlats.", HttpStatus.BAD_REQUEST);
        if(format == null || format.isEmpty())
            return new ResponseEntity<>("Provide format.", HttpStatus.BAD_REQUEST);

        IRequestHandler requestHandler = new RequestHandler();
        RoutingContext routingContext = requestHandler.readRoutingContext(profile, alternativeIdx);
        List<OsmNodeNamed> waypointList = requestHandler.readWayPointList(lonlats);
        routingEngine = new RoutingEngine(waypointList, routingContext);
        routingEngine.quite = true;
        routingEngine.doRun();
        if (routingEngine.getErrorMessage() != null) {
            return new ResponseEntity<>(routingEngine.getErrorMessage(), HttpStatus.BAD_REQUEST);
        } else {
            OsmTrack track = routingEngine.getFoundTrack();
            if (track != null) {
                    return new ResponseEntity<>(requestHandler.formatTrack(track), HttpStatus.OK);
            }
        }
        return new ResponseEntity<>("", HttpStatus.OK);
    }
}
