package com.routerbackend.repositories;

import com.routerbackend.dtos.TokenDTO;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;
import java.util.Optional;

public interface TokenRepository extends MongoRepository<TokenDTO, String> {
    @Query("{userId: '?0'}")
    List<TokenDTO> findAllValidTokenByUser(String userId);

    @Query("{token: '?0'}")
    Optional<TokenDTO> findByToken(String token);
}
