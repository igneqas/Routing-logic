package com.routerbackend.repositories;

import com.routerbackend.dtos.RouteDTO;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;

public interface RouteRepository extends MongoRepository<RouteDTO, String> {
    @Query("{userId: '?0'}")
    List<RouteDTO> findByUserId(String userId);

    @Query("{tripType: '?0'}")
    List<RouteDTO> findByTripType(String tripType);
}
