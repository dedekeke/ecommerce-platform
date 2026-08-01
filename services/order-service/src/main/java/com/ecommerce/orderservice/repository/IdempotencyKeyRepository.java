package com.ecommerce.orderservice.repository;

import com.ecommerce.orderservice.domain.entity.IdempotencyKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, String> {

    Optional<IdempotencyKey> findByUserIdAndIdempotencyKey(String userId, String idempotencyKey);

    void deleteByUserIdAndIdempotencyKey(String userId, String idempotencyKey);
}
