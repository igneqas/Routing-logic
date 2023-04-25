package com.routerbackend.repositories;

import com.routerbackend.dtos.UserDTO;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.Optional;

public interface UserRepository extends MongoRepository<UserDTO, String> {
    @Query("{email: '?0'}")
    Optional<UserDTO> findByEmail(String email);
}
