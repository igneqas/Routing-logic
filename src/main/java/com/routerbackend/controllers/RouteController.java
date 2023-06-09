package com.routerbackend.controllers;

import com.routerbackend.controllers.utils.DistanceCalculator;
import com.routerbackend.dtos.RouteDTO;
import com.routerbackend.dtos.UserDTO;
import com.routerbackend.dtos.utils.Coordinates;
import com.routerbackend.repositories.RouteRepository;
import com.routerbackend.repositories.UserRepository;
import com.routerbackend.routinglogic.core.OsmNodeNamed;
import com.routerbackend.routinglogic.core.OsmTrack;
import com.routerbackend.routinglogic.core.RoutingContext;
import com.routerbackend.routinglogic.core.RoutingEngine;
import com.routerbackend.requesthandling.incomingrequest.routing.RouteRequestParser;
import com.routerbackend.security.config.JwtService;
import jakarta.servlet.http.HttpServletRequest;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

import static com.routerbackend.dtos.utils.Converter.JsonToCoordinates;
import static com.routerbackend.dtos.utils.Converter.RoutesToJson;

@RestController
@RequestMapping("/route")
public class RouteController {

    @Autowired
    RouteRepository routeRepository;

    @Autowired
    UserRepository userRepository;

    @Autowired
    JwtService jwtService;

    @GetMapping("/generate")
    @CrossOrigin(origins = "http://localhost:3000")
    public ResponseEntity<String> getRoute (@RequestParam(value="profile", required = false) String profile, @RequestParam(value="lonlats", required = false) String lonlats, @RequestParam(value = "alternativeidx", required = false) String alternativeIdx) {
        if(profile == null || profile.isEmpty())
            return new ResponseEntity<>("Provide a profile.", HttpStatus.BAD_REQUEST);
        if(lonlats == null || lonlats.isEmpty())
            return new ResponseEntity<>("Provide coordinates.", HttpStatus.BAD_REQUEST);

        RouteRequestParser routeRequestParser = new RouteRequestParser();
        RoutingContext routingContext = routeRequestParser.readRoutingContext(profile, alternativeIdx);
        List<OsmNodeNamed> waypointList = routeRequestParser.readWaypointList(lonlats);
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
    public ResponseEntity<String> saveRoute(HttpServletRequest request, @RequestBody String body){
        JSONObject jsonObject = new JSONObject(body);
        String name = jsonObject.getString("name");
        Date dateCreated = new Date();
        double length = jsonObject.getDouble("distance");
        int duration = jsonObject.getInt("time");
        String tripType = jsonObject.getString("type");
        int ascend = jsonObject.getInt("ascend");
        List<Coordinates> coordinates = JsonToCoordinates(jsonObject.getJSONArray("coordinates"));

        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authHeader == null ||!authHeader.startsWith("Bearer ")) {
            return new ResponseEntity<>("", HttpStatus.UNAUTHORIZED);
        }
        String token = authHeader.substring(7);
        String userEmail = jwtService.extractUsername(token);
        Optional<UserDTO> user = userRepository.findByEmail(userEmail);

        if(user.isPresent()) {
            RouteDTO routeDTO = new RouteDTO(name, user.get().get_id(), dateCreated, length, duration, tripType, coordinates, ascend);
            routeRepository.save(routeDTO);
            return new ResponseEntity<>("", HttpStatus.OK);
        }
        return new ResponseEntity<>("", HttpStatus.BAD_REQUEST);
    }

    @GetMapping(path = "/getSavedRoutes")
    @CrossOrigin(origins = "http://localhost:3000")
    public ResponseEntity<String> getSavedRoutes(HttpServletRequest request){
        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authHeader == null ||!authHeader.startsWith("Bearer ")) {
            return new ResponseEntity<>("", HttpStatus.UNAUTHORIZED);
        }
        String token = authHeader.substring(7);
        String userEmail = jwtService.extractUsername(token);
        Optional<UserDTO> user = userRepository.findByEmail(userEmail);

        if(user.isPresent()) {
            List<RouteDTO> routes = routeRepository.findByUserId(user.get().get_id());
            Collections.reverse(routes);
            return new ResponseEntity<>(RoutesToJson(routes).toString(), HttpStatus.OK);
        }
        return new ResponseEntity<>("", HttpStatus.FORBIDDEN);
    }

    @DeleteMapping
    @CrossOrigin(origins = "http://localhost:3000")
    public ResponseEntity<String> deleteRoute(@RequestParam("id") String id){
        routeRepository.deleteById(id);
        return new ResponseEntity<>("", HttpStatus.OK);
    }

    @GetMapping("/suggestions")
    @CrossOrigin(origins = "http://localhost:3000")
    public ResponseEntity<String> getSuggestions (@RequestParam(value="profile", required = false) String profile, @RequestParam(value="lonlats", required = false) String lonLats) {
        if(profile == null || profile.isEmpty())
            return new ResponseEntity<>("Provide a profile.", HttpStatus.BAD_REQUEST);
        if(lonLats == null || lonLats.isEmpty())
            return new ResponseEntity<>("Provide coordinates.", HttpStatus.BAD_REQUEST);

        String[] coordinates = lonLats.split("\\;");
        String[] startCoordinates = coordinates[0].split(",");
        String[] finishCoordinates = coordinates[1].split(",");
        List<RouteDTO> routes = routeRepository.findByTripType(profile);
        List<RouteDTO> filteredRoutes = routes.stream().filter(route -> DistanceCalculator.isWithin300Meters(route.getCoordinates().get(0), startCoordinates) &&
                DistanceCalculator.isWithin300Meters(route.getCoordinates().get(route.getCoordinates().size() - 1), finishCoordinates)).toList();

        if(filteredRoutes.size() <= 5)
            return new ResponseEntity<>(RoutesToJson(filteredRoutes).toString(), HttpStatus.OK);

        Collections.shuffle(filteredRoutes);
        return new ResponseEntity<>(RoutesToJson(filteredRoutes.subList(0, 5)).toString(), HttpStatus.OK);
    }
}
