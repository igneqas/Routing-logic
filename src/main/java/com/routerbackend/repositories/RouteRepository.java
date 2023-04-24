package com.routerbackend.repositories;

import com.routerbackend.dtos.RouteDTO;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

public interface RouteRepository extends MongoRepository<RouteDTO, String> {
}
