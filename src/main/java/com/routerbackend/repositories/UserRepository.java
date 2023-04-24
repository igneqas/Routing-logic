package com.routerbackend.repositories;

import com.routerbackend.dtos.UserDTO;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

public interface UserRepository extends MongoRepository<UserDTO, String> {
    @Query("{email: '?0'}")
    UserDTO findByEmail(String email);
}
