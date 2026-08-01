package com.ecommerce.recommendationservice.repository;

import com.ecommerce.recommendationservice.domain.ConsumedOrderDocument;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ConsumedOrderRepository extends MongoRepository<ConsumedOrderDocument, String> {
}
