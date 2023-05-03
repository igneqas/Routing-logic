package com.routerbackend.controllers;

import com.routerbackend.dtos.RouteDTO;
import com.routerbackend.dtos.UserDTO;
import com.routerbackend.repositories.RouteRepository;
import com.routerbackend.repositories.UserRepository;
import com.routerbackend.requesthandling.incomingrequest.routing.RouteRequestParser;
import com.routerbackend.routinglogic.core.OsmTrack;
import com.routerbackend.routinglogic.core.ProfileActions;
import com.routerbackend.routinglogic.core.RoutingContext;
import com.routerbackend.routinglogic.core.RoutingEngine;
import com.routerbackend.security.config.JwtService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.stubbing.Answer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static com.routerbackend.dtos.utils.Converter.RoutesToJson;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.openMocks;

public class RouteControllerTests {

    @Mock
    private JwtService jwtService;

    @Mock
    private RouteRepository routeRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private HttpServletRequest request;

    @InjectMocks
    @Resource
    private RouteController routeController;

    @Before
    public void setup() {
        openMocks(this);
    }

    @Test
    public void getRoute_returnsRoute() {
        RouteRequestParser routeRequestParser = mock(RouteRequestParser.class);
        RoutingContext routingContext = mock(RoutingContext.class);
        RoutingEngine routingEngine = mock(RoutingEngine.class);
//        MockedStatic<ProfileActions> profileActions = mockStatic(ProfileActions.class);
        OsmTrack track = mock(OsmTrack.class);

        when(routeRequestParser.readRoutingContext(anyString(), anyString())).thenReturn(routingContext);
//        profileActions.when(() -> ProfileActions.parseProfile(any())).thenAnswer((Answer<Void>) invocation -> null);
        when(routingEngine.getFoundTrack()).thenReturn(track);

        HttpStatus expectedStatus = HttpStatus.OK;
        Assert.assertEquals(expectedStatus, routeController.getRoute("safety", "25.3095371,54.7248457;25.254838469299273,54.710891700000005", "0").getStatusCode());
    }

    @Test
    public void saveRoute_returnsOK() throws JSONException {
        Optional<UserDTO> optionalUserDTO = Optional.of(new UserDTO("username", "email", "password"));
        RouteDTO route = new RouteDTO("name", "userId", new Date(), 1.1, 10, "quickest", new ArrayList<>(), 1);
        List<RouteDTO> routeList = new ArrayList<>();
        routeList.add(route);

        JSONObject jsonObject = new JSONObject();
        jsonObject.put("name", "name");
        jsonObject.put("distance", 1.2);
        jsonObject.put("time", 10);
        jsonObject.put("type", "quickest");
        jsonObject.put("ascend", 10);
        JSONArray coordsObject = new JSONArray();
        coordsObject.put(BigDecimal.valueOf(10.15d));
        coordsObject.put(BigDecimal.valueOf(10.15d));
        JSONArray coordinatesArray = new JSONArray();
        coordinatesArray.put(0, coordsObject);
        jsonObject.put("coordinates", coordinatesArray);

        when(request.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn("Bearer xyz");
        when(jwtService.extractUsername(anyString())).thenReturn("username");
        when(userRepository.findByEmail("username")).thenReturn(optionalUserDTO);
        when(routeRepository.findByUserId(any())).thenReturn(routeList);

        HttpStatus expectedStatus = HttpStatus.OK;
        Assert.assertEquals(expectedStatus, routeController.saveRoute(request, jsonObject.toString()).getStatusCode());
    }

    @Test
    public void getSavedRoutes_returnsRoutes() {
        Optional<UserDTO> optionalUserDTO = Optional.of(new UserDTO("username", "email", "password"));
        RouteDTO route = new RouteDTO("name", "userId", new Date(), 1.1, 10, "quickest", new ArrayList<>(), 1);
        List<RouteDTO> routeList = new ArrayList<>();
        routeList.add(route);
        String routeString = RoutesToJson(routeList).toString();

        when(request.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn("Bearer xyz");
        when(jwtService.extractUsername(anyString())).thenReturn("username");
        when(userRepository.findByEmail("username")).thenReturn(optionalUserDTO);
        when(routeRepository.findByUserId(any())).thenReturn(routeList);

        ResponseEntity expectedResult = new ResponseEntity<>(routeString, HttpStatus.OK);
        Assert.assertEquals(expectedResult, routeController.getSavedRoutes(request));
    }

    @Test
    public void deleteRoute_returnsOK() {
        doNothing().when(routeRepository).deleteById(anyString());
        ResponseEntity expectedResult = new ResponseEntity<>("", HttpStatus.OK);
        Assert.assertEquals(expectedResult, routeController.deleteRoute("id"));
    }
}
