package com.routerbackend.controllers;

import com.routerbackend.dtos.RouteDTO;
import com.routerbackend.dtos.UserDTO;
import com.routerbackend.dtos.utils.Coordinates;
import com.routerbackend.repositories.RouteRepository;
import com.routerbackend.repositories.UserRepository;
import com.routerbackend.routinglogic.core.OsmNodeNamed;
import com.routerbackend.routinglogic.core.OsmTrack;
import com.routerbackend.routinglogic.core.RoutingContext;
import com.routerbackend.routinglogic.core.RoutingEngine;
import com.routerbackend.requesthandling.incomingrequest.routing.IRequestHandler;
import com.routerbackend.requesthandling.incomingrequest.routing.RequestHandler;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.List;

import static com.routerbackend.dtos.utils.Converter.JsonToCoordinates;

@RestController
@RequestMapping("/route")
public class RouteController {

    @Autowired
    RouteRepository routeRepository;

    @Autowired
    UserRepository userRepository;

    @GetMapping
    @CrossOrigin(origins = "http://localhost:3000")
    public ResponseEntity<String> getRoute (@RequestParam(value="profile", required = false) String profile, @RequestParam(value="lonlats", required = false) String lonlats, @RequestParam(value = "alternativeidx", required = false) String alternativeIdx) {
        if(profile == null || profile.isEmpty())
            return new ResponseEntity<>("Provide a profile.", HttpStatus.BAD_REQUEST);
        if(lonlats == null || lonlats.isEmpty())
            return new ResponseEntity<>("Provide coordinates.", HttpStatus.BAD_REQUEST);

        IRequestHandler requestHandler = new RequestHandler();
        RoutingContext routingContext = requestHandler.readRoutingContext(profile, alternativeIdx);
        List<OsmNodeNamed> waypointList = requestHandler.readWaypointList(lonlats);
        RoutingEngine routingEngine = new RoutingEngine(waypointList, routingContext);
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

    @PostMapping
    @CrossOrigin(origins = "http://localhost:3000")
    public ResponseEntity<String> saveRoute(@RequestBody String body){
        JSONObject jsonObject = new JSONObject(body);
        String name = jsonObject.getString("name");
        String userId = jsonObject.getString("userId");
        UserDTO user = userRepository.findByEmail("ignas@gmail.com");
        System.out.println(user);
        Date dateCreated = new Date();
        double length = jsonObject.getDouble("length");
        int duration = jsonObject.getInt("duration");
        String tripType = jsonObject.getString("tripType");
        List<Coordinates> coordinates = JsonToCoordinates(jsonObject.getJSONArray("coordinates"));
        RouteDTO routeDTO = new RouteDTO(name, userId, dateCreated, length, duration, tripType, coordinates);
        routeRepository.save(routeDTO);
        return new ResponseEntity<>("", HttpStatus.OK);
    }
}
